package pl.ingotlabs.base.storage;

import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.TxRecord;
import pl.ingotlabs.base.economy.TxType;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite / MySQL storage. All writes and history reads run on one dedicated thread,
 * so the single connection is never used concurrently.
 */
public final class SqlStorage {
    public record AccountRow(UUID uuid, String name, long balance) {
    }

    private static final int SCHEMA_VERSION = 1;

    private final IngotLabsBase plugin;
    private final Settings settings;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "IngotLabs-Storage"));
    private Connection connection;
    private boolean mysql;

    public SqlStorage(IngotLabsBase plugin, Settings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void init() throws SQLException {
        mysql = settings.storageType().equals("mysql");
        plugin.getDataFolder().mkdirs();
        connect();
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta (k VARCHAR(32) NOT NULL PRIMARY KEY, v VARCHAR(64) NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS accounts ("
                    + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "name VARCHAR(32) NOT NULL, "
                    + "balance BIGINT NOT NULL, "
                    + "updated BIGINT NOT NULL)");
            if (mysql) {
                st.execute("CREATE TABLE IF NOT EXISTS transactions ("
                        + "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                        + "ts BIGINT NOT NULL, account VARCHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                        + "delta BIGINT NOT NULL, balance_after BIGINT NOT NULL, "
                        + "other VARCHAR(64) NULL, note VARCHAR(128) NULL, "
                        + "INDEX idx_tx_account (account, id))");
            } else {
                st.execute("CREATE TABLE IF NOT EXISTS transactions ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "ts BIGINT NOT NULL, account VARCHAR(36) NOT NULL, type VARCHAR(16) NOT NULL, "
                        + "delta BIGINT NOT NULL, balance_after BIGINT NOT NULL, "
                        + "other VARCHAR(64) NULL, note VARCHAR(128) NULL)");
                st.execute("CREATE INDEX IF NOT EXISTS idx_tx_account ON transactions(account, id)");
            }
        }
        // schema versioning (room for future migrations)
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT v FROM meta WHERE k = 'schema_version'")) {
            if (!rs.next()) {
                try (PreparedStatement ps = connection.prepareStatement("INSERT INTO meta (k, v) VALUES ('schema_version', ?)")) {
                    ps.setString(1, String.valueOf(SCHEMA_VERSION));
                    ps.executeUpdate();
                }
            }
        }
    }

    private void connect() throws SQLException {
        if (mysql) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("MySQL driver class not found, relying on DriverManager.");
            }
            String url = "jdbc:mysql://" + settings.mysqlHost() + ":" + settings.mysqlPort() + "/"
                    + settings.mysqlDatabase() + "?useSSL=" + settings.mysqlSsl()
                    + "&allowPublicKeyRetrieval=true&characterEncoding=utf8&autoReconnect=true";
            connection = DriverManager.getConnection(url, settings.mysqlUser(), settings.mysqlPassword());
        } else {
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().warning("SQLite driver class not found, relying on DriverManager.");
            }
            File file = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA busy_timeout=5000");
            }
        }
    }

    private Connection conn() throws SQLException {
        if (connection == null || connection.isClosed() || (mysql && !connection.isValid(2))) {
            connect();
        }
        return connection;
    }

    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            plugin.getLogger().warning("Storage is closed, a write was dropped.");
        }
    }

    // ---------- reads ----------

    /** Called once on startup, before any async task is queued. */
    public List<AccountRow> loadAccounts() throws SQLException {
        List<AccountRow> rows = new ArrayList<>();
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery("SELECT uuid, name, balance FROM accounts")) {
            while (rs.next()) {
                try {
                    rows.add(new AccountRow(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Skipping account with invalid UUID: " + rs.getString(1));
                }
            }
        }
        return rows;
    }

    public CompletableFuture<List<TxRecord>> history(UUID uuid, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            List<TxRecord> list = new ArrayList<>();
            String sql = "SELECT ts, type, delta, balance_after, other, note FROM transactions "
                    + "WHERE account = ? ORDER BY id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, limit);
                ps.setInt(3, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TxType type;
                        try {
                            type = TxType.valueOf(rs.getString(2));
                        } catch (IllegalArgumentException e) {
                            type = TxType.DEPOSIT;
                        }
                        list.add(new TxRecord(rs.getLong(1), uuid, type, rs.getLong(3), rs.getLong(4),
                                rs.getString(5), rs.getString(6)));
                    }
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return list;
        }, executor);
    }

    // ---------- writes (async) ----------

    public void saveAccount(UUID uuid, String name, long balance) {
        submit(() -> {
            String sql = mysql
                    ? "INSERT INTO accounts (uuid, name, balance, updated) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), balance = VALUES(balance), updated = VALUES(updated)"
                    : "INSERT INTO accounts (uuid, name, balance, updated) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET name = excluded.name, balance = excluded.balance, updated = excluded.updated";
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, balance);
                ps.setLong(4, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save account " + uuid, e);
            }
        });
    }

    public void logTransaction(TxRecord r) {
        submit(() -> {
            String sql = "INSERT INTO transactions (ts, account, type, delta, balance_after, other, note) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn().prepareStatement(sql)) {
                ps.setLong(1, r.timestamp());
                ps.setString(2, r.account().toString());
                ps.setString(3, r.type().name());
                ps.setLong(4, r.delta());
                ps.setLong(5, r.balanceAfter());
                ps.setString(6, cut(r.other(), 64));
                ps.setString(7, cut(r.note(), 128));
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not log transaction", e);
            }
        });
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Storage did not finish writing within 30 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // nothing to do
        }
    }
}

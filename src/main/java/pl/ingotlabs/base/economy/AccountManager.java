package pl.ingotlabs.base.economy;

import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.storage.SqlStorage;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds all accounts in memory (source of truth); every change is written to the database
 * asynchronously. All balance mutations are serialized by one lock, so transfers are atomic.
 */
public final class AccountManager {
    private final SqlStorage storage;
    private final Settings settings;
    private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> byName = new ConcurrentHashMap<>();
    private final Object lock = new Object();

    public AccountManager(SqlStorage storage, Settings settings) {
        this.storage = storage;
        this.settings = settings;
    }

    public void load() throws SQLException {
        for (SqlStorage.AccountRow row : storage.loadAccounts()) {
            Account a = new Account(row.uuid(), row.name(), row.balance());
            accounts.put(a.uuid(), a);
            byName.put(row.name().toLowerCase(Locale.ROOT), a.uuid());
        }
    }

    // ---------- lookup ----------

    public Account get(UUID uuid) {
        return accounts.get(uuid);
    }

    public Account find(String name) {
        if (name == null) {
            return null;
        }
        UUID id = byName.get(name.toLowerCase(Locale.ROOT));
        return id == null ? null : accounts.get(id);
    }

    public boolean has(UUID uuid) {
        return accounts.containsKey(uuid);
    }

    public long balance(UUID uuid) {
        Account a = accounts.get(uuid);
        return a == null ? 0 : a.balance();
    }

    public Collection<Account> all() {
        return accounts.values();
    }

    // ---------- creation ----------

    public Account getOrCreate(UUID uuid, String name) {
        Account existing = accounts.get(uuid);
        if (existing != null) {
            if (name != null && !name.equals(existing.name())) {
                rename(existing, name);
            }
            return existing;
        }
        synchronized (lock) {
            existing = accounts.get(uuid);
            if (existing != null) {
                return existing;
            }
            String n = name == null ? uuid.toString().substring(0, 8) : name;
            Account a = new Account(uuid, n, settings.startingUnits());
            accounts.put(uuid, a);
            byName.put(n.toLowerCase(Locale.ROOT), uuid);
            storage.saveAccount(uuid, n, a.balance());
            if (a.balance() != 0) {
                log(a, TxType.START, a.balance(), null, "starting balance");
            }
            return a;
        }
    }

    private void rename(Account a, String newName) {
        synchronized (lock) {
            byName.remove(a.name().toLowerCase(Locale.ROOT), a.uuid());
            a.setName(newName);
            byName.put(newName.toLowerCase(Locale.ROOT), a.uuid());
            storage.saveAccount(a.uuid(), newName, a.balance());
        }
    }

    // ---------- operations ----------

    public TxResult deposit(UUID id, long units, TxType type, String other, String note) {
        if (units < 0) {
            return new TxResult(TxResult.Status.INVALID_AMOUNT, balance(id));
        }
        synchronized (lock) {
            Account a = accounts.get(id);
            if (a == null) {
                return new TxResult(TxResult.Status.NO_ACCOUNT, 0);
            }
            long nb;
            try {
                nb = Math.addExact(a.balance(), units);
            } catch (ArithmeticException e) {
                return new TxResult(TxResult.Status.OVER_LIMIT, a.balance());
            }
            if (nb > settings.maxUnits()) {
                return new TxResult(TxResult.Status.OVER_LIMIT, a.balance());
            }
            apply(a, nb, type, other, note);
            return new TxResult(TxResult.Status.SUCCESS, nb);
        }
    }

    public TxResult withdraw(UUID id, long units, TxType type, String other, String note) {
        if (units < 0) {
            return new TxResult(TxResult.Status.INVALID_AMOUNT, balance(id));
        }
        synchronized (lock) {
            Account a = accounts.get(id);
            if (a == null) {
                return new TxResult(TxResult.Status.NO_ACCOUNT, 0);
            }
            long nb;
            try {
                nb = Math.subtractExact(a.balance(), units);
            } catch (ArithmeticException e) {
                return new TxResult(TxResult.Status.INSUFFICIENT_FUNDS, a.balance());
            }
            if (nb < settings.minUnits()) {
                return new TxResult(TxResult.Status.INSUFFICIENT_FUNDS, a.balance());
            }
            apply(a, nb, type, other, note);
            return new TxResult(TxResult.Status.SUCCESS, nb);
        }
    }

    public TxResult set(UUID id, long units, TxType type, String other, String note) {
        synchronized (lock) {
            Account a = accounts.get(id);
            if (a == null) {
                return new TxResult(TxResult.Status.NO_ACCOUNT, 0);
            }
            if (units > settings.maxUnits()) {
                return new TxResult(TxResult.Status.OVER_LIMIT, a.balance());
            }
            if (units < settings.minUnits()) {
                return new TxResult(TxResult.Status.INVALID_AMOUNT, a.balance());
            }
            apply(a, units, type, other, note);
            return new TxResult(TxResult.Status.SUCCESS, units);
        }
    }

    /**
     * Atomic transfer. The sender pays {@code amount}, the receiver gets {@code amount - tax}.
     * The result's balance is the sender's new balance.
     */
    public TxResult transfer(UUID from, UUID to, long amount, long tax, String note) {
        if (amount <= 0 || tax < 0 || tax > amount || from.equals(to)) {
            return new TxResult(TxResult.Status.INVALID_AMOUNT, balance(from));
        }
        synchronized (lock) {
            Account a = accounts.get(from);
            Account b = accounts.get(to);
            if (a == null || b == null) {
                return new TxResult(TxResult.Status.NO_ACCOUNT, 0);
            }
            long received = amount - tax;
            long newFrom;
            long newTo;
            try {
                newFrom = Math.subtractExact(a.balance(), amount);
                newTo = Math.addExact(b.balance(), received);
            } catch (ArithmeticException e) {
                return new TxResult(TxResult.Status.OVER_LIMIT, a.balance());
            }
            if (newFrom < settings.minUnits()) {
                return new TxResult(TxResult.Status.INSUFFICIENT_FUNDS, a.balance());
            }
            if (newTo > settings.maxUnits()) {
                return new TxResult(TxResult.Status.OVER_LIMIT, a.balance());
            }
            apply(a, newFrom, TxType.TRANSFER_OUT, b.name(), note);
            apply(b, newTo, TxType.TRANSFER_IN, a.name(), note);
            return new TxResult(TxResult.Status.SUCCESS, newFrom);
        }
    }

    // ---------- internals ----------

    private void apply(Account a, long newBalance, TxType type, String other, String note) {
        long delta = newBalance - a.balance();
        a.setBalance(newBalance);
        storage.saveAccount(a.uuid(), a.name(), newBalance);
        log(a, type, delta, other, note);
    }

    private void log(Account a, TxType type, long delta, String other, String note) {
        if (!settings.logTransactions()) {
            return;
        }
        storage.logTransaction(new TxRecord(System.currentTimeMillis(), a.uuid(), type, delta,
                a.balance(), other, note));
    }
}

package pl.ingotlabs.base.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import pl.ingotlabs.base.economy.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Typed view of config.yml. Reloaded with /ieco reload. */
public final class Settings {
    private String language = "en";

    private String storageType = "sqlite";
    private String mysqlHost = "localhost";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "ingotlabs";
    private String mysqlUser = "root";
    private String mysqlPassword = "";
    private boolean mysqlSsl = false;

    private String nameSingular = "Ingot";
    private String namePlural = "Ingots";
    private String symbol = "$";
    private String format = "{symbol}{amount}";
    private int decimals = 2;
    private boolean grouping = true;
    private char thousandsSeparator = ',';
    private char decimalSeparator = '.';
    private long stepUnits = 100;

    private long startingUnits;
    private long minUnits;
    private long maxUnits;

    private boolean payEnabled = true;
    private long payMin;
    private long payMax;
    private BigDecimal taxPercent = BigDecimal.ZERO;
    private int payCooldown = 3;
    private long payConfirmAbove;

    private int topCacheSeconds = 30;
    private int topPerPage = 10;
    private Set<String> topHidden = new HashSet<>();

    private boolean logTransactions = true;
    private List<Long> adminSteps = new ArrayList<>();

    private List<Long> payGuiSteps = new ArrayList<>();
    private int chatInputSeconds = 30;

    private boolean exchangeEnabled = true;
    private String exchangePlugin = "WebMarket";
    private String exchangeCommand = "webmarket";

    private boolean takeoverEnabled = true;
    private Map<String, String> takeover = new LinkedHashMap<>();

    public void load(FileConfiguration c) {
        language = c.getString("language", "en");

        storageType = c.getString("storage.type", "sqlite").toLowerCase(Locale.ROOT);
        mysqlHost = c.getString("storage.mysql.host", "localhost");
        mysqlPort = c.getInt("storage.mysql.port", 3306);
        mysqlDatabase = c.getString("storage.mysql.database", "ingotlabs");
        mysqlUser = c.getString("storage.mysql.username", "root");
        mysqlPassword = c.getString("storage.mysql.password", "");
        mysqlSsl = c.getBoolean("storage.mysql.use-ssl", false);

        nameSingular = c.getString("currency.name-singular", "Ingot");
        namePlural = c.getString("currency.name-plural", "Ingots");
        symbol = c.getString("currency.symbol", "$");
        format = c.getString("currency.format", "{symbol}{amount}");
        decimals = Math.max(0, Math.min(4, c.getInt("currency.decimals", 2)));
        grouping = c.getBoolean("currency.grouping", true);
        thousandsSeparator = firstChar(c.getString("currency.thousands-separator", ","), ',');
        decimalSeparator = firstChar(c.getString("currency.decimal-separator", "."), '.');
        long step = 1;
        for (int i = 0; i < Money.SCALE - decimals; i++) {
            step *= 10;
        }
        stepUnits = step;

        startingUnits = cfgUnits(c, "accounts.starting-balance", 100.0);
        minUnits = cfgUnits(c, "accounts.min-balance", 0.0);
        maxUnits = cfgUnits(c, "accounts.max-balance", 1_000_000_000_000.0);

        payEnabled = c.getBoolean("pay.enabled", true);
        payMin = cfgUnits(c, "pay.min-amount", 1.0);
        payMax = cfgUnits(c, "pay.max-amount", 0.0);
        taxPercent = BigDecimal.valueOf(c.getDouble("pay.tax-percent", 0.0));
        payCooldown = Math.max(0, c.getInt("pay.cooldown-seconds", 3));
        payConfirmAbove = cfgUnits(c, "pay.confirm-above", 10000.0);

        topCacheSeconds = Math.max(0, c.getInt("top.cache-seconds", 30));
        topPerPage = Math.max(1, c.getInt("top.per-page", 10));
        topHidden = new HashSet<>();
        for (String n : c.getStringList("top.hidden-players")) {
            topHidden.add(n.toLowerCase(Locale.ROOT));
        }

        logTransactions = c.getBoolean("transactions.log", true);

        payGuiSteps = parseSteps(c, "pay.gui-steps");
        chatInputSeconds = Math.max(5, Math.min(300, c.getInt("gui.chat-input-seconds", 30)));

        exchangeEnabled = c.getBoolean("exchange.enabled", true);
        exchangePlugin = c.getString("exchange.plugin", "WebMarket");
        String cmd = c.getString("exchange.command", "webmarket").trim();
        exchangeCommand = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        takeoverEnabled = c.getBoolean("commands.override-other-plugins", true);
        takeover = new LinkedHashMap<>();
        ConfigurationSection section = c.getConfigurationSection("commands.takeover");
        if (section == null) {
            // config.yml from an older version: use the defaults
            String[][] defaults = {
                    {"balance", "balance"}, {"bal", "balance"}, {"money", "balance"},
                    {"ebalance", "balance"}, {"ebal", "balance"}, {"emoney", "balance"},
                    {"pay", "pay"}, {"epay", "pay"},
                    {"baltop", "baltop"}, {"balancetop", "baltop"}, {"ebaltop", "baltop"}, {"ebalancetop", "baltop"},
                    {"eco", "ieco"}, {"economy", "ieco"}, {"eeco", "ieco"}, {"eeconomy", "ieco"}
            };
            for (String[] d : defaults) {
                takeover.put(d[0], d[1]);
            }
        } else {
            for (String label : section.getKeys(false)) {
                String ours = section.getString(label);
                if (ours != null && !ours.isBlank()) {
                    takeover.put(label.toLowerCase(Locale.ROOT), ours.toLowerCase(Locale.ROOT));
                }
            }
        }

        adminSteps = new ArrayList<>();
        List<?> raw = c.getList("admin-gui.steps");
        if (raw != null) {
            for (Object o : raw) {
                if (adminSteps.size() >= 5) {
                    break;
                }
                try {
                    long u = Money.toUnits(new BigDecimal(String.valueOf(o)));
                    if (u > 0) {
                        adminSteps.add(u);
                    }
                } catch (RuntimeException ignored) {
                    // skip invalid entries
                }
            }
        }
        if (adminSteps.isEmpty()) {
            for (long v : new long[]{1, 10, 100, 1000, 10000}) {
                adminSteps.add(v * Money.FACTOR);
            }
        }
    }

    /** Reads up to 5 positive amounts for the +/- buttons; falls back to 1, 10, 100, 1000, 10000. */
    private static List<Long> parseSteps(FileConfiguration c, String path) {
        List<Long> out = new ArrayList<>();
        List<?> raw = c.getList(path);
        if (raw != null) {
            for (Object o : raw) {
                if (out.size() >= 5) {
                    break;
                }
                try {
                    long u = Money.toUnits(new BigDecimal(String.valueOf(o)));
                    if (u > 0) {
                        out.add(u);
                    }
                } catch (RuntimeException ignored) {
                    // skip invalid entries
                }
            }
        }
        if (out.isEmpty()) {
            for (long v : new long[]{1, 10, 100, 1000, 10000}) {
                out.add(v * Money.FACTOR);
            }
        }
        return out;
    }

    private static char firstChar(String s, char def) {
        return s == null || s.isEmpty() ? def : s.charAt(0);
    }

    private static long cfgUnits(FileConfiguration c, String path, double def) {
        try {
            return Money.toUnits(BigDecimal.valueOf(c.getDouble(path, def)));
        } catch (RuntimeException e) {
            return Money.toUnits(BigDecimal.valueOf(def));
        }
    }

    // ---------- conversion & formatting ----------

    /** Converts a double (Vault) to units, rounded to the configured decimals. Returns -1 if invalid. */
    public long unitsFromDouble(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d) || d < 0) {
            return -1;
        }
        return unitsFromDecimal(BigDecimal.valueOf(d));
    }

    /** Converts a BigDecimal to units, rounded to the configured decimals. Returns -1 if invalid. */
    public long unitsFromDecimal(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return -1;
        }
        try {
            return Money.toUnits(value.setScale(decimals, RoundingMode.HALF_UP));
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    public long calculateTax(long units) {
        if (taxPercent.signum() <= 0) {
            return 0;
        }
        long raw = BigDecimal.valueOf(units).multiply(taxPercent)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).longValue();
        return raw - (raw % stepUnits);
    }

    private DecimalFormatSymbols symbols() {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(thousandsSeparator);
        sym.setDecimalSeparator(decimalSeparator);
        return sym;
    }

    /** Number without symbol. */
    public String formatNumber(long units, boolean grouped) {
        boolean negative = units < 0;
        BigDecimal bd = Money.toDecimal(Math.abs(units)).setScale(decimals, RoundingMode.DOWN);
        String pattern = (grouped ? "#,##0" : "0") + (decimals > 0 ? "." + "0".repeat(decimals) : "");
        DecimalFormat df = new DecimalFormat(pattern, symbols());
        df.setRoundingMode(RoundingMode.DOWN);
        String s = df.format(bd);
        return negative ? "-" + s : s;
    }

    /** Full currency format, e.g. "$1,234.50". */
    public String format(long units) {
        if (units < 0) {
            return "-" + format(-units);
        }
        return format.replace("{symbol}", symbol)
                .replace("{name}", units == Money.FACTOR ? nameSingular : namePlural)
                .replace("{amount}", formatNumber(units, grouping));
    }

    /** Short form, e.g. 1.5k, 2.3M. */
    public String formatShort(long units) {
        boolean negative = units < 0;
        double v = Math.abs(units) / (double) Money.FACTOR;
        if (v < 1000) {
            return formatNumber(units, false);
        }
        String[] suffix = {"", "k", "M", "B", "T"};
        int i = 0;
        while (v >= 1000 && i < suffix.length - 1) {
            v /= 1000;
            i++;
        }
        DecimalFormat df = new DecimalFormat("0.##", symbols());
        df.setRoundingMode(RoundingMode.DOWN);
        return (negative ? "-" : "") + df.format(v) + suffix[i];
    }

    // ---------- getters ----------

    public String language() { return language; }
    public String storageType() { return storageType; }
    public String mysqlHost() { return mysqlHost; }
    public int mysqlPort() { return mysqlPort; }
    public String mysqlDatabase() { return mysqlDatabase; }
    public String mysqlUser() { return mysqlUser; }
    public String mysqlPassword() { return mysqlPassword; }
    public boolean mysqlSsl() { return mysqlSsl; }
    public String nameSingular() { return nameSingular; }
    public String namePlural() { return namePlural; }
    public String symbol() { return symbol; }
    public int decimals() { return decimals; }
    public long startingUnits() { return startingUnits; }
    public long minUnits() { return minUnits; }
    public long maxUnits() { return maxUnits; }
    public boolean payEnabled() { return payEnabled; }
    public long payMin() { return payMin; }
    public long payMax() { return payMax; }
    public int payCooldown() { return payCooldown; }
    public long payConfirmAbove() { return payConfirmAbove; }
    public int topCacheSeconds() { return topCacheSeconds; }
    public int topPerPage() { return topPerPage; }
    public Set<String> topHidden() { return topHidden; }
    public boolean logTransactions() { return logTransactions; }
    public List<Long> adminSteps() { return adminSteps; }
    public List<Long> payGuiSteps() { return payGuiSteps; }
    public int chatInputSeconds() { return chatInputSeconds; }
    public boolean exchangeEnabled() { return exchangeEnabled; }
    public String exchangePlugin() { return exchangePlugin; }
    public String exchangeCommand() { return exchangeCommand; }
    public boolean takeoverEnabled() { return takeoverEnabled; }
    public Map<String, String> takeover() { return takeover; }
}

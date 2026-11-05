package pl.ingotlabs.base.economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import pl.ingotlabs.base.IngotLabsBase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public API for other plugins (e.g. WebMarket). Registered as a Bukkit service.
 * <pre>IngotAPI api = IngotAPI.get();</pre>
 * All methods are thread-safe. Amounts are rounded to the configured number of decimals.
 */
public final class IngotAPI {
    private final IngotLabsBase plugin;

    public IngotAPI(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    /** @return the API or null if Ingot Labs Base is not running. */
    public static IngotAPI get() {
        RegisteredServiceProvider<IngotAPI> rsp = Bukkit.getServicesManager().getRegistration(IngotAPI.class);
        return rsp == null ? null : rsp.getProvider();
    }

    public boolean hasAccount(UUID uuid) {
        return plugin.accounts().has(uuid);
    }

    public void createAccount(UUID uuid, String name) {
        plugin.accounts().getOrCreate(uuid, name);
    }

    public BigDecimal getBalance(UUID uuid) {
        return Money.toDecimal(plugin.accounts().balance(uuid));
    }

    public boolean has(UUID uuid, BigDecimal amount) {
        long units = plugin.settings().unitsFromDecimal(amount);
        return units >= 0 && plugin.accounts().balance(uuid) >= units;
    }

    public TxResult deposit(UUID uuid, BigDecimal amount, String reason) {
        long units = plugin.settings().unitsFromDecimal(amount);
        if (units < 0) {
            return new TxResult(TxResult.Status.INVALID_AMOUNT, plugin.accounts().balance(uuid));
        }
        return plugin.accounts().deposit(uuid, units, TxType.DEPOSIT, null, reason);
    }

    public TxResult withdraw(UUID uuid, BigDecimal amount, String reason) {
        long units = plugin.settings().unitsFromDecimal(amount);
        if (units < 0) {
            return new TxResult(TxResult.Status.INVALID_AMOUNT, plugin.accounts().balance(uuid));
        }
        return plugin.accounts().withdraw(uuid, units, TxType.WITHDRAW, null, reason);
    }

    /** Atomic transfer without fee. The result's balance is the sender's new balance. */
    public TxResult transfer(UUID from, UUID to, BigDecimal amount, String reason) {
        long units = plugin.settings().unitsFromDecimal(amount);
        if (units <= 0) {
            return new TxResult(TxResult.Status.INVALID_AMOUNT, plugin.accounts().balance(from));
        }
        return plugin.accounts().transfer(from, to, units, 0, reason);
    }

    public String format(BigDecimal amount) {
        long units = plugin.settings().unitsFromDecimal(amount.abs());
        String s = plugin.settings().format(Math.max(units, 0));
        return amount.signum() < 0 ? "-" + s : s;
    }

    public String currencySymbol() {
        return plugin.settings().symbol();
    }

    public List<TopManager.Entry> getTop(int limit) {
        return plugin.top().slice(0, limit);
    }
}

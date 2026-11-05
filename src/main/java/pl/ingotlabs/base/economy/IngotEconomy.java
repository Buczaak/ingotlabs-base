package pl.ingotlabs.base.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.Settings;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Vault (legacy API) economy provider. Works with Vault and VaultUnlocked. */
@SuppressWarnings("deprecation")
public final class IngotEconomy implements Economy {
    private final IngotLabsBase plugin;

    public IngotEconomy(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    private AccountManager am() {
        return plugin.accounts();
    }

    private Settings s() {
        return plugin.settings();
    }

    // ---------- helpers ----------

    private UUID uuidOf(String name) {
        if (name == null) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return am().getOrCreate(online.getUniqueId(), online.getName()).uuid();
        }
        Account a = am().find(name);
        if (a != null) {
            return a.uuid();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null) {
            Account ensured = ensure(cached);
            return ensured == null ? null : ensured.uuid();
        }
        return null;
    }

    private UUID uuidOf(OfflinePlayer p) {
        Account a = ensure(p);
        return a == null ? null : a.uuid();
    }

    /** Returns the account, creating it lazily for players who are online or have played before. */
    private Account ensure(OfflinePlayer p) {
        if (p == null) {
            return null;
        }
        Account a = am().get(p.getUniqueId());
        if (a != null) {
            return a;
        }
        if (p.isOnline() || p.hasPlayedBefore()) {
            return am().getOrCreate(p.getUniqueId(), p.getName());
        }
        return null;
    }

    private double balanceOf(UUID id) {
        return id == null ? 0 : Money.toDouble(am().balance(id));
    }

    private boolean hasFunds(UUID id, double amount) {
        if (id == null) {
            return false;
        }
        long units = s().unitsFromDouble(amount);
        return units >= 0 && am().balance(id) >= units;
    }

    private EconomyResponse fail(double balance, String message) {
        return new EconomyResponse(0, balance, ResponseType.FAILURE, message);
    }

    private EconomyResponse respond(double amount, TxResult r, UUID id) {
        if (r.success()) {
            return new EconomyResponse(amount, Money.toDouble(r.balance()), ResponseType.SUCCESS, null);
        }
        String msg = switch (r.status()) {
            case NO_ACCOUNT -> "Account not found";
            case INSUFFICIENT_FUNDS -> "Insufficient funds";
            case OVER_LIMIT -> "Balance limit exceeded";
            default -> "Invalid amount";
        };
        return fail(balanceOf(id), msg);
    }

    private EconomyResponse deposit(UUID id, double amount) {
        if (id == null) {
            return fail(0, "Account not found");
        }
        long units = s().unitsFromDouble(amount);
        if (units < 0) {
            return fail(balanceOf(id), "Invalid amount");
        }
        return respond(amount, am().deposit(id, units, TxType.DEPOSIT, null, "vault"), id);
    }

    private EconomyResponse withdraw(UUID id, double amount) {
        if (id == null) {
            return fail(0, "Account not found");
        }
        long units = s().unitsFromDouble(amount);
        if (units < 0) {
            return fail(balanceOf(id), "Invalid amount");
        }
        return respond(amount, am().withdraw(id, units, TxType.WITHDRAW, null, "vault"), id);
    }

    private boolean create(OfflinePlayer p) {
        if (p == null) {
            return false;
        }
        am().getOrCreate(p.getUniqueId(), p.getName());
        return true;
    }

    private boolean createByName(String name) {
        UUID id = uuidOf(name);
        if (id != null) {
            return true;
        }
        OfflinePlayer p = Bukkit.getOfflinePlayerIfCached(name);
        return p != null && create(p);
    }

    private EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    // ---------- general ----------

    @Override
    public boolean isEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public String getName() {
        return "IngotLabsBase";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return s().decimals();
    }

    @Override
    public String format(double amount) {
        try {
            return s().format(Money.toUnits(BigDecimal.valueOf(amount)));
        } catch (RuntimeException e) {
            return String.valueOf(amount);
        }
    }

    @Override
    public String currencyNamePlural() {
        return s().namePlural();
    }

    @Override
    public String currencyNameSingular() {
        return s().nameSingular();
    }

    // ---------- accounts ----------

    @Override
    public boolean hasAccount(String playerName) {
        return uuidOf(playerName) != null;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return ensure(player) != null;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createByName(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return create(player);
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return createByName(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return create(player);
    }

    // ---------- balance ----------

    @Override
    public double getBalance(String playerName) {
        return balanceOf(uuidOf(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return balanceOf(uuidOf(player));
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return hasFunds(uuidOf(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return hasFunds(uuidOf(player), amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    // ---------- withdraw / deposit ----------

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdraw(uuidOf(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(uuidOf(player), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return deposit(uuidOf(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(uuidOf(player), amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    // ---------- banks (not supported) ----------

    @Override
    public EconomyResponse createBank(String name, String player) {
        return noBanks();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return noBanks();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return noBanks();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }
}

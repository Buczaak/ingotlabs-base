package pl.ingotlabs.base.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player-to-player transfers, shared by /pay and the transfer menu. */
public final class PayService {
    private final IngotLabsBase plugin;
    private final Map<UUID, Long> lastPay = new ConcurrentHashMap<>();

    public PayService(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    /** Validates a transfer and tells the sender why it is not allowed. */
    public boolean check(Player from, Account target, long units) {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        if (!s.payEnabled()) {
            lang.send(from, "pay.disabled");
            return false;
        }
        if (target.uuid().equals(from.getUniqueId())) {
            lang.send(from, "pay.self");
            return false;
        }
        if (units <= 0) {
            lang.send(from, "general.invalid-amount");
            return false;
        }
        if (units < s.payMin()) {
            lang.send(from, "pay.too-low", "min", s.format(s.payMin()));
            return false;
        }
        if (s.payMax() > 0 && units > s.payMax()) {
            lang.send(from, "pay.too-high", "max", s.format(s.payMax()));
            return false;
        }
        if (s.payCooldown() > 0 && !from.hasPermission("ingotlabs.pay.bypass-cooldown")) {
            long remaining = lastPay.getOrDefault(from.getUniqueId(), 0L) + s.payCooldown() * 1000L
                    - System.currentTimeMillis();
            if (remaining > 0) {
                lang.send(from, "pay.cooldown", "seconds", String.valueOf((remaining + 999) / 1000));
                return false;
            }
        }
        return true;
    }

    /** Checks, transfers atomically and notifies both players. Returns true on success. */
    public boolean execute(Player from, Account target, long units) {
        if (!check(from, target, units)) {
            return false;
        }
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        long tax = s.calculateTax(units);
        TxResult result = plugin.accounts().transfer(from.getUniqueId(), target.uuid(), units, tax, null);
        switch (result.status()) {
            case SUCCESS -> {
                lastPay.put(from.getUniqueId(), System.currentTimeMillis());
                plugin.top().invalidate();
                if (tax > 0) {
                    lang.send(from, "pay.sent-tax", "amount", s.format(units), "player", target.name(),
                            "tax", s.format(tax), "received", s.format(units - tax));
                } else {
                    lang.send(from, "pay.sent", "amount", s.format(units), "player", target.name());
                }
                Player receiver = Bukkit.getPlayer(target.uuid());
                if (receiver != null) {
                    lang.send(receiver, "pay.received", "amount", s.format(units - tax), "player", from.getName());
                }
                return true;
            }
            case INSUFFICIENT_FUNDS -> lang.send(from, "pay.insufficient");
            case OVER_LIMIT -> lang.send(from, "pay.target-limit", "player", target.name());
            default -> lang.send(from, "general.error");
        }
        return false;
    }
}

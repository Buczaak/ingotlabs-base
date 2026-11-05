package pl.ingotlabs.base.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.TopManager;

import java.util.Locale;

/**
 * PlaceholderAPI expansion, identifier "ingot".
 * <ul>
 *   <li>%ingot_balance% - plain number, %ingot_balance_formatted% - with symbol,
 *       %ingot_balance_commas% - grouped number, %ingot_balance_short% - 1.5k</li>
 *   <li>%ingot_rank%</li>
 *   <li>%ingot_top_N_name%, %ingot_top_N_balance%, %ingot_top_N_balance_formatted%, %ingot_top_N_balance_short%</li>
 *   <li>%ingot_currency_symbol%, %ingot_currency_name%, %ingot_currency_name_plural%</li>
 * </ul>
 */
public final class IngotExpansion extends PlaceholderExpansion {
    private final IngotLabsBase plugin;

    public IngotExpansion(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "ingot";
    }

    @Override
    public String getAuthor() {
        return "Ingot Labs";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Settings s = plugin.settings();
        String p = params.toLowerCase(Locale.ROOT);

        switch (p) {
            case "currency_symbol":
                return s.symbol();
            case "currency_name":
                return s.nameSingular();
            case "currency_name_plural":
                return s.namePlural();
            default:
                break;
        }

        if (p.startsWith("top_")) {
            String[] parts = p.split("_");
            if (parts.length < 3) {
                return null;
            }
            int position;
            try {
                position = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            TopManager.Entry e = plugin.top().get(position);
            String field = p.substring(("top_" + parts[1] + "_").length());
            return switch (field) {
                case "name" -> e == null ? "-" : e.name();
                case "balance" -> s.formatNumber(e == null ? 0 : e.balance(), false);
                case "balance_formatted" -> s.format(e == null ? 0 : e.balance());
                case "balance_short" -> s.formatShort(e == null ? 0 : e.balance());
                default -> null;
            };
        }

        if (player == null) {
            return "";
        }
        long balance = plugin.accounts().balance(player.getUniqueId());
        return switch (p) {
            case "balance" -> s.formatNumber(balance, false);
            case "balance_formatted" -> s.format(balance);
            case "balance_commas" -> s.formatNumber(balance, true);
            case "balance_short" -> s.formatShort(balance);
            case "rank" -> {
                int rank = plugin.top().rank(player.getUniqueId());
                yield rank > 0 ? String.valueOf(rank) : "-";
            }
            default -> null;
        };
    }
}

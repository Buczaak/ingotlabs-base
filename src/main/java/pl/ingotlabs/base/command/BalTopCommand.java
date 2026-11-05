package pl.ingotlabs.base.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.economy.TopManager;

import java.util.List;

public final class BalTopCommand implements TabExecutor {
    private final IngotLabsBase plugin;

    public BalTopCommand(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!plugin.requirePermission(sender, "ingotlabs.top")) {
            return true;
        }
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        send(plugin, sender, page);
        return true;
    }

    /** Shared with the bank GUI. */
    public static void send(IngotLabsBase plugin, CommandSender sender, int page) {
        var lang = plugin.lang();
        var settings = plugin.settings();
        TopManager top = plugin.top();
        int perPage = settings.topPerPage();
        int total = top.size();
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        int p = Math.max(1, Math.min(page, pages));

        lang.sendPlain(sender, "top.header", "page", String.valueOf(p), "pages", String.valueOf(pages));
        if (total == 0) {
            lang.sendPlain(sender, "top.empty");
            return;
        }
        int rank = (p - 1) * perPage;
        for (TopManager.Entry e : top.slice(rank, perPage)) {
            rank++;
            lang.sendPlain(sender, "top.entry", "rank", String.valueOf(rank), "player", e.name(),
                    "balance", settings.format(e.balance()));
        }
        if (sender instanceof Player player) {
            int mine = top.rank(player.getUniqueId());
            if (mine > 0) {
                lang.sendPlain(sender, "top.your-rank", "rank", String.valueOf(mine), "balance",
                        settings.format(plugin.accounts().balance(player.getUniqueId())));
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

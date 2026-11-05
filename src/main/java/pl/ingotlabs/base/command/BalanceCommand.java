package pl.ingotlabs.base.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.economy.Account;

import java.util.List;
import java.util.Locale;

public final class BalanceCommand implements TabExecutor {
    private final IngotLabsBase plugin;

    public BalanceCommand(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var lang = plugin.lang();
        var settings = plugin.settings();
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                lang.send(sender, "general.players-only");
                return true;
            }
            if (!plugin.requirePermission(player, "ingotlabs.balance")) {
                return true;
            }
            Account acc = plugin.accounts().getOrCreate(player.getUniqueId(), player.getName());
            lang.send(player, "balance.self", "balance", settings.format(acc.balance()));
            return true;
        }
        if (!plugin.requirePermission(sender, "ingotlabs.balance.others")) {
            return true;
        }
        Account acc = plugin.findAccount(args[0]);
        if (acc == null) {
            lang.send(sender, "general.player-not-found", "player", args[0]);
            return true;
        }
        lang.send(sender, "balance.other", "player", acc.name(), "balance", settings.format(acc.balance()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("ingotlabs.balance.others")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        return List.of();
    }
}

package pl.ingotlabs.base.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.gui.BankGui;

import java.util.List;

public final class BankCommand implements TabExecutor {
    private final IngotLabsBase plugin;

    public BankCommand(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.lang().send(sender, "general.players-only");
            return true;
        }
        if (!plugin.requirePermission(player, "ingotlabs.bank")) {
            return true;
        }
        plugin.accounts().getOrCreate(player.getUniqueId(), player.getName());
        new BankGui(plugin, player).open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

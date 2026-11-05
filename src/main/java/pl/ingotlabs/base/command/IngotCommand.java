package pl.ingotlabs.base.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import pl.ingotlabs.base.IngotLabsBase;

import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** /ingotlabs - plugin info and /ingotlabs reload (config.yml + language files). */
public final class IngotCommand implements TabExecutor {
    private final IngotLabsBase plugin;

    public IngotCommand(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!plugin.requirePermission(sender, "ingotlabs.admin.reload")) {
                return true;
            }
            try {
                plugin.reload();
                plugin.lang().send(sender, "general.reloaded");
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.SEVERE, "Reload failed", e);
                plugin.lang().send(sender, "general.error");
            }
            return true;
        }
        plugin.lang().sendLines(sender, "about.info",
                "version", plugin.getPluginMeta().getVersion(), "label", label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("ingotlabs.admin.reload")
                && "reload".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("reload");
        }
        return List.of();
    }
}

package pl.ingotlabs.base.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerLoadEvent;
import pl.ingotlabs.base.IngotLabsBase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Makes Ingot Labs commands win over commands of other plugins (EssentialsX /balance, /pay, /eco ...).
 * <ol>
 *   <li>After the server has loaded, conflicting entries in the command map are swapped for ours.</li>
 *   <li>As a safety net, a command typed with a taken-over label that still reaches another plugin
 *       is redirected to Ingot Labs before it runs.</li>
 * </ol>
 * The original commands are put back when the plugin is disabled or reloaded.
 */
public final class CommandTakeover implements Listener {
    private final IngotLabsBase plugin;
    private final Map<String, Command> replaced = new LinkedHashMap<>();

    public CommandTakeover(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    private Map<String, Command> known() {
        return Bukkit.getCommandMap().getKnownCommands();
    }

    private boolean isOurs(Command c) {
        return c instanceof AliasCommand || (c instanceof PluginCommand pc && pc.getPlugin() == plugin);
    }

    private static String owner(Command c) {
        return c instanceof PluginCommand pc ? pc.getPlugin().getName() : c.getClass().getSimpleName();
    }

    /** Swaps conflicting commands for ours. Safe to call repeatedly. */
    public void apply() {
        if (!plugin.settings().takeoverEnabled()) {
            return;
        }
        Map<String, Command> known = known();
        List<String> taken = new ArrayList<>();
        for (Map.Entry<String, String> e : plugin.settings().takeover().entrySet()) {
            String label = e.getKey();
            PluginCommand mine = plugin.getCommand(e.getValue());
            if (mine == null) {
                continue;
            }
            Command current = known.get(label);
            if (current == null || isOurs(current)) {
                continue;
            }
            replaced.putIfAbsent(label, current);
            known.put(label, mine);
            taken.add("/" + label + " (" + owner(current) + ")");
        }
        if (!taken.isEmpty()) {
            plugin.getLogger().info("Took over commands from other plugins: " + String.join(", ", taken));
            sync();
        }
    }

    /** Puts the original commands back. */
    public void restore() {
        Map<String, Command> known = known();
        for (Map.Entry<String, Command> e : replaced.entrySet()) {
            Command now = known.get(e.getKey());
            if (now != null && isOurs(now)) {
                known.put(e.getKey(), e.getValue());
            }
        }
        replaced.clear();
    }

    /** Refreshes the command tree sent to clients, so tab completion matches the new owners. */
    private void sync() {
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older/other server software - the safety net below still redirects the commands
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateCommands();
        }
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        apply();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.length() > 1 && redirect(event.getPlayer(), message.substring(1))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (redirect(event.getSender(), event.getCommand())) {
            event.setCancelled(true);
        }
    }

    /** Runs our command instead when another plugin still owns the typed label. */
    private boolean redirect(org.bukkit.command.CommandSender sender, String line) {
        if (!plugin.settings().takeoverEnabled()) {
            return false;
        }
        int space = line.indexOf(' ');
        String label = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        String ours = plugin.settings().takeover().get(label);
        if (ours == null) {
            return false;
        }
        Command current = known().get(label);
        if (current == null || isOurs(current)) {
            return false;
        }
        PluginCommand mine = plugin.getCommand(ours);
        if (mine == null) {
            return false;
        }
        String rest = space < 0 ? "" : line.substring(space + 1);
        String[] args = rest.isEmpty() ? new String[0] : rest.split(" ");
        mine.execute(sender, label, args);
        return true;
    }
}

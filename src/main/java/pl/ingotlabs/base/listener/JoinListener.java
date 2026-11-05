package pl.ingotlabs.base.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.ingotlabs.base.IngotLabsBase;

public final class JoinListener implements Listener {
    private final IngotLabsBase plugin;

    public JoinListener(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.accounts().getOrCreate(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}

package pl.ingotlabs.base.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import pl.ingotlabs.base.IngotLabsBase;

public abstract class IngotGui implements InventoryHolder {
    protected final IngotLabsBase plugin;
    protected Inventory inventory;

    protected IngotGui(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public abstract void onClick(InventoryClickEvent event);

    public void open(Player player) {
        player.openInventory(inventory);
    }

    /** Opens on the next tick - safe to call from inside a click handler. */
    protected void openLater(Player player, IngotGui gui) {
        Bukkit.getScheduler().runTask(plugin, () -> gui.open(player));
    }

    protected void closeLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }
}

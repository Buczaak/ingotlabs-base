package pl.ingotlabs.base.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public final class GuiItems {
    private GuiItems() {
    }

    public static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                    .toList());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        stack.setItemMeta(meta);
        return stack;
    }

    /** Player head with the skin of the given player; the lore shows on hover. */
    public static ItemStack head(UUID owner, Component name, List<Component> lore) {
        ItemStack stack = item(Material.PLAYER_HEAD, name, lore);
        if (stack.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static ItemStack filler() {
        return item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), null);
    }
}

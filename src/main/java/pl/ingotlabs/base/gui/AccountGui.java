package pl.ingotlabs.base.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.util.GuiItems;

import java.util.Locale;

/** "Your account" menu: balance, ranking position and a way back. */
public final class AccountGui extends IngotGui {
    private static final int SLOT_HEAD = 4;
    private static final int SLOT_BALANCE = 11;
    private static final int SLOT_RANK = 15;
    private static final int SLOT_BACK = 22;

    private final Player viewer;

    public AccountGui(IngotLabsBase plugin, Player viewer) {
        super(plugin);
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 27, plugin.lang().plain("gui.account.title"));
        render();
    }

    private void render() {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        long balance = plugin.accounts().balance(viewer.getUniqueId());
        int rank = plugin.top().rank(viewer.getUniqueId());
        String share = String.format(Locale.ROOT, "%.2f", plugin.top().sharePercent(balance));

        inventory.setItem(SLOT_HEAD, GuiItems.head(viewer.getUniqueId(),
                lang.plain("gui.account.head.name", "player", viewer.getName()),
                lang.lines("gui.account.head.lore", "currency", s.namePlural())));
        inventory.setItem(SLOT_BALANCE, GuiItems.item(Material.GOLD_INGOT,
                lang.plain("gui.account.balance.name"),
                lang.lines("gui.account.balance.lore", "balance", s.format(balance),
                        "short", s.formatShort(balance))));
        inventory.setItem(SLOT_RANK, GuiItems.item(Material.GOLD_BLOCK,
                lang.plain("gui.account.rank.name"),
                lang.lines("gui.account.rank.lore", "rank", rank > 0 ? "#" + rank : "-",
                        "share", share, "accounts", String.valueOf(plugin.top().size()))));
        inventory.setItem(SLOT_BACK, GuiItems.item(Material.BARRIER, lang.plain("gui.account.back"), null));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getRawSlot() == SLOT_BACK) {
            openLater(viewer, new BankGui(plugin, viewer));
        }
    }
}

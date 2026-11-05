package pl.ingotlabs.base.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.TopManager;
import pl.ingotlabs.base.util.GuiItems;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Ranking of the richest players as player heads. Hover a head to see the statistics. */
public final class RankGui extends IngotGui {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_ME = 49;
    private static final int SLOT_NEXT = 53;

    private final Player viewer;
    private final int page;
    private final int pages;

    public RankGui(IngotLabsBase plugin, Player viewer, int page) {
        super(plugin);
        this.viewer = viewer;
        int total = plugin.top().size();
        this.pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().plain("gui.rank.title",
                "page", String.valueOf(this.page + 1), "pages", String.valueOf(pages)));
        render();
    }

    private String share(long units) {
        return String.format(Locale.ROOT, "%.2f", plugin.top().sharePercent(units));
    }

    private void render() {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        TopManager top = plugin.top();

        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, GuiItems.filler());
        }

        List<TopManager.Entry> entries = top.slice(page * PER_PAGE, PER_PAGE);
        for (int i = 0; i < entries.size(); i++) {
            TopManager.Entry e = entries.get(i);
            int rank = page * PER_PAGE + i + 1;

            List<Component> lore = new ArrayList<>(lang.lines("gui.rank.head.lore",
                    "balance", s.format(e.balance()),
                    "rank", String.valueOf(rank),
                    "share", share(e.balance())));
            TopManager.Entry above = rank > 1 ? top.get(rank - 1) : null;
            TopManager.Entry below = top.get(rank + 1);
            if (above != null) {
                lore.add(lang.plain("gui.rank.head.gap-above", "above", String.valueOf(rank - 1),
                        "diff", s.format(Math.max(0, above.balance() - e.balance()))));
            } else if (below != null) {
                lore.add(lang.plain("gui.rank.head.lead", "below", String.valueOf(rank + 1),
                        "diff", s.format(Math.max(0, e.balance() - below.balance()))));
            }
            if (e.uuid().equals(viewer.getUniqueId())) {
                lore.add(Component.empty());
                lore.add(lang.plain("gui.rank.head.you"));
            }
            inventory.setItem(i, GuiItems.head(e.uuid(),
                    lang.plain("gui.rank.head.name", "rank", String.valueOf(rank), "player", e.name()), lore));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiItems.item(Material.ARROW, lang.plain("gui.rank.prev"), null));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiItems.item(Material.ARROW, lang.plain("gui.rank.next"), null));
        }
        inventory.setItem(SLOT_BACK, GuiItems.item(Material.BARRIER, lang.plain("gui.rank.back"), null));

        long mine = plugin.accounts().balance(viewer.getUniqueId());
        int myRank = top.rank(viewer.getUniqueId());
        inventory.setItem(SLOT_ME, GuiItems.head(viewer.getUniqueId(), lang.plain("gui.rank.me.name"),
                lang.lines("gui.rank.me.lore", "rank", myRank > 0 ? "#" + myRank : "-",
                        "balance", s.format(mine), "share", share(mine))));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case SLOT_PREV -> {
                if (page > 0) {
                    openLater(viewer, new RankGui(plugin, viewer, page - 1));
                }
            }
            case SLOT_NEXT -> {
                if (page < pages - 1) {
                    openLater(viewer, new RankGui(plugin, viewer, page + 1));
                }
            }
            case SLOT_BACK -> openLater(viewer, new BankGui(plugin, viewer));
            default -> {
            }
        }
    }
}

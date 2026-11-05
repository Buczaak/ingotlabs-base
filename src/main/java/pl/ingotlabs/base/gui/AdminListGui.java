package pl.ingotlabs.base.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.Account;
import pl.ingotlabs.base.util.GuiItems;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Admin panel: all accounts sorted by balance, 45 per page. */
public final class AdminListGui extends IngotGui {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final int page;
    private final UUID[] slotMap = new UUID[PER_PAGE];
    private boolean hasNext;

    public AdminListGui(IngotLabsBase plugin, int page) {
        super(plugin);
        this.page = Math.max(0, page);
        LangManager lang = plugin.lang();
        this.inventory = Bukkit.createInventory(this, 54,
                lang.plain("gui.admin.title", "page", String.valueOf(this.page + 1)));
        render();
    }

    private void render() {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        List<Account> sorted = plugin.accounts().all().stream()
                .sorted(Comparator.comparingLong(Account::balance).reversed()
                        .thenComparing(Account::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int start = page * PER_PAGE;
        hasNext = sorted.size() > start + PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int index = start + i;
            if (index >= sorted.size()) {
                break;
            }
            Account a = sorted.get(index);
            slotMap[i] = a.uuid();
            ItemStack head = GuiItems.item(Material.PLAYER_HEAD,
                    lang.plain("gui.admin.head.name", "player", a.name()),
                    lang.lines("gui.admin.head.lore", "balance", s.format(a.balance())));
            if (head.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(a.uuid()));
                head.setItemMeta(meta);
            }
            inventory.setItem(i, head);
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiItems.item(Material.ARROW, lang.plain("gui.admin.prev"), null));
        }
        if (hasNext) {
            inventory.setItem(SLOT_NEXT, GuiItems.item(Material.ARROW, lang.plain("gui.admin.next"), null));
        }
        inventory.setItem(SLOT_BACK, GuiItems.item(Material.BARRIER, lang.plain("gui.admin.back"), null));
        inventory.setItem(SLOT_INFO, GuiItems.item(Material.NETHER_STAR,
                lang.plain("gui.admin.info.name"),
                lang.lines("gui.admin.info.lore", "accounts", String.valueOf(sorted.size()))));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        if (!viewer.hasPermission("ingotlabs.admin.panel")) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < PER_PAGE) {
            UUID target = slotMap[slot];
            if (target != null) {
                openLater(viewer, new AdminPlayerGui(plugin, target, page));
            }
        } else if (slot == SLOT_PREV && page > 0) {
            openLater(viewer, new AdminListGui(plugin, page - 1));
        } else if (slot == SLOT_NEXT && hasNext) {
            openLater(viewer, new AdminListGui(plugin, page + 1));
        } else if (slot == SLOT_BACK) {
            openLater(viewer, new BankGui(plugin, viewer));
        }
    }
}

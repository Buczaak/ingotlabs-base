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
import pl.ingotlabs.base.economy.TxResult;
import pl.ingotlabs.base.economy.TxType;
import pl.ingotlabs.base.util.GuiItems;
import pl.ingotlabs.base.util.HistoryFormatter;

import java.util.List;
import java.util.UUID;

/** Admin editor for one player's balance (27 slots). */
public final class AdminPlayerGui extends IngotGui {
    private static final int SLOT_HEAD = 4;
    private static final int ADD_ROW = 10;
    private static final int TAKE_ROW = 19;
    private static final int SLOT_RESET = 16;
    private static final int SLOT_HISTORY = 17;
    private static final int SLOT_SET = 25;
    private static final int SLOT_BACK = 26;

    private final UUID target;
    private final int returnPage;

    public AdminPlayerGui(IngotLabsBase plugin, UUID target, int returnPage) {
        super(plugin);
        this.target = target;
        this.returnPage = returnPage;
        Account acc = plugin.accounts().get(target);
        String name = acc == null ? target.toString() : acc.name();
        this.inventory = Bukkit.createInventory(this, 27, plugin.lang().plain("gui.player.title", "player", name));
        render();
    }

    private void render() {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        Account acc = plugin.accounts().get(target);
        String name = acc == null ? target.toString() : acc.name();
        long balance = acc == null ? 0 : acc.balance();

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        ItemStack head = GuiItems.item(Material.PLAYER_HEAD,
                lang.plain("gui.admin.head.name", "player", name),
                lang.lines("gui.admin.head.lore", "balance", s.format(balance)));
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(target));
            head.setItemMeta(meta);
        }
        inventory.setItem(SLOT_HEAD, head);

        List<Long> steps = s.adminSteps();
        for (int i = 0; i < steps.size(); i++) {
            String amount = s.format(steps.get(i));
            inventory.setItem(ADD_ROW + i, GuiItems.item(Material.LIME_CONCRETE,
                    lang.plain("gui.player.add", "amount", amount), null));
            inventory.setItem(TAKE_ROW + i, GuiItems.item(Material.RED_CONCRETE,
                    lang.plain("gui.player.take", "amount", amount), null));
        }
        inventory.setItem(SLOT_RESET, GuiItems.item(Material.TNT, lang.plain("gui.player.reset.name"),
                lang.lines("gui.player.reset.lore", "amount", s.format(s.startingUnits()))));
        inventory.setItem(SLOT_HISTORY, GuiItems.item(Material.BOOK, lang.plain("gui.player.history.name"), null));
        inventory.setItem(SLOT_SET, GuiItems.item(Material.NAME_TAG, lang.plain("gui.player.set.name"),
                lang.lines("gui.player.set.lore")));
        inventory.setItem(SLOT_BACK, GuiItems.item(Material.ARROW, lang.plain("gui.player.back"), null));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) {
            return;
        }
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        int slot = event.getRawSlot();
        List<Long> steps = s.adminSteps();
        Account acc = plugin.accounts().get(target);
        if (acc == null) {
            return;
        }

        if (slot >= ADD_ROW && slot < ADD_ROW + steps.size()) {
            if (viewer.hasPermission("ingotlabs.admin.give")) {
                apply(viewer, plugin.accounts().deposit(target, steps.get(slot - ADD_ROW),
                        TxType.ADMIN_GIVE, viewer.getName(), "admin gui"));
            } else {
                lang.send(viewer, "general.no-permission");
            }
        } else if (slot >= TAKE_ROW && slot < TAKE_ROW + steps.size()) {
            if (viewer.hasPermission("ingotlabs.admin.take")) {
                apply(viewer, plugin.accounts().withdraw(target, steps.get(slot - TAKE_ROW),
                        TxType.ADMIN_TAKE, viewer.getName(), "admin gui"));
            } else {
                lang.send(viewer, "general.no-permission");
            }
        } else if (slot == SLOT_RESET) {
            if (viewer.hasPermission("ingotlabs.admin.reset")) {
                apply(viewer, plugin.accounts().set(target, s.startingUnits(),
                        TxType.ADMIN_RESET, viewer.getName(), "admin gui"));
            } else {
                lang.send(viewer, "general.no-permission");
            }
        } else if (slot == SLOT_SET) {
            if (viewer.hasPermission("ingotlabs.admin.set")) {
                closeLater(viewer);
                lang.sendPlain(viewer, "gui.player.set.hint", "player", acc.name());
            } else {
                lang.send(viewer, "general.no-permission");
            }
        } else if (slot == SLOT_HISTORY) {
            if (viewer.hasPermission("ingotlabs.admin.history")) {
                HistoryGui.open(plugin, viewer, target, acc.name(), 0,
                        p -> new AdminPlayerGui(plugin, target, returnPage).open(p));
            } else {
                lang.send(viewer, "general.no-permission");
            }
        } else if (slot == SLOT_BACK) {
            openLater(viewer, new AdminListGui(plugin, returnPage));
        }
    }

    private void apply(Player viewer, TxResult result) {
        if (result.success()) {
            plugin.top().invalidate();
            render();
        } else if (result.status() == TxResult.Status.OVER_LIMIT) {
            plugin.lang().send(viewer, "ieco.over-limit", "max", plugin.settings().format(plugin.settings().maxUnits()));
        } else {
            plugin.lang().send(viewer, "ieco.insufficient", "balance", plugin.settings().format(result.balance()));
        }
    }
}

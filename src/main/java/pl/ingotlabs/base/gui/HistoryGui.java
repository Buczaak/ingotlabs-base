package pl.ingotlabs.base.gui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.Account;
import pl.ingotlabs.base.economy.TxRecord;
import pl.ingotlabs.base.economy.TxType;
import pl.ingotlabs.base.util.GuiItems;
import pl.ingotlabs.base.util.HistoryFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Transaction history as items; hover an entry to see the details. Works for players and admins. */
public final class HistoryGui extends IngotGui {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final Player viewer;
    private final UUID target;
    private final String targetName;
    private final int page;
    private final boolean hasNext;
    private final Consumer<Player> back;

    /** Loads the page asynchronously and opens the menu on the main thread. */
    public static void open(IngotLabsBase plugin, Player viewer, UUID target, String targetName, int page,
                            Consumer<Player> back) {
        final int p = Math.max(0, page);
        plugin.storage().history(target, PER_PAGE + 1, p * PER_PAGE).whenComplete((list, err) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!viewer.isOnline()) {
                    return;
                }
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING, "Could not load history", err);
                    plugin.lang().send(viewer, "general.error");
                    return;
                }
                new HistoryGui(plugin, viewer, target, targetName, p, back, list).open(viewer);
            });
        });
    }

    private HistoryGui(IngotLabsBase plugin, Player viewer, UUID target, String targetName, int page,
                       Consumer<Player> back, List<TxRecord> records) {
        super(plugin);
        this.viewer = viewer;
        this.target = target;
        this.targetName = targetName;
        this.page = page;
        this.back = back;
        this.hasNext = records.size() > PER_PAGE;
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().plain("gui.history.title",
                "player", targetName, "page", String.valueOf(page + 1)));
        render(records);
    }

    private void render(List<TxRecord> records) {
        LangManager lang = plugin.lang();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        int shown = Math.min(records.size(), PER_PAGE);
        for (int i = 0; i < shown; i++) {
            inventory.setItem(i, entryItem(records.get(i)));
        }
        if (shown == 0) {
            inventory.setItem(22, GuiItems.item(Material.BARRIER, lang.plain("gui.history.empty"), null));
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiItems.item(Material.ARROW, lang.plain("gui.history.prev"), null));
        }
        if (hasNext) {
            inventory.setItem(SLOT_NEXT, GuiItems.item(Material.ARROW, lang.plain("gui.history.next"), null));
        }
        inventory.setItem(SLOT_BACK, GuiItems.item(Material.BARRIER, lang.plain("gui.history.back"), null));
        inventory.setItem(SLOT_INFO, GuiItems.head(target, lang.plain("gui.history.info.name", "player", targetName),
                lang.lines("gui.history.info.lore", "balance",
                        plugin.settings().format(plugin.accounts().balance(target)),
                        "page", String.valueOf(page + 1))));
    }

    private ItemStack entryItem(TxRecord r) {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();

        String typeKey = "history.types." + r.type().name();
        String typeName = lang.raw(typeKey);
        if (typeName.equals(typeKey)) {
            typeName = r.type().name();
        }
        boolean plus = r.delta() >= 0;
        Component name = lang.plain(plus ? "gui.history.entry.name-plus" : "gui.history.entry.name-minus",
                "type", typeName, "amount", s.format(Math.abs(r.delta())));

        List<Component> lore = new ArrayList<>();
        lore.add(lang.plain("gui.history.entry.time", "time", HistoryFormatter.time(r.timestamp())));
        lore.add(lang.plain("gui.history.entry.balance", "balance", s.format(r.balanceAfter())));
        if (r.other() != null && !r.other().isBlank()) {
            lore.add(lang.plain("gui.history.entry.other", "other", r.other()));
        }
        if (r.note() != null && !r.note().isBlank()) {
            lore.add(lang.plain("gui.history.entry.note", "note", r.note()));
        }

        // Transfers show the head of the other player when we know them
        if ((r.type() == TxType.TRANSFER_IN || r.type() == TxType.TRANSFER_OUT) && r.other() != null) {
            Account other = plugin.accounts().find(r.other());
            if (other != null) {
                return GuiItems.head(other.uuid(), name, lore);
            }
        }
        return GuiItems.item(materialFor(r.type()), name, lore);
    }

    private static Material materialFor(TxType type) {
        return switch (type) {
            case DEPOSIT -> Material.EMERALD;
            case WITHDRAW -> Material.REDSTONE;
            case TRANSFER_IN -> Material.LIME_DYE;
            case TRANSFER_OUT -> Material.RED_DYE;
            case ADMIN_GIVE -> Material.EMERALD_BLOCK;
            case ADMIN_TAKE -> Material.REDSTONE_BLOCK;
            case ADMIN_SET -> Material.NAME_TAG;
            case ADMIN_RESET -> Material.TNT;
            case START -> Material.GOLD_INGOT;
        };
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        switch (event.getRawSlot()) {
            case SLOT_PREV -> {
                if (page > 0) {
                    open(plugin, viewer, target, targetName, page - 1, back);
                }
            }
            case SLOT_NEXT -> {
                if (hasNext) {
                    open(plugin, viewer, target, targetName, page + 1, back);
                }
            }
            case SLOT_BACK -> Bukkit.getScheduler().runTask(plugin, () -> back.accept(viewer));
            default -> {
            }
        }
    }
}

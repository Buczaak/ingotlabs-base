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

/** Main player menu (/bank). */
public final class BankGui extends IngotGui {
    private static final int SLOT_ACCOUNT = 4;
    private static final int SLOT_TOP = 20;
    private static final int SLOT_TRANSFER = 22;
    private static final int SLOT_HISTORY = 24;
    private static final int SLOT_EXCHANGE = 31;
    private static final int SLOT_ADMIN = 35;

    private final Player viewer;

    public BankGui(IngotLabsBase plugin, Player viewer) {
        super(plugin);
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 36, plugin.lang().plain("gui.bank.title"));
        render();
    }

    private boolean exchangeAvailable() {
        Settings s = plugin.settings();
        return s.exchangeEnabled() && Bukkit.getPluginManager().isPluginEnabled(s.exchangePlugin());
    }

    private void render() {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        long balance = plugin.accounts().balance(viewer.getUniqueId());
        int rank = plugin.top().rank(viewer.getUniqueId());

        inventory.setItem(SLOT_ACCOUNT, GuiItems.head(viewer.getUniqueId(),
                lang.plain("gui.bank.account.name"),
                lang.lines("gui.bank.account.lore", "balance", s.format(balance),
                        "rank", rank > 0 ? "#" + rank : "-")));

        List<Component> topLore = new ArrayList<>(lang.lines("gui.bank.top.lore"));
        for (int i = 1; i <= 5; i++) {
            TopManager.Entry e = plugin.top().get(i);
            if (e == null) {
                break;
            }
            topLore.add(lang.plain("gui.bank.top.entry", "rank", String.valueOf(i), "player", e.name(),
                    "balance", s.format(e.balance())));
        }
        inventory.setItem(SLOT_TOP, GuiItems.item(Material.GOLD_BLOCK, lang.plain("gui.bank.top.name"), topLore));

        inventory.setItem(SLOT_TRANSFER, GuiItems.item(Material.PAPER,
                lang.plain("gui.bank.transfer.name"), lang.lines("gui.bank.transfer.lore")));
        inventory.setItem(SLOT_HISTORY, GuiItems.item(Material.BOOK,
                lang.plain("gui.bank.history.name"), lang.lines("gui.bank.history.lore")));

        if (exchangeAvailable()) {
            inventory.setItem(SLOT_EXCHANGE, GuiItems.item(Material.EMERALD,
                    lang.plain("gui.bank.exchange.name"), lang.lines("gui.bank.exchange.lore")));
        }
        if (viewer.hasPermission("ingotlabs.admin.panel")) {
            inventory.setItem(SLOT_ADMIN, GuiItems.item(Material.COMMAND_BLOCK,
                    lang.plain("gui.bank.admin.name"), lang.lines("gui.bank.admin.lore")));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        LangManager lang = plugin.lang();
        switch (event.getRawSlot()) {
            case SLOT_ACCOUNT -> openLater(viewer, new AccountGui(plugin, viewer));
            case SLOT_TOP -> openLater(viewer, new RankGui(plugin, viewer, 0));
            case SLOT_TRANSFER -> {
                if (!viewer.hasPermission("ingotlabs.pay")) {
                    closeLater(viewer);
                    lang.send(viewer, "general.no-permission");
                } else if (!plugin.settings().payEnabled()) {
                    closeLater(viewer);
                    lang.send(viewer, "pay.disabled");
                } else {
                    openLater(viewer, new PayPlayerGui(plugin, viewer, 0));
                }
            }
            case SLOT_HISTORY -> HistoryGui.open(plugin, viewer, viewer.getUniqueId(), viewer.getName(), 0,
                    p -> new BankGui(plugin, p).open(p));
            case SLOT_EXCHANGE -> {
                if (exchangeAvailable()) {
                    String command = plugin.settings().exchangeCommand();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        viewer.closeInventory();
                        viewer.performCommand(command);
                    });
                }
            }
            case SLOT_ADMIN -> {
                if (viewer.hasPermission("ingotlabs.admin.panel")) {
                    openLater(viewer, new AdminListGui(plugin, 0));
                }
            }
            default -> {
            }
        }
    }
}

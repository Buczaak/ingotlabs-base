package pl.ingotlabs.base.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.economy.Account;
import pl.ingotlabs.base.util.GuiItems;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Step 1 of a transfer: pick the recipient among online players (or type an offline name in chat). */
public final class PayPlayerGui extends IngotGui {
    private static final int PER_PAGE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_NAME = 50;
    private static final int SLOT_NEXT = 53;

    private final Player viewer;
    private final int page;
    private final int pages;
    private final UUID[] slotMap = new UUID[PER_PAGE];

    public PayPlayerGui(IngotLabsBase plugin, Player viewer, int page) {
        super(plugin);
        this.viewer = viewer;
        List<Player> candidates = candidates();
        this.pages = Math.max(1, (candidates.size() + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));
        this.inventory = Bukkit.createInventory(this, 54, plugin.lang().plain("gui.pay.player-title"));
        render(candidates);
    }

    private List<Player> candidates() {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.getUniqueId().equals(viewer.getUniqueId()))
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .map(p -> (Player) p)
                .toList();
    }

    private void render(List<Player> candidates) {
        LangManager lang = plugin.lang();
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < candidates.size(); i++) {
            Player p = candidates.get(start + i);
            slotMap[i] = p.getUniqueId();
            inventory.setItem(i, GuiItems.head(p.getUniqueId(),
                    lang.plain("gui.pay.head.name", "player", p.getName()),
                    lang.lines("gui.pay.head.lore", "player", p.getName())));
        }
        if (candidates.isEmpty()) {
            inventory.setItem(22, GuiItems.item(Material.BARRIER, lang.plain("gui.pay.nobody"), null));
        }
        if (page > 0) {
            inventory.setItem(SLOT_PREV, GuiItems.item(Material.ARROW, lang.plain("gui.pay.prev"), null));
        }
        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, GuiItems.item(Material.ARROW, lang.plain("gui.pay.next"), null));
        }
        inventory.setItem(SLOT_BACK, GuiItems.item(Material.BARRIER, lang.plain("gui.pay.back"), null));
        inventory.setItem(SLOT_NAME, GuiItems.item(Material.NAME_TAG, lang.plain("gui.pay.name-search.name"),
                lang.lines("gui.pay.name-search.lore")));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < PER_PAGE) {
            UUID id = slotMap[slot];
            if (id == null) {
                return;
            }
            Player chosen = Bukkit.getPlayer(id);
            if (chosen == null) {
                openLater(viewer, new PayPlayerGui(plugin, viewer, page));
                return;
            }
            Account target = plugin.accounts().getOrCreate(chosen.getUniqueId(), chosen.getName());
            openLater(viewer, new PayAmountGui(plugin, viewer, target.uuid(), 0, page));
            return;
        }
        switch (slot) {
            case SLOT_PREV -> {
                if (page > 0) {
                    openLater(viewer, new PayPlayerGui(plugin, viewer, page - 1));
                }
            }
            case SLOT_NEXT -> {
                if (page < pages - 1) {
                    openLater(viewer, new PayPlayerGui(plugin, viewer, page + 1));
                }
            }
            case SLOT_BACK -> openLater(viewer, new BankGui(plugin, viewer));
            case SLOT_NAME -> askForName();
            default -> {
            }
        }
    }

    private void askForName() {
        LangManager lang = plugin.lang();
        closeLater(viewer);
        lang.send(viewer, "prompt.player");
        plugin.chatPrompt().ask(viewer, text -> {
            Account target = plugin.findAccount(text);
            if (target == null) {
                lang.send(viewer, "general.player-not-found", "player", text);
                new PayPlayerGui(plugin, viewer, page).open(viewer);
            } else if (target.uuid().equals(viewer.getUniqueId())) {
                lang.send(viewer, "pay.self");
                new PayPlayerGui(plugin, viewer, page).open(viewer);
            } else {
                new PayAmountGui(plugin, viewer, target.uuid(), 0, page).open(viewer);
            }
        }, () -> new PayPlayerGui(plugin, viewer, page).open(viewer));
    }
}

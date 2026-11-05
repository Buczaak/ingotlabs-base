package pl.ingotlabs.base.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.Account;
import pl.ingotlabs.base.util.AmountParser;
import pl.ingotlabs.base.util.GuiItems;

import java.util.List;
import java.util.UUID;

/** Step 2 of a transfer: choose the amount with +/- buttons or type it in chat, then confirm. */
public final class PayAmountGui extends IngotGui {
    private static final int SLOT_HEAD = 4;
    private static final int ADD_BASE = 11;
    private static final int SLOT_AMOUNT = 22;
    private static final int TAKE_BASE = 29;
    private static final int SLOT_BACK = 36;
    private static final int SLOT_CUSTOM = 38;
    private static final int SLOT_CONFIRM = 40;
    private static final int SLOT_RESET = 42;

    private final Player viewer;
    private final UUID targetId;
    private final String targetName;
    private final int returnPage;
    private long amount;

    public PayAmountGui(IngotLabsBase plugin, Player viewer, UUID targetId, long amount, int returnPage) {
        super(plugin);
        this.viewer = viewer;
        this.targetId = targetId;
        this.amount = Math.max(0, amount);
        this.returnPage = returnPage;
        Account acc = plugin.accounts().get(targetId);
        this.targetName = acc == null ? targetId.toString() : acc.name();
        this.inventory = Bukkit.createInventory(this, 45, plugin.lang().plain("gui.pay.amount-title",
                "player", targetName));
        render();
    }

    /** Highest amount the buttons allow: your balance, limited by the maximum transfer. */
    private long cap() {
        long cap = plugin.accounts().balance(viewer.getUniqueId());
        long max = plugin.settings().payMax();
        if (max > 0) {
            cap = Math.min(cap, max);
        }
        return Math.max(0, cap);
    }

    private void render() {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        for (int i = 0; i < 45; i++) {
            inventory.setItem(i, GuiItems.filler());
        }
        inventory.setItem(SLOT_HEAD, GuiItems.head(targetId,
                lang.plain("gui.pay.recipient.name", "player", targetName),
                lang.lines("gui.pay.recipient.lore")));

        List<Long> steps = s.payGuiSteps();
        for (int i = 0; i < steps.size(); i++) {
            String text = s.format(steps.get(i));
            inventory.setItem(ADD_BASE + i, GuiItems.item(Material.LIME_CONCRETE,
                    lang.plain("gui.pay.add", "amount", text), null));
            inventory.setItem(TAKE_BASE + i, GuiItems.item(Material.RED_CONCRETE,
                    lang.plain("gui.pay.take", "amount", text), null));
        }

        long tax = s.calculateTax(amount);
        long balance = plugin.accounts().balance(viewer.getUniqueId());
        inventory.setItem(SLOT_AMOUNT, GuiItems.item(Material.GOLD_INGOT,
                lang.plain("gui.pay.amount.name", "amount", s.format(amount)),
                lang.lines("gui.pay.amount.lore",
                        "tax", s.format(tax),
                        "received", s.format(Math.max(0, amount - tax)),
                        "after", s.format(balance - amount),
                        "min", s.format(s.payMin()))));

        inventory.setItem(SLOT_BACK, GuiItems.item(Material.BARRIER, lang.plain("gui.pay.back"), null));
        inventory.setItem(SLOT_CUSTOM, GuiItems.item(Material.NAME_TAG, lang.plain("gui.pay.custom.name"),
                lang.lines("gui.pay.custom.lore")));
        inventory.setItem(SLOT_CONFIRM, GuiItems.item(Material.EMERALD_BLOCK,
                lang.plain("gui.pay.confirm.name", "amount", s.format(amount)),
                lang.lines("gui.pay.confirm.lore", "player", targetName)));
        inventory.setItem(SLOT_RESET, GuiItems.item(Material.HOPPER, lang.plain("gui.pay.reset.name"), null));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        List<Long> steps = plugin.settings().payGuiSteps();

        if (slot >= ADD_BASE && slot < ADD_BASE + steps.size()) {
            amount = Math.min(cap(), amount + steps.get(slot - ADD_BASE));
            render();
        } else if (slot >= TAKE_BASE && slot < TAKE_BASE + steps.size()) {
            amount = Math.max(0, amount - steps.get(slot - TAKE_BASE));
            render();
        } else if (slot == SLOT_RESET) {
            amount = 0;
            render();
        } else if (slot == SLOT_BACK) {
            openLater(viewer, new PayPlayerGui(plugin, viewer, returnPage));
        } else if (slot == SLOT_CUSTOM) {
            askForAmount();
        } else if (slot == SLOT_CONFIRM) {
            confirm();
        }
    }

    private void askForAmount() {
        LangManager lang = plugin.lang();
        final long keep = amount;
        closeLater(viewer);
        lang.send(viewer, "prompt.amount");
        plugin.chatPrompt().ask(viewer, text -> {
            long parsed = AmountParser.parse(text, plugin.settings());
            if (parsed <= 0) {
                lang.send(viewer, "general.invalid-amount");
                new PayAmountGui(plugin, viewer, targetId, keep, returnPage).open(viewer);
            } else {
                new PayAmountGui(plugin, viewer, targetId, parsed, returnPage).open(viewer);
            }
        }, () -> new PayAmountGui(plugin, viewer, targetId, keep, returnPage).open(viewer));
    }

    private void confirm() {
        LangManager lang = plugin.lang();
        closeLater(viewer);
        if (amount <= 0) {
            lang.send(viewer, "pay.choose-amount");
            return;
        }
        Account target = plugin.accounts().get(targetId);
        if (target == null) {
            lang.send(viewer, "general.error");
            return;
        }
        plugin.payService().execute(viewer, target, amount);
    }
}

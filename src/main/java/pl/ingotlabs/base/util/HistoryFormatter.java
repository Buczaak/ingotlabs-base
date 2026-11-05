package pl.ingotlabs.base.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.TxRecord;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/** Shows paginated transaction history in chat (used by /ieco history and the GUIs). */
public final class HistoryFormatter {
    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private HistoryFormatter() {
    }

    public static String time(long timestamp) {
        return TIME.format(java.time.Instant.ofEpochMilli(timestamp));
    }

    public static void show(IngotLabsBase plugin, CommandSender viewer, UUID target, String targetName, int page) {
        final int p = Math.max(1, page);
        plugin.storage().history(target, PAGE_SIZE + 1, (p - 1) * PAGE_SIZE).whenComplete((list, err) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                LangManager lang = plugin.lang();
                if (err != null) {
                    plugin.getLogger().log(Level.WARNING, "Could not load history", err);
                    lang.send(viewer, "general.error");
                    return;
                }
                render(plugin, viewer, targetName, p, list);
            });
        });
    }

    private static void render(IngotLabsBase plugin, CommandSender viewer, String name, int page, List<TxRecord> list) {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        lang.sendPlain(viewer, "history.header", "player", name, "page", String.valueOf(page));
        if (list.isEmpty()) {
            lang.sendPlain(viewer, "history.empty");
            return;
        }
        int shown = Math.min(list.size(), PAGE_SIZE);
        for (int i = 0; i < shown; i++) {
            TxRecord r = list.get(i);
            String amount = (r.delta() >= 0 ? "+" : "") + s.format(r.delta());
            String type = lang.raw("history.types." + r.type().name());
            if (type.equals("history.types." + r.type().name())) {
                type = r.type().name();
            }
            Component line = lang.plain("history.entry",
                    "time", TIME.format(java.time.Instant.ofEpochMilli(r.timestamp())),
                    "type", type,
                    "amount", amount,
                    "balance", s.format(r.balanceAfter()),
                    "other", r.other() == null ? "-" : r.other());
            viewer.sendMessage(line);
        }
        if (list.size() > PAGE_SIZE) {
            lang.sendPlain(viewer, "history.more", "next", String.valueOf(page + 1));
        }
    }
}

package pl.ingotlabs.base.economy;

import pl.ingotlabs.base.config.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Cached ranking of the richest players. */
public final class TopManager {
    public record Entry(UUID uuid, String name, long balance) {
    }

    private record Snapshot(long builtAt, List<Entry> entries, Map<UUID, Integer> ranks, double total) {
    }

    private final AccountManager accounts;
    private final Settings settings;
    private volatile Snapshot snapshot;

    public TopManager(AccountManager accounts, Settings settings) {
        this.accounts = accounts;
        this.settings = settings;
    }

    public void invalidate() {
        snapshot = null;
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        long now = System.currentTimeMillis();
        if (s == null || now - s.builtAt() > settings.topCacheSeconds() * 1000L) {
            s = rebuild(now);
        }
        return s;
    }

    private synchronized Snapshot rebuild(long now) {
        Snapshot s = snapshot;
        if (s != null && now - s.builtAt() <= settings.topCacheSeconds() * 1000L) {
            return s;
        }
        List<Entry> list = new ArrayList<>();
        double total = 0;
        for (Account a : accounts.all()) {
            long balance = a.balance();
            if (balance > 0) {
                total += balance;
            }
            if (settings.topHidden().contains(a.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            list.add(new Entry(a.uuid(), a.name(), balance));
        }
        list.sort((x, y) -> {
            int c = Long.compare(y.balance(), x.balance());
            return c != 0 ? c : x.name().compareToIgnoreCase(y.name());
        });
        Map<UUID, Integer> ranks = new HashMap<>();
        for (int i = 0; i < list.size(); i++) {
            ranks.put(list.get(i).uuid(), i + 1);
        }
        s = new Snapshot(now, List.copyOf(list), ranks, total);
        snapshot = s;
        return s;
    }

    public int size() {
        return current().entries().size();
    }

    /** 1-based rank, or 0 when the player is hidden or has no account. */
    public int rank(UUID uuid) {
        return current().ranks().getOrDefault(uuid, 0);
    }

    /** 1-based position, or null if out of range. */
    public Entry get(int position) {
        List<Entry> list = current().entries();
        return position < 1 || position > list.size() ? null : list.get(position - 1);
    }

    public List<Entry> slice(int from, int count) {
        List<Entry> list = current().entries();
        if (from >= list.size()) {
            return List.of();
        }
        return list.subList(from, Math.min(list.size(), from + count));
    }

    /** Share of the given balance in all money on the server, in percent (0-100). */
    public double sharePercent(long units) {
        double total = current().total();
        if (total <= 0 || units <= 0) {
            return 0;
        }
        return Math.min(100.0, units / total * 100.0);
    }
}

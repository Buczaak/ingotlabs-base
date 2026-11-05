package pl.ingotlabs.base.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import pl.ingotlabs.base.IngotLabsBase;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.Account;
import pl.ingotlabs.base.economy.TxResult;
import pl.ingotlabs.base.economy.TxType;
import pl.ingotlabs.base.gui.AdminListGui;
import pl.ingotlabs.base.util.AmountParser;
import pl.ingotlabs.base.util.HistoryFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class IecoCommand implements TabExecutor {
    private static final List<String> SUBS =
            List.of("give", "take", "set", "reset", "check", "history", "panel", "reload");

    private final IngotLabsBase plugin;

    public IecoCommand(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LangManager lang = plugin.lang();
        if (args.length == 0) {
            boolean any = SUBS.stream().anyMatch(sub -> sender.hasPermission("ingotlabs.admin." + sub));
            if (!any) {
                lang.send(sender, "general.no-permission");
                return true;
            }
            lang.sendLines(sender, "ieco.help");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!SUBS.contains(sub)) {
            lang.send(sender, "general.unknown-subcommand");
            return true;
        }
        if (!plugin.requirePermission(sender, "ingotlabs.admin." + sub)) {
            return true;
        }
        switch (sub) {
            case "give", "take", "set" -> modify(sender, label, sub, args);
            case "reset" -> reset(sender, label, args);
            case "check" -> check(sender, label, args);
            case "history" -> history(sender, label, args);
            case "panel" -> {
                if (sender instanceof Player p) {
                    new AdminListGui(plugin, 0).open(p);
                } else {
                    lang.send(sender, "general.players-only");
                }
            }
            case "reload" -> {
                plugin.reload();
                lang.send(sender, "general.reloaded");
            }
            default -> lang.send(sender, "general.unknown-subcommand");
        }
        return true;
    }

    private void modify(CommandSender sender, String label, String sub, String[] args) {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        if (args.length < 3) {
            lang.send(sender, "ieco.usage", "usage", "/" + label + " " + sub + " [player] [amount]");
            return;
        }
        Account acc = plugin.findAccount(args[1]);
        if (acc == null) {
            lang.send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        long units = AmountParser.parse(args[2], s);
        if (units < 0 || (!sub.equals("set") && units == 0)) {
            lang.send(sender, "general.invalid-amount");
            return;
        }
        String admin = sender.getName();
        TxResult r = switch (sub) {
            case "give" -> plugin.accounts().deposit(acc.uuid(), units, TxType.ADMIN_GIVE, admin, "admin");
            case "take" -> plugin.accounts().withdraw(acc.uuid(), units, TxType.ADMIN_TAKE, admin, "admin");
            default -> plugin.accounts().set(acc.uuid(), units, TxType.ADMIN_SET, admin, "admin");
        };
        respond(sender, "ieco." + sub, acc, units, r);
    }

    private void reset(CommandSender sender, String label, String[] args) {
        LangManager lang = plugin.lang();
        if (args.length < 2) {
            lang.send(sender, "ieco.usage", "usage", "/" + label + " reset [player]");
            return;
        }
        Account acc = plugin.findAccount(args[1]);
        if (acc == null) {
            lang.send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        long units = plugin.settings().startingUnits();
        TxResult r = plugin.accounts().set(acc.uuid(), units, TxType.ADMIN_RESET, sender.getName(), "admin");
        respond(sender, "ieco.reset", acc, units, r);
    }

    private void respond(CommandSender sender, String key, Account acc, long units, TxResult r) {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        switch (r.status()) {
            case SUCCESS -> lang.send(sender, key, "player", acc.name(), "amount", s.format(units),
                    "balance", s.format(r.balance()));
            case INSUFFICIENT_FUNDS, INVALID_AMOUNT -> lang.send(sender, "ieco.insufficient",
                    "balance", s.format(r.balance()));
            case OVER_LIMIT -> lang.send(sender, "ieco.over-limit", "max", s.format(s.maxUnits()));
            default -> lang.send(sender, "general.error");
        }
    }

    private void check(CommandSender sender, String label, String[] args) {
        LangManager lang = plugin.lang();
        if (args.length < 2) {
            lang.send(sender, "ieco.usage", "usage", "/" + label + " check [player]");
            return;
        }
        Account acc = plugin.findAccount(args[1]);
        if (acc == null) {
            lang.send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        int rank = plugin.top().rank(acc.uuid());
        lang.send(sender, "ieco.check", "player", acc.name(),
                "balance", plugin.settings().format(acc.balance()),
                "rank", rank > 0 ? "#" + rank : "-");
    }

    private void history(CommandSender sender, String label, String[] args) {
        LangManager lang = plugin.lang();
        if (args.length < 2) {
            lang.send(sender, "ieco.usage", "usage", "/" + label + " history [player] [page]");
            return;
        }
        Account acc = plugin.findAccount(args[1]);
        if (acc == null) {
            lang.send(sender, "general.player-not-found", "player", args[1]);
            return;
        }
        int page = 1;
        if (args.length > 2) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
        }
        HistoryFormatter.show(plugin, sender, acc.uuid(), acc.name(), page);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String sub : SUBS) {
                if (sub.startsWith(prefix) && sender.hasPermission("ingotlabs.admin." + sub)) {
                    out.add(sub);
                }
            }
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && List.of("give", "take", "set", "reset", "check", "history").contains(sub)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 3 && List.of("give", "take", "set").contains(sub)) {
            return List.of("10", "100", "1000").stream().filter(v -> v.startsWith(args[2])).toList();
        }
        return List.of();
    }
}

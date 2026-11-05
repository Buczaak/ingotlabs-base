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
import pl.ingotlabs.base.gui.PayAmountGui;
import pl.ingotlabs.base.gui.PayPlayerGui;
import pl.ingotlabs.base.util.AmountParser;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PayCommand implements TabExecutor {
    private record Pending(UUID target, long units, long expires) {
    }

    private static final long CONFIRM_MILLIS = 15_000L;

    private final IngotLabsBase plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public PayCommand(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LangManager lang = plugin.lang();
        Settings s = plugin.settings();
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.players-only");
            return true;
        }
        if (!plugin.requirePermission(player, "ingotlabs.pay")) {
            return true;
        }
        if (!s.payEnabled()) {
            lang.send(player, "pay.disabled");
            return true;
        }
        plugin.accounts().getOrCreate(player.getUniqueId(), player.getName());

        // /pay -> choose a player in the menu, /pay <player> -> choose the amount in the menu
        if (args.length == 0) {
            new PayPlayerGui(plugin, player, 0).open(player);
            return true;
        }
        Account target = plugin.findAccount(args[0]);
        if (target == null) {
            lang.send(player, "general.player-not-found", "player", args[0]);
            return true;
        }
        if (target.uuid().equals(player.getUniqueId())) {
            lang.send(player, "pay.self");
            return true;
        }
        if (args.length == 1) {
            new PayAmountGui(plugin, player, target.uuid(), 0, 0).open(player);
            return true;
        }

        long units = AmountParser.parse(args[1], s);
        if (units <= 0) {
            lang.send(player, "general.invalid-amount");
            return true;
        }
        if (!plugin.payService().check(player, target, units)) {
            return true;
        }

        long now = System.currentTimeMillis();
        if (s.payConfirmAbove() > 0 && units >= s.payConfirmAbove()) {
            Pending p = pending.get(player.getUniqueId());
            if (p == null || p.expires() < now || !p.target().equals(target.uuid()) || p.units() != units) {
                pending.put(player.getUniqueId(), new Pending(target.uuid(), units, now + CONFIRM_MILLIS));
                lang.send(player, "pay.confirm", "amount", s.format(units), "player", target.name());
                return true;
            }
            pending.remove(player.getUniqueId());
        }

        plugin.payService().execute(player, target, units);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> !n.equals(sender.getName()))
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2) {
            return List.of("10", "100", "1000").stream().filter(v -> v.startsWith(args[1])).toList();
        }
        return List.of();
    }
}

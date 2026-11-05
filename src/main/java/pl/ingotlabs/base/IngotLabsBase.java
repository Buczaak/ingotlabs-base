package pl.ingotlabs.base;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import pl.ingotlabs.base.command.AliasCommand;
import pl.ingotlabs.base.command.BalTopCommand;
import pl.ingotlabs.base.command.BalanceCommand;
import pl.ingotlabs.base.command.BankCommand;
import pl.ingotlabs.base.command.CommandTakeover;
import pl.ingotlabs.base.command.IngotCommand;
import pl.ingotlabs.base.economy.PayService;
import pl.ingotlabs.base.listener.ChatPrompt;
import pl.ingotlabs.base.command.IecoCommand;
import pl.ingotlabs.base.command.PayCommand;
import pl.ingotlabs.base.config.LangManager;
import pl.ingotlabs.base.config.Settings;
import pl.ingotlabs.base.economy.Account;
import pl.ingotlabs.base.economy.AccountManager;
import pl.ingotlabs.base.economy.IngotAPI;
import pl.ingotlabs.base.economy.IngotEconomy;
import pl.ingotlabs.base.economy.TopManager;
import pl.ingotlabs.base.hook.IngotExpansion;
import pl.ingotlabs.base.listener.GuiListener;
import pl.ingotlabs.base.listener.JoinListener;
import pl.ingotlabs.base.storage.SqlStorage;

import java.util.List;
import java.util.logging.Level;

public final class IngotLabsBase extends JavaPlugin {
    private static final List<String> COMMANDS = List.of("bank", "balance", "pay", "baltop", "ieco", "ingotlabs");

    private Settings settings;
    private LangManager lang;
    private SqlStorage storage;
    private AccountManager accounts;
    private TopManager top;
    private PayService payService;
    private ChatPrompt chatPrompt;
    private CommandTakeover takeover;
    private Runnable placeholderUnhook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings();
        settings.load(getConfig());
        lang = new LangManager(this);
        lang.load(settings.language());

        storage = new SqlStorage(this, settings);
        try {
            storage.init();
            accounts = new AccountManager(storage, settings);
            accounts.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not initialize storage, disabling plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        top = new TopManager(accounts, settings);
        payService = new PayService(this);

        getServer().getServicesManager().register(Economy.class, new IngotEconomy(this), this, ServicePriority.Highest);
        getServer().getServicesManager().register(IngotAPI.class, new IngotAPI(this), this, ServicePriority.Normal);

        registerCommands();
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new GuiListener(), this);

        chatPrompt = new ChatPrompt(this);
        getServer().getPluginManager().registerEvents(chatPrompt, this);

        // Take commands over from EssentialsX & co. once every plugin has registered its own
        takeover = new CommandTakeover(this);
        getServer().getPluginManager().registerEvents(takeover, this);
        getServer().getScheduler().runTask(this, takeover::apply);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            IngotExpansion expansion = new IngotExpansion(this);
            expansion.register();
            placeholderUnhook = expansion::unregister;
            getLogger().info("PlaceholderAPI expansion registered (%ingot_...%).");
        }

        getLogger().info("Loaded " + accounts.all().size() + " accounts. Vault provider registered.");
    }

    @Override
    public void onDisable() {
        if (placeholderUnhook != null) {
            placeholderUnhook.run();
        }
        if (takeover != null) {
            takeover.restore();
        }
        removeAliases();
        if (storage != null) {
            storage.close();
        }
    }

    private void registerCommands() {
        bind("bank", new BankCommand(this));
        bind("balance", new BalanceCommand(this));
        bind("pay", new PayCommand(this));
        bind("baltop", new BalTopCommand(this));
        bind("ieco", new IecoCommand(this));
        bind("ingotlabs", new IngotCommand(this));

        // Extra aliases from the language file (e.g. /saldo, /przelew in Polish)
        CommandMap map = Bukkit.getCommandMap();
        for (String name : COMMANDS) {
            PluginCommand target = getCommand(name);
            if (target == null) {
                continue;
            }
            for (String alias : lang.rawList("commands." + name)) {
                String a = alias.toLowerCase().trim();
                if (a.isEmpty() || a.equals(name)) {
                    continue;
                }
                map.register("ingotlabsbase", new AliasCommand(a, target));
            }
        }
    }

    private void bind(String name, Object handler) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command /" + name + " missing from plugin.yml");
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) handler);
        cmd.setTabCompleter((org.bukkit.command.TabCompleter) handler);
    }

    private void removeAliases() {
        try {
            Bukkit.getCommandMap().getKnownCommands().values().removeIf(c -> c instanceof AliasCommand);
        } catch (Exception ignored) {
            // best effort
        }
    }

    /** Reloads config.yml and the language file. Storage settings and command aliases need a restart. */
    public void reload() {
        reloadConfig();
        settings.load(getConfig());
        lang.load(settings.language());
        top.invalidate();
        takeover.restore();
        takeover.apply();
    }

    public boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        lang.send(sender, "general.no-permission");
        return false;
    }

    /** Finds an account by player name (online players get an account on demand). */
    public Account findAccount(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return accounts.getOrCreate(online.getUniqueId(), online.getName());
        }
        return accounts.find(name);
    }

    public Settings settings() {
        return settings;
    }

    public LangManager lang() {
        return lang;
    }

    public SqlStorage storage() {
        return storage;
    }

    public AccountManager accounts() {
        return accounts;
    }

    public TopManager top() {
        return top;
    }

    public PayService payService() {
        return payService;
    }

    public ChatPrompt chatPrompt() {
        return chatPrompt;
    }
}

package pl.ingotlabs.base.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.ingotlabs.base.IngotLabsBase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Loads lang/<code>.yml, falls back to the bundled English file for missing keys. */
public final class LangManager {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final IngotLabsBase plugin;
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private String prefix = "";

    public LangManager(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    public void load(String code) {
        for (String bundled : new String[]{"en", "pl"}) {
            File f = new File(plugin.getDataFolder(), "lang/" + bundled + ".yml");
            if (!f.exists()) {
                plugin.saveResource("lang/" + bundled + ".yml", false);
            }
        }
        String c = code == null ? "en" : code.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9_-]", "");
        File file = new File(plugin.getDataFolder(), "lang/" + c + ".yml");
        if (!file.exists()) {
            plugin.getLogger().warning("Language file lang/" + c + ".yml not found, using English.");
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }
        lang = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("lang/en.yml")) {
            if (in != null) {
                fallback = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // fallback stays empty
        }
        prefix = raw("prefix");
    }

    public String raw(String key) {
        String v = lang.getString(key);
        if (v == null) {
            v = fallback.getString(key);
        }
        return v == null ? key : v;
    }

    public List<String> rawList(String key) {
        if (lang.isList(key)) {
            return lang.getStringList(key);
        }
        if (lang.isString(key)) {
            return List.of(lang.getString(key));
        }
        if (fallback.isList(key)) {
            return fallback.getStringList(key);
        }
        if (fallback.isString(key)) {
            return List.of(fallback.getString(key));
        }
        return List.of();
    }

    private String apply(String text, String... pairs) {
        String out = text.replace("{prefix}", prefix);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out = out.replace("{" + pairs[i] + "}", MM.escapeTags(pairs[i + 1]));
        }
        return out;
    }

    public Component plain(String key, String... pairs) {
        return MM.deserialize(apply(raw(key), pairs));
    }

    public Component prefixed(String key, String... pairs) {
        return MM.deserialize(prefix + apply(raw(key), pairs));
    }

    public List<Component> lines(String key, String... pairs) {
        return rawList(key).stream().map(l -> MM.deserialize(apply(l, pairs))).toList();
    }

    public void send(CommandSender to, String key, String... pairs) {
        to.sendMessage(prefixed(key, pairs));
    }

    public void sendPlain(CommandSender to, String key, String... pairs) {
        to.sendMessage(plain(key, pairs));
    }

    public void sendLines(CommandSender to, String key, String... pairs) {
        for (Component c : lines(key, pairs)) {
            to.sendMessage(c);
        }
    }
}

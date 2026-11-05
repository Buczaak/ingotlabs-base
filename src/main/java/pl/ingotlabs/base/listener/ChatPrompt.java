package pl.ingotlabs.base.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.ingotlabs.base.IngotLabsBase;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lets a menu ask the player for one line of text in chat (custom amount, player name). */
public final class ChatPrompt implements Listener {
    private static final Set<String> CANCEL_WORDS = Set.of("cancel", "anuluj");

    private record Prompt(Consumer<String> onInput, Runnable onCancel, long expires) {
    }

    private final IngotLabsBase plugin;
    private final Map<UUID, Prompt> prompts = new ConcurrentHashMap<>();

    public ChatPrompt(IngotLabsBase plugin) {
        this.plugin = plugin;
    }

    /**
     * The next chat message of the player is swallowed and passed to {@code onInput} (main thread).
     * Typing "cancel" calls {@code onCancel}. After the configured time the prompt silently expires.
     */
    public void ask(Player player, Consumer<String> onInput, Runnable onCancel) {
        int seconds = plugin.settings().chatInputSeconds();
        Prompt prompt = new Prompt(onInput, onCancel, System.currentTimeMillis() + seconds * 1000L);
        prompts.put(player.getUniqueId(), prompt);
        plugin.lang().send(player, "prompt.hint", "seconds", String.valueOf(seconds));

        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (prompts.remove(id, prompt)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    plugin.lang().send(p, "prompt.timeout");
                }
            }
        }, seconds * 20L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Prompt prompt = prompts.get(id);
        if (prompt == null) {
            return;
        }
        if (prompt.expires() < System.currentTimeMillis()) {
            prompts.remove(id, prompt);
            return;
        }
        if (!prompts.remove(id, prompt)) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null) {
                return;
            }
            if (CANCEL_WORDS.contains(text.toLowerCase())) {
                plugin.lang().send(player, "prompt.cancelled");
                prompt.onCancel().run();
            } else {
                prompt.onInput().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        prompts.remove(event.getPlayer().getUniqueId());
    }
}

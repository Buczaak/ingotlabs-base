package pl.ingotlabs.base.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Extra command label (from the language file) that delegates to a plugin.yml command. */
public final class AliasCommand extends Command {
    private final Command target;

    public AliasCommand(String name, Command target) {
        super(name, target.getDescription(), target.getUsage(), List.of());
        this.target = target;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return target.execute(sender, label, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return target.tabComplete(sender, alias, args);
    }
}

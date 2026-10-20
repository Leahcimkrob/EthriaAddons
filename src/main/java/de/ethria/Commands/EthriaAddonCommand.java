package de.ethria.Commands;

import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class EthriaAddonCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final LanguageManager language;

    public EthriaAddonCommand(Main plugin, LanguageManager language) {
        this.plugin = plugin;
        this.language = language;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !"reload".equalsIgnoreCase(args[0])) {
            sender.sendMessage(language.message("reload.usage", "label", label));
            return true;
        }
        if (!sender.hasPermission("ethriaaddons.command.ethriaaddon.reload")
                && !sender.hasPermission("ethriaaddons.admin")) {
            sender.sendMessage(language.message("command.no-permission"));
            return true;
        }

        try {
            plugin.reloadPluginConfiguration();
            sender.sendMessage(language.message("reload.success"));
        } catch (Exception exception) {
            plugin.getLogger().warning("Could not reload configuration and language files: " + exception.getMessage());
            sender.sendMessage(language.message("reload.failed"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || (!sender.hasPermission("ethriaaddons.command.ethriaaddon.reload")
                && !sender.hasPermission("ethriaaddons.admin"))) {
            return List.of();
        }
        return "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("reload") : List.of();
    }
}

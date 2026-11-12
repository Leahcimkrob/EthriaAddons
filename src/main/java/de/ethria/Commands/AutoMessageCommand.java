package de.ethria.Commands;

import de.ethria.Commands.AutoMessageSubcommands.Create;
import de.ethria.Commands.AutoMessageSubcommands.Editor;
import de.ethria.Commands.AutoMessageSubcommands.Delete;
import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/** AutoMessage management commands. */
public final class AutoMessageCommand implements CommandExecutor, TabCompleter {
    private final Main plugin;
    private final LanguageManager language;
    private final DatabaseManager database;
    private final Create createSubcommand;
    private final Editor editor;
    private final Delete deleteSubcommand;
    private final de.ethria.Commands.AutoMessageSubcommands.Help help;
    private final de.ethria.Commands.AutoMessageSubcommands.Config configGui;

    public AutoMessageCommand(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin;
        this.database = database;
        this.language = language;
        this.createSubcommand = new Create(plugin, database, language);
        this.editor = new Editor(plugin, database, language);
        this.help = new de.ethria.Commands.AutoMessageSubcommands.Help(language);
        this.deleteSubcommand = new Delete(plugin, database, language);
        this.configGui = new de.ethria.Commands.AutoMessageSubcommands.Config(plugin, language,
                de.ethria.Config.YamlDefaults.loadAndUpdate(plugin, "Addons/AutoMesssage/automessageGUi.yml", new java.io.File(plugin.getDataFolder(), "Addons/AutoMesssage/automessageGUi.yml")));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help.execute(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("create")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.create")) {
                sender.sendMessage(language.message("automessage.no-permission"));
                return true;
            }
            createSubcommand.execute(sender, label, args);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("editor")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.editor")) sender.sendMessage(language.message("automessage.no-permission"));
            else if (sender instanceof org.bukkit.entity.Player player) editor.open(player);
            else sender.sendMessage(language.message("command.player-only"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("config")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.config")) sender.sendMessage(language.message("automessage.no-permission"));
            else if (sender instanceof org.bukkit.entity.Player player) configGui.open(player);
            else sender.sendMessage(language.message("command.player-only"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("debug")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.debug")) sender.sendMessage(language.message("automessage.no-permission"));
            else {
                long seconds = plugin.autoMessageScheduler() == null ? 0 : plugin.autoMessageScheduler().secondsUntilNextMessage();
                sender.sendMessage(language.message("automessage.debug", "server", database.getServerName(), "time", String.format("%02d:%02d", seconds / 60, seconds % 60)));
            }
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("delete")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.delete")) sender.sendMessage(language.message("automessage.no-permission"));
            else deleteSubcommand.execute(sender, args);
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("view")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.view")) {
                sender.sendMessage(language.message("automessage.no-permission"));
                return true;
            }
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
            editor.view(sender, name);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("ethriaaddons.command.automessage.reload")) {
                sender.sendMessage(language.message("automessage.no-permission"));
                return true;
            }
            plugin.reloadAutoMessageConfig();
            sender.sendMessage(language.message("automessage.reload-success"));
            return true;
        }
        help.execute(sender);
        return true;
    }

    public Editor editor() { return editor; }
    public de.ethria.Commands.AutoMessageSubcommands.Config configGui() { return configGui; }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("create", "editor", "view", "delete", "config", "reload", "debug", "help").stream()
                    .filter(value -> value.startsWith(prefix))
                    .filter(value -> !value.equals("create") || sender.hasPermission("ethriaaddons.command.automessage.create"))
                    .filter(value -> !value.equals("reload") || sender.hasPermission("ethriaaddons.command.automessage.reload"))
                    .filter(value -> !value.equals("delete") || sender.hasPermission("ethriaaddons.command.automessage.delete"))
                    .filter(value -> !value.equals("config") || sender.hasPermission("ethriaaddons.command.automessage.config"))
                    .filter(value -> !value.equals("debug") || sender.hasPermission("ethriaaddons.command.automessage.debug"))
                    .toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("view") && sender.hasPermission("ethriaaddons.command.automessage.view")) {
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            try {
                return database.getAutoMessageNames().stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            } catch (java.sql.SQLException ignored) { return List.of(); }
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("delete") && sender.hasPermission("ethriaaddons.command.automessage.delete")) {
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            try { return database.getAutoMessageNames().stream().filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList(); }
            catch (java.sql.SQLException ignored) { return List.of(); }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")
                && sender.hasPermission("ethriaaddons.command.automessage.create")) return List.of("<name>");
        return List.of();
    }
}

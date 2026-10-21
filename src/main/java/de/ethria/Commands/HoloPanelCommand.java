package de.ethria.Commands;

import de.ethria.Commands.HoloPanelSubcommands.Create;
import de.ethria.Commands.HoloPanelSubcommands.DeletePanel;
import de.ethria.Commands.HoloPanelSubcommands.Editor;
import de.ethria.Commands.HoloPanelSubcommands.Help;
import de.ethria.Commands.HoloPanelSubcommands.ReloadHolograms;
import de.ethria.Commands.HoloPanelSubcommands.RemoveTool;
import de.ethria.Commands.HoloPanelSubcommands.SetTool;
import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Routes the HoloPanel root command to its dedicated subcommand handlers. */
public final class HoloPanelCommand implements CommandExecutor, TabCompleter {
    private final Create createSubCommand;
    private final Editor editor;
    private final Help helpSubCommand;
    private final ReloadHolograms reloadSubCommand;
    private final SetTool setToolSubCommand;
    private final RemoveTool removeToolSubCommand;
    private final DeletePanel deleteSubCommand;
    private final boolean enabled;

    public HoloPanelCommand(Main plugin, DatabaseManager database, LanguageManager language, Editor editor) {
        this.createSubCommand = new Create(plugin, database, language);
        this.editor = editor;
        this.helpSubCommand = new Help(language);
        this.reloadSubCommand = new ReloadHolograms(editor, language);
        this.setToolSubCommand = new SetTool(editor, language);
        this.removeToolSubCommand = new RemoveTool(editor, language);
        this.deleteSubCommand = new DeletePanel(editor, language);
        this.enabled = plugin.getConfig().getBoolean("HoloPanel", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!enabled) {
            sender.sendMessage("§cHoloPanel ist in der Hauptconfig deaktiviert.");
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            helpSubCommand.execute(sender);
            return true;
        }
        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> {
                if (args.length == 1) reloadSubCommand.execute(sender);
                else helpSubCommand.execute(sender);
            }
            case "set", "remove", "delete" -> {
                if (args.length < 2) {
                    helpSubCommand.execute(sender);
                    break;
                }
                String panelName = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
                switch (subCommand) {
                    case "set" -> setToolSubCommand.execute(sender, panelName);
                    case "remove" -> removeToolSubCommand.execute(sender, panelName);
                    case "delete" -> deleteSubCommand.execute(sender, panelName);
                }
            }
            case "confirm", "cancel" -> {
                if (args.length == 2) deleteSubCommand.confirm(sender, args[1], "confirm".equals(subCommand));
                else helpSubCommand.execute(sender);
            }
            case "editor", "list" -> {
                if (!sender.hasPermission("ethriaaddons.command.holopanel.editor")) {
                    sender.sendMessage(createSubCommand.noPermission());
                } else if (sender instanceof org.bukkit.entity.Player player) {
                    editor.openOverview(player, 0);
                } else {
                    sender.sendMessage(createSubCommand.usage(label));
                }
            }
            case "create" -> {
                if (!sender.hasPermission("ethriaaddons.command.holopanel.create")) {
                    sender.sendMessage(createSubCommand.noPermission());
                } else {
                    createSubCommand.execute(sender, label, args);
                }
            }
            default -> helpSubCommand.execute(sender);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>();
            if ("help".startsWith(prefix)) suggestions.add("help");
            if (sender.hasPermission("ethriaaddons.command.holopanel.reload") && "reload".startsWith(prefix)) suggestions.add("reload");
            if (sender.hasPermission("ethriaaddons.command.holopanel.create") && "create".startsWith(prefix)) suggestions.add("create");
            if (sender.hasPermission("ethriaaddons.command.holopanel.editor") && "editor".startsWith(prefix)) suggestions.add("editor");
            if (sender.hasPermission("ethriaaddons.command.holopanel.set") && "set".startsWith(prefix)) suggestions.add("set");
            if (sender.hasPermission("ethriaaddons.command.holopanel.remove") && "remove".startsWith(prefix)) suggestions.add("remove");
            if (sender.hasPermission("ethriaaddons.command.holopanel.delete") && "delete".startsWith(prefix)) suggestions.add("delete");
            return suggestions;
        }
        if (args.length >= 2 && List.of("set", "remove", "delete").contains(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission("ethriaaddons.command.holopanel." + args[0].toLowerCase(Locale.ROOT))) {
            String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
            return editor.panelNames().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && "create".equalsIgnoreCase(args[0])
                && sender.hasPermission("ethriaaddons.command.holopanel.create")) return List.of("<Name>");
        return List.of();
    }

    public Editor editor() { return editor; }
}

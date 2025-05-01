package de.ethria.Commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class EventChestsCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.add("create");
            suggestions.add("delete");
            suggestions.add("list");
            suggestions.add("edit");
            suggestions.add("give");
            suggestions.add("resetPlayer");
            suggestions.add("locations");
        }
        if (args[0].equals("create") || args[0].equals("delete") || args[0].equals("edit") || args[0].equals("give")) {
            suggestions.add("<Name>");
        }
        if (args[0].equals("resetPlayer")) {
            if(args.length > 2) {
                suggestions.add("<Name>");
            } else {
                suggestions.add("<Spieler>");
            }
        }
        return suggestions;
    }
}

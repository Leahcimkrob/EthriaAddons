package de.ethria.Commands;

import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LocateBiomeCompleter implements TabCompleter {

    private String Perms;

    public LocateBiomeCompleter(String perms) {
        this.Perms = perms;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(Perms)) {
            return Arrays.stream(Biome.values())
                    .map(Enum::name)
                    .map(String::toLowerCase)
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}

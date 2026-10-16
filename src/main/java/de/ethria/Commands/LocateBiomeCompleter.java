package de.ethria.Commands;

import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LocateBiomeCompleter implements TabCompleter {

    private String Perms;

    public LocateBiomeCompleter(String perms) {
        this.Perms = perms;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission(Perms)) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> biomes = new ArrayList<>();
            for (Biome biome : Biome.values()) {
                String name = biome.name().toLowerCase(Locale.ROOT);
                if (name.startsWith(prefix)) {
                    biomes.add(name);
                }
            }
            return biomes;
        }
        return new ArrayList<>();
    }
}

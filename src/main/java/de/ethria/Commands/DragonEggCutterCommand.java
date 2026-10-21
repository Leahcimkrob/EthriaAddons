package de.ethria.Commands;

import de.ethria.Language.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DragonEggCutterCommand implements CommandExecutor, TabCompleter {
    private final List<String> materials;
    private final List<String> keys;
    private final List<String> ids;
    private final List<String> names;
    private final List<String> lore;
    private final LanguageManager language;

    public DragonEggCutterCommand(List<String> keys, List<String> materials, List<String> ids, List<String> names,
                                  List<String> lore, LanguageManager language) {
        this.keys = keys;
        this.materials = materials;
        this.ids = ids;
        this.names = names;
        this.lore = lore;
        this.language = language;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ethriaaddons.command.dragoneggcutter.give")
                && !sender.hasPermission("ethriaaddons.admin")) {
            sender.sendMessage(language.message("command.no-permission"));
            return true;
        }
        if ((args.length < 3 || args.length > 5) || !args[0].equalsIgnoreCase("give")
                || (args.length == 5 && !args[1].equalsIgnoreCase("player"))) {
            sender.sendMessage(language.message("dragoneggcutter.usage", "label", label));
            return true;
        }

        int itemIndex;
        int amountIndex = -1;
        Player target;
        if (args.length == 5) {
            target = Bukkit.getPlayerExact(args[2]);
            itemIndex = 3;
            amountIndex = 4;
        } else if (args[1].equalsIgnoreCase("player")) {
            if (args.length == 4 && Bukkit.getPlayerExact(args[2]) != null && !hasItemKey(args[2])) {
                target = Bukkit.getPlayerExact(args[2]);
                itemIndex = 3;
            } else {
                target = sender instanceof Player player ? player : null;
                itemIndex = 2;
                if (args.length == 4) amountIndex = 3;
            }
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            itemIndex = 2;
            if (args.length == 4) amountIndex = 3;
        }
        int amount = 1;
        if (amountIndex >= 0) {
            try {
                amount = Integer.parseInt(args[amountIndex]);
            } catch (NumberFormatException exception) {
                sender.sendMessage(language.message("dragoneggcutter.invalid-amount"));
                return true;
            }
        }
        if (target == null) {
            if (args[1].equalsIgnoreCase("player") && args.length < 5 && itemIndex == 2) {
                sender.sendMessage(language.message("command.player-only"));
                return true;
            }
            String playerName = args.length == 5 || (args[1].equalsIgnoreCase("player") && itemIndex == 3)
                    ? args[2] : args[1];
            sender.sendMessage(language.message("dragoneggcutter.player-not-found", "player", playerName));
            return true;
        }
        if (amount < 1) {
            sender.sendMessage(language.message("dragoneggcutter.invalid-amount"));
            return true;
        }

        int index = -1;
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).equalsIgnoreCase(args[itemIndex])) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            sender.sendMessage(language.message("dragoneggcutter.item-not-found", "item", args[itemIndex]));
            return true;
        }

        String configuredMaterial = materials.get(index).trim();
        Material material = Material.matchMaterial(configuredMaterial);
        if (material == null) {
            sender.sendMessage(language.message("dragoneggcutter.invalid-item", "item", keys.get(index),
                    "detail", "MATERIAL='" + configuredMaterial + "'"));
            return true;
        }
        int modelId;
        try {
            modelId = Integer.parseInt(ids.get(index).trim());
        } catch (NumberFormatException exception) {
            sender.sendMessage(language.message("dragoneggcutter.invalid-item", "item", keys.get(index),
                    "detail", "ID='" + ids.get(index) + "'"));
            return true;
        }
        if (modelId < 0) {
            sender.sendMessage(language.message("dragoneggcutter.invalid-item", "item", keys.get(index),
                    "detail", "ID muss mindestens 0 sein (aktuell " + modelId + ")"));
            return true;
        }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sender.sendMessage(language.message("dragoneggcutter.invalid-item", "item", keys.get(index),
                    "detail", "MATERIAL='" + configuredMaterial + "' unterstützt keine Item-Metadaten"));
            return true;
        }
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', names.get(index)));
        meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', lore.get(index))));
        meta.setCustomModelData(modelId);
        item.setItemMeta(meta);

        target.getInventory().addItem(item).values().forEach(overflow ->
                target.getWorld().dropItemNaturally(target.getLocation(), overflow));
        sender.sendMessage(language.message("dragoneggcutter.given", "amount", String.valueOf(amount),
                "item", keys.get(index), "player", target.getName()));
        return true;
    }

    private boolean hasItemKey(String candidate) {
        return keys.stream().anyMatch(key -> key.equalsIgnoreCase(candidate));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ethriaaddons.command.dragoneggcutter.give")
                && !sender.hasPermission("ethriaaddons.admin")) return List.of();
        if (args.length == 0) return List.of("give");
        if (args.length == 1) return prefix(List.of("give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> players = new ArrayList<>(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            players.add("player");
            return prefix(players, args[1]);
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 3) {
            if (!args[1].equalsIgnoreCase("player")) return prefix(keys, args[2]);
            List<String> suggestions = new ArrayList<>(keys);
            suggestions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return prefix(suggestions, args[2]);
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 4) {
            if (args[1].equalsIgnoreCase("player") && Bukkit.getPlayerExact(args[2]) != null
                    && !hasItemKey(args[2])) {
                return prefix(keys, args[3]);
            }
            return prefix(List.of("1"), args[3]);
        }
        if (args[0].equalsIgnoreCase("give") && args.length == 5 && args[1].equalsIgnoreCase("player")) {
            return prefix(List.of("1"), args[4]);
        }
        return List.of();
    }

    private List<String> prefix(List<String> values, String input) {
        String lowered = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lowered)) matches.add(value);
        return matches;
    }
}

package de.ethria.Commands;

import de.ethria.Commands.Utils.ItemStackUtils;
import de.ethria.Config.EventChests.DataRS;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class EventChests implements CommandExecutor {

    private String Prefix, ECPerm;
    private boolean ECEnabled;
    DataRS dataRS = new DataRS();

    HashMap<UUID, Boolean> EventChestEditMode;
    HashMap<UUID, String> EventChestEditModeChest;

    public EventChests(String prefix, HashMap<UUID, Boolean> eventchesteditmode, HashMap<UUID, String> eventchesteditmodechest, boolean ecenabled, String ecperm) {
        this.Prefix = prefix;
        this.EventChestEditMode = eventchesteditmode;
        this.EventChestEditModeChest = eventchesteditmodechest;

        this.ECEnabled = ecenabled;
        this.ECPerm = ecperm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if(ECEnabled) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if(player.hasPermission(ECPerm)) {
                    if (args.length == 0 || args[0].isEmpty()) {
                        if (dataRS.getChestsCount() > 0) {
                            ArrayList<String> chestData = dataRS.getChests();

                            Inventory inventory = Bukkit.createInventory(null, calculateChestSize(chestData.size()), "§9§lEventChests §8| §eListe");
                            for (int i = 0; i <= chestData.size() - 1; i++) {
                                ItemStack barrel = new ItemStack(Material.BARREL);
                                ItemMeta meta = barrel.getItemMeta();
                                if (meta != null) {
                                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', chestData.get(i)));
                                    meta.addEnchant(Enchantment.DIG_SPEED, 10, true);
                                    barrel.setItemMeta(meta);
                                }
                                inventory.addItem(barrel);
                            }
                            player.openInventory(inventory);
                            return true;
                        } else {
                            player.sendMessage(Prefix + "§cEs wurden noch keine §4EventChests §cerstellt. Du kannst welche mit §e/" + label + " create <Anzeigename> §cerstellen.");
                        }
                    } else if (args[0].equals("create") || args[0].equals("erstellen")) {
                        if (args.length >= 2 && !args[1].isEmpty()) {
                            if (dataRS.createChest(args[1])) {
                                player.sendMessage(Prefix + "§aEine neue Event-Truhe mit dem Namen §e" + ChatColor.translateAlternateColorCodes('&', args[1]) + " §awurde erstellt!");
                                return true;
                            } else {
                                player.sendMessage(Prefix + "§cEs existiert bereits eine Truhe mit diesem Anzeigenamen");
                            }
                        } else {
                            player.sendMessage(Prefix + "§eBenutze: /" + label + " create <Name>");
                        }
                    } else if (args[0].equals("delete") || args[0].equals("entfernen")) {
                        if (args.length >= 2 && !args[1].isEmpty()) {
                            player.sendMessage(Prefix + "§aLade...");
                            if (dataRS.deleteChest(args[1])) {
                                dataRS.deleteAllChestItems(args[1], player);
                                if (dataRS.deleteAllSlotChest(args[1])) {
                                    player.sendMessage(Prefix + "§aAlle §eSlot-Speicherungen §avon dieser EventChest wurden entfernt.");
                                } else {
                                    player.sendMessage(Prefix + "§cEs konnten keine §eSlot-Speicherungen §cvon dieser EventChest entfernt werden.");
                                }
                                if (dataRS.deleteAllChestLocation(args[1])) {
                                    player.sendMessage(Prefix + "§aAlle §ePosition §avon dieser EventChest wurden entfernt.");
                                } else {
                                    player.sendMessage(Prefix + "§cEs konnten keine §ePositionen §cvon dieser EventChest entfernt werden.");
                                }
                                player.sendMessage(Prefix + "§aDie Event-Truhe mit dem Namen §e" + ChatColor.translateAlternateColorCodes('&', args[1]) + " §awurde entfernt!");
                                return true;
                            } else {
                                player.sendMessage(Prefix + "§cEs existiert keine Truhe mit diesem Anzeigenamen");
                            }
                        } else {
                            player.sendMessage(Prefix + "§eBenutze: /" + label + " delete <Name>");
                        }
                    } else if (args[0].equals("list") || args[0].equals("liste")) {
                        if (dataRS.getChestsCount() > 0) {
                            ArrayList<String> chestData = dataRS.getChests();

                            player.sendMessage(Prefix + "§8-x-x-x-x-[§9§lEventChests§8]-x-x-x-x-");
                            for (int i = 0; i <= chestData.size() - 1; i++) {
                                player.sendMessage(Prefix + "§eNr. " + (i + 1) + " §7| §f" + ChatColor.translateAlternateColorCodes('&', chestData.get(i)));
                            }
                            player.sendMessage(Prefix + "§8-x-x-x-x-[§9§lEventChests§8]-x-x-x-x-");
                            return true;
                        } else {
                            player.sendMessage(Prefix + "§cEs wurden noch keine §4EventChests §cerstellt. Du kannst welche mit §e/" + label + " create <Anzeigename> §cerstellen.");
                        }
                    } else if (args[0].equals("edit") || args[0].equals("bearbeiten")) {
                        if (args.length >= 2 && !args[1].isEmpty()) {
                            if (dataRS.getChestsCount() > 0) {
                                if (dataRS.checkChestEntry(args[1])) {
                                    EventChestEditMode.put(player.getUniqueId(), true);
                                    EventChestEditModeChest.put(player.getUniqueId(), args[1]);
                                    Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', args[1]));
                                    String[][] itemsArray = dataRS.readItems(EventChestEditModeChest.get(player.getUniqueId()));
                                    for (String[] itemInfo : itemsArray) {
                                        int slot = Integer.parseInt(itemInfo[0]); // Slot number
                                        String item = itemInfo[1]; // Item
                                        if (!item.isEmpty()) {
                                            ItemStack newItemStack = ItemStackUtils.deserializeItemStack(item);
                                            inventory.setItem(slot, newItemStack);
                                        }
                                    }
                                    player.openInventory(inventory);
                                    return true;
                                } else {
                                    player.sendMessage(Prefix + "§cEs existiert keine §4EventChest §cmit dem Namen §e" + args[1] + "§c!");
                                }
                            } else {
                                player.sendMessage(Prefix + "§cEs wurden noch keine §4EventChests §cerstellt. Du kannst welche mit §e/" + label + " create <Anzeigename> §cerstellen.");
                            }
                        } else {
                            player.sendMessage(Prefix + "§eBenutze: /" + label + " edit <Name>");
                        }
                    } else if (args[0].equals("give") || args[0].equals("geben")) {
                        if (args.length >= 2 && !args[1].isEmpty()) {
                            if (dataRS.getChestsCount() > 0) {
                                if (dataRS.checkChestEntry(args[1])) {
                                    if (player.getInventory().firstEmpty() != -1) {
                                        EventChestEditModeChest.put(player.getUniqueId(), args[1]);

                                        // Create a barrel item with desired attributes
                                        ItemStack barrel = new ItemStack(Material.BARREL);
                                        ItemMeta barrelMeta = barrel.getItemMeta();
                                        if (barrelMeta != null) {
                                            barrelMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', args[1]));
                                            List<String> lore = new ArrayList<>();
                                            lore.add("§9§lDies ist ein EventChest-Item");
                                            barrelMeta.setLore(lore);
                                            barrelMeta.addEnchant(Enchantment.DIG_SPEED, 10, true);
                                            barrel.setItemMeta(barrelMeta);
                                            player.getInventory().addItem(barrel);

                                            player.sendMessage(Prefix + "§aDu hast die §eEventChest " + ChatColor.translateAlternateColorCodes('&', args[1]) + " §aerhalten.");
                                        }
                                        return true;
                                    } else {
                                        player.sendMessage(Prefix + "§cDein Inventar ist voll!");
                                    }
                                } else {
                                    player.sendMessage(Prefix + "§cEs existiert keine §4EventChest §cmit dem Namen §e" + args[1] + "§c!");
                                }
                            } else {
                                player.sendMessage(Prefix + "§cEs wurden noch keine §4EventChests §cerstellt. Du kannst welche mit §e/" + label + " create <Anzeigename> §cerstellen.");
                            }
                        } else {
                            player.sendMessage(Prefix + "§eBenutze: /" + label + " give <Name>");
                        }
                    } else if (args[0].equals("resetPlayer")) {
                        if (args.length >= 3 && !args[1].isEmpty() && !args[2].isEmpty()) {
                            if (dataRS.getChestsCount() > 0) {
                                if (dataRS.checkChestEntry(args[2])) {
                                    Player RPlayer = (Player) Bukkit.getPlayer(args[1]);
                                    if (RPlayer != null) {
                                        UUID RPlayerUUID = RPlayer.getUniqueId();
                                        if (dataRS.deleteAllSlot(args[2], RPlayerUUID)) {
                                            player.sendMessage(Prefix + "§aDer Spielerdaten für diese EventChest von §e" + args[1] + " §awurden entfernt.");
                                            return true;
                                        } else {
                                            player.sendMessage(Prefix + "§cDer Spielerdaten für diese EventChest von §e" + args[1] + " §ckonnte nicht entfernt werden. Es gibt möglicherweise keine Einträge");
                                        }
                                    } else {
                                        player.sendMessage(Prefix + "§cDer Spieler §e" + args[1] + " §ckonnte nicht gefunden werden.");
                                    }
                                } else {
                                    player.sendMessage(Prefix + "§cEs existiert keine §4EventChest §cmit dem Namen §e" + args[2] + "§c!");
                                }
                            } else {
                                player.sendMessage(Prefix + "§cEs wurden noch keine §4EventChests §cerstellt. Du kannst welche mit §e/" + label + " create <Anzeigename> §cerstellen.");
                            }
                        } else {
                            player.sendMessage(Prefix + "§eBenutze: /" + label + " resetPlayer <Spieler> <Name>");
                        }
                    } else if (args[0].equals("locations") || args[0].equals("positionen")) {
                        if (dataRS.getChestsCount() > 0) {
                            ArrayList<Location> locations = dataRS.getLocations();

                            player.sendMessage(Prefix + "§8-x-x-x-x-[§9§lPositionen§8]-x-x-x-x-");
                            for (int i = 0; i < locations.size(); i++) {
                                Location location = locations.get(i);
                                TextComponent msg = new TextComponent(Prefix + "§e" + location.getWorld().getName() + ", " + location.getX() + ", " + location.getY() + ", " + location.getZ());
                                msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e§oTeleportieren").create()));
                                msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + location.getX() + " " + location.getY() + " " + location.getZ()));
                                player.spigot().sendMessage(msg);
                            }
                            player.sendMessage(Prefix + "§8-x-x-x-x-[§9§lPositionen§8]-x-x-x-x-");
                            return true;

                        } else {
                            player.sendMessage(Prefix + "§cEs wurden noch keine §4EventChests §cerstellt. Du kannst welche mit §e/" + label + " create <Anzeigename> §cerstellen.");
                        }
                    }
                } else {
                    player.sendMessage(Prefix + "§cDu hast dazu keine Rechte!");
                }
            }
        }
        return false;
    }

    private int calculateChestSize(int numItems) {
        int baseSize = 9;
        int additionalSlots = (numItems / 10) * 10;
        int chestSize = Math.min(54, baseSize + additionalSlots);
        chestSize = (chestSize / 9) * 9;
        return chestSize;
    }
}

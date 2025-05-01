package de.ethria.Listener;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.plot.Plot;
import de.ethria.Commands.Utils.ItemStackUtils;
import de.ethria.Config.EventChests.DataRS;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import de.ethria.Commands.EventChests;

import java.util.*;

public class ClickListener implements Listener {

    private String Prefix, DependPlotSquaredWorld, ECPerm;
    private boolean DependPlotSquard, FDragonEgg, FBarrierBedrock, ECEnabled;

    private ArrayList<String> FDragonEggMaterial;
    private ArrayList<String> FDragonEggID;
    private ArrayList<String> FDragonEggName;
    private ArrayList<String> FDragonEggLore;
    private ArrayList<String> FBarrierBedrockMaterial;
    private ArrayList<String> FBarrierBedrockID;
    private ArrayList<String> FBarrierBedrockName;
    private ArrayList<String> FBarrierBedrockLore;


    private HashMap<UUID, Long> PlayerHashTime = new HashMap<>();
    HashMap<UUID, Boolean> EventChestEditMode;
    HashMap<UUID, String> EventChestEditModeChest;
    HashMap<UUID, Boolean> EventChestOpen = new HashMap<>();
    HashMap<UUID, String> EventChestOpenName = new HashMap<>();

    DataRS dataRS = new DataRS();

    public ClickListener(String prefix, boolean dependPlotSquared, String dependPlotSquaredWorlds, boolean fDragonEgg, ArrayList<String> fDragonEggMaterial, ArrayList<String> fDragonEggID, ArrayList<String> fDragonEggName, ArrayList<String> fDragonEggLore, boolean fBarrierBedrock, ArrayList<String> fBarrierBedrockMaterial, ArrayList<String> fBarrierBedrockID, ArrayList<String> fBarrierBedrockName, ArrayList<String> fBarrierBedrockLore, HashMap<UUID, Boolean> eventchesteditmode, HashMap<UUID, String> eventchesteditmodechest, boolean ecenabled, String ecperm) {
        this.Prefix = prefix;
        this.DependPlotSquard = dependPlotSquared;
        this.DependPlotSquaredWorld = dependPlotSquaredWorlds;

        // DragonEgg
        this.FDragonEgg = fDragonEgg;
        this.FDragonEggMaterial = fDragonEggMaterial;
        this.FDragonEggID = fDragonEggID;
        this.FDragonEggName = fDragonEggName;
        this.FDragonEggLore = fDragonEggLore;
        // FBarrierBedrock
        this.FBarrierBedrock = fBarrierBedrock;
        this.FBarrierBedrockMaterial = fBarrierBedrockMaterial;
        this.FBarrierBedrockID = fBarrierBedrockID;
        this.FBarrierBedrockName = fBarrierBedrockName;
        this.FBarrierBedrockLore = fBarrierBedrockLore;

        this.EventChestEditMode = eventchesteditmode;
        this.EventChestEditModeChest = eventchesteditmodechest;

        this.ECEnabled = ecenabled;
        this.ECPerm = ecperm;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = (Player) event.getPlayer();
        if(EventChestOpen.getOrDefault(player.getUniqueId(), false)) {
            EventChestOpen.put(player.getUniqueId(), false);
            EventChestOpenName.put(player.getUniqueId(), "null");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        String chestName = ChatColor.translateAlternateColorCodes('&', EventChestEditModeChest.getOrDefault(player.getUniqueId(), "none"));
        if (event.getView().getTitle().equals(chestName)) {
            if (EventChestEditMode.getOrDefault(player.getUniqueId(), false)) {
                EventChestEditMode.put(player.getUniqueId(), false);
            }
        }
        if(EventChestOpen.getOrDefault(player.getUniqueId(), false)) {
            EventChestOpen.put(player.getUniqueId(), false);
            EventChestOpenName.put(player.getUniqueId(), "null");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if ((PlayerHashTime.getOrDefault(player.getUniqueId(), 0L) + 1000) >= System.currentTimeMillis()) {
            return;
        }
        PlayerHashTime.put(player.getUniqueId(), System.currentTimeMillis());
        if (FDragonEgg && (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null)) {
            if (event.getClickedBlock().getType().equals(Material.DRAGON_EGG)) {
                ItemStack itemStack = player.getItemInHand();
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null && itemMeta.hasCustomModelData() && itemMeta.hasLore()) {
                    for (int i = 0; i <= FDragonEggName.size() - 1; i++) {
                        FDragonEggName.set(i, ChatColor.translateAlternateColorCodes('&', FDragonEggName.get(i)));
                        FDragonEggLore.set(i, ChatColor.translateAlternateColorCodes('&', FDragonEggLore.get(i)));
                    }
                    String ItemLore = "";
                    for (int i = 0; i <= itemMeta.getLore().size() - 1; i++) {
                        ItemLore = ItemLore + itemMeta.getLore().get(i);
                    }
                    if (FDragonEggName.contains(itemMeta.getDisplayName()) && FDragonEggID.contains(String.valueOf(itemMeta.getCustomModelData())) && FDragonEggMaterial.contains(itemStack.getType().toString()) && FDragonEggLore.contains(ItemLore)) {
                        if (!player.getGameMode().equals(GameMode.CREATIVE)) {
                            if (DependPlotSquard && DependPlotSquaredWorld.equals(player.getLocation().getWorld().getName())) {
                                Plot plot = BukkitUtil.adapt(player.getLocation()).getPlot();
                                if (plot != null) {
                                    UUID playerUUID = player.getUniqueId();
                                    if (plot.getOwner() != null) {
                                        if (plot.getOwner().equals(playerUUID) || plot.getTrusted().contains(playerUUID) || plot.getMembers().contains(playerUUID)) {
                                            event.setCancelled(true);
                                            player.getWorld().dropItemNaturally(event.getClickedBlock().getLocation(), new ItemStack(Material.DRAGON_EGG));
                                            event.getClickedBlock().setType(Material.AIR);
                                            DurabilityEvent(player, itemStack);
                                        }
                                    }
                                }
                            } else {
                                event.setCancelled(true);
                                player.getWorld().dropItemNaturally(event.getClickedBlock().getLocation(), new ItemStack(Material.DRAGON_EGG));
                                event.getClickedBlock().setType(Material.AIR);
                                DurabilityEvent(player, itemStack);
                            }
                        }
                    }
                }
            } else if (FBarrierBedrock && (event.getClickedBlock().getType().equals(Material.BARRIER) || event.getClickedBlock().getType().equals(Material.BEDROCK))) {
                Material TempMaterial = event.getClickedBlock().getType();
                ItemStack itemStack = player.getItemInHand();
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null && itemMeta.hasCustomModelData() && itemMeta.hasLore()) {
                    for (int i = 0; i <= FBarrierBedrockName.size() - 1; i++) {
                        FBarrierBedrockName.set(i, ChatColor.translateAlternateColorCodes('&', FBarrierBedrockName.get(i)));
                        FBarrierBedrockLore.set(i, ChatColor.translateAlternateColorCodes('&', FBarrierBedrockLore.get(i)));
                    }
                    String ItemLore = "";
                    for (int i = 0; i <= itemMeta.getLore().size() - 1; i++) {
                        ItemLore = ItemLore + itemMeta.getLore().get(i);
                    }

                    if (FBarrierBedrockName.contains(itemMeta.getDisplayName()) && FBarrierBedrockID.contains(String.valueOf(itemMeta.getCustomModelData())) && FBarrierBedrockMaterial.contains(itemStack.getType().toString()) && FBarrierBedrockLore.contains(ItemLore)) {
                        if (!player.getGameMode().equals(GameMode.CREATIVE)) {
                            if (DependPlotSquard && DependPlotSquaredWorld.equals(player.getLocation().getWorld().getName())) {
                                Plot plot = BukkitUtil.adapt(player.getLocation()).getPlot();
                                if (plot != null) {
                                    UUID playerUUID = player.getUniqueId();
                                    if (plot.getOwner() != null) {
                                        if (plot.getOwner().equals(playerUUID) || plot.getTrusted().contains(playerUUID) || plot.getMembers().contains(playerUUID)) {
                                            int EventBlockX = event.getClickedBlock().getLocation().getBlockX();
                                            int EventBlockY = event.getClickedBlock().getLocation().getBlockY();
                                            int EventBlockZ = event.getClickedBlock().getLocation().getBlockZ();
                                            if (EventBlockY != -63) {
                                                if (EventBlockY == 0) {
                                                    Location EventBlockLocation = new Location(event.getClickedBlock().getLocation().getWorld(), EventBlockX, -63, EventBlockZ);
                                                    if (EventBlockLocation.getBlock().getType().equals(Material.BEDROCK)) {
                                                        event.setCancelled(true);
                                                        if (TempMaterial.equals(Material.BARRIER)) {
                                                            player.getWorld().dropItemNaturally(event.getClickedBlock().getLocation(), new ItemStack(TempMaterial));
                                                        }
                                                        event.getClickedBlock().setType(Material.AIR);
                                                        DurabilityEvent(player, itemStack);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                event.setCancelled(true);
                                if (TempMaterial.equals(Material.BARRIER)) {
                                    player.getWorld().dropItemNaturally(event.getClickedBlock().getLocation(), new ItemStack(TempMaterial));
                                }
                                event.getClickedBlock().setType(Material.AIR);
                                DurabilityEvent(player, itemStack);
                            }
                        }
                    }
                }
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.BARREL) {
                if(dataRS.readChestLocation(clickedBlock.getLocation()) != null) {
                    event.setCancelled(true);
                    String chestName = dataRS.readChestLocation(clickedBlock.getLocation());
                    Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', chestName));
                    String[][] itemsArray = dataRS.readItems(chestName);
                    for (String[] itemInfo : itemsArray) {
                        int slot = Integer.parseInt(itemInfo[0]); // Slot number
                        if(!dataRS.readSlot(chestName, slot, player.getUniqueId())) {
                            String item = itemInfo[1]; // Item
                            if (!item.isEmpty()) {
                                ItemStack newItemStack = ItemStackUtils.deserializeItemStack(item);
                                inventory.setItem(slot, newItemStack);
                            }
                        }
                    }
                    EventChestOpen.put(player.getUniqueId(), true);
                    EventChestOpenName.put(player.getUniqueId(), chestName);
                    player.openInventory(inventory);
                }
            }
        }
    }

    public void DurabilityEvent(Player player, ItemStack itemStack) {
        if (itemStack.getType().getMaxDurability() > 0) {
            short durability = itemStack.getDurability();
            if (durability < itemStack.getType().getMaxDurability()) {
                itemStack.setDurability((short) (durability + 1));
            } else if (durability >= itemStack.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory clickedInventory = event.getClickedInventory();
        Player player = (Player) event.getWhoClicked();
        String chestName = ChatColor.translateAlternateColorCodes('&', EventChestEditModeChest.getOrDefault(player.getUniqueId(), "none"));

        if(ECEnabled) {
            if (event.getView().getTitle().equals(ChatColor.translateAlternateColorCodes('&', EventChestOpenName.getOrDefault(player.getUniqueId(), "null")))) {
                if (event.getSlot() >= 0 && event.getSlot() <= 26) {
                    if (dataRS.getChestsCount() > 0) {
                        if (dataRS.checkChestEntry(EventChestOpenName.getOrDefault(player.getUniqueId(), "null"))) {
                            dataRS.setSlot(EventChestOpenName.get(player.getUniqueId()), event.getSlot(), player.getUniqueId());
                        }
                    }
                }
            } else if (player.hasPermission(ECPerm)) {
                if (clickedInventory != null && clickedInventory.equals(event.getWhoClicked().getInventory())) {
                    if (event.getView().getTitle().startsWith(chestName)) {
                        ItemStack clickedItem = event.getCurrentItem();
                        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                            if (dataRS.getChestsCount() > 0) {
                                if (EventChestEditMode.getOrDefault(player.getUniqueId(), false)) {
                                    if (dataRS.checkChestEntry(EventChestEditModeChest.getOrDefault(player.getUniqueId(), "null"))) {
                                        event.setCancelled(true);
                                        ItemStack itemStack = event.getCurrentItem().clone();
                                        if (!itemStack.getType().equals(Material.AIR)) {
                                            player.closeInventory();
                                            String itemStackString = ItemStackUtils.serializeItemStack(itemStack);
                                            if (dataRS.createItem(EventChestEditModeChest.get(player.getUniqueId()), itemStackString)) {
                                                player.sendMessage(Prefix + "§aÄnderungen gespeichert.");
                                                openEventChest(player, chestName);
                                            } else {
                                                player.sendMessage(Prefix + "§cDie EventChest ist bereits voll. Bitte entferne ein Item, bevor du ein neues hinzufügen kannst.");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (event.getView().getTitle().startsWith(chestName)) {
                    if (event.getView().getTitle().startsWith(chestName)) {
                        ItemStack clickedItem = event.getCurrentItem();
                        if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                            if (dataRS.getChestsCount() > 0) {
                                if (EventChestEditMode.getOrDefault(player.getUniqueId(), false)) {
                                    if (dataRS.checkChestEntry(EventChestEditModeChest.getOrDefault(player.getUniqueId(), "null"))) {
                                        if (event.getSlot() >= 0 && event.getSlot() <= 26) {
                                            event.setCancelled(true);
                                            ItemStack itemStack = event.getCurrentItem().clone();
                                            if (!itemStack.getType().equals(Material.AIR)) {
                                                player.closeInventory();
                                                String itemStackString = ItemStackUtils.serializeItemStack(itemStack);
                                                if (dataRS.deleteItem(EventChestEditModeChest.get(player.getUniqueId()), itemStackString, event.getSlot())) {
                                                    player.sendMessage(Prefix + "§aÄnderungen gespeichert.");
                                                    openEventChest(player, chestName);
                                                } else {
                                                    player.sendMessage(Prefix + "§cEs ist ein Fehler aufgetreten:");
                                                    player.sendMessage(Prefix + "§cDas Item konnte nicht aus der §4EventChest §centfernt werden.");
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (event.getView().getTitle().equals("§9§lEventChests §8| §eListe")) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.BARREL) {
                if (clickedItem.getEnchantments().containsKey(Enchantment.DIG_SPEED) && clickedItem.getEnchantments().get(Enchantment.DIG_SPEED) == 10) {
                    event.setCancelled(true);
                }
            }
        }
    }

    public void openEventChest(Player player, String chestName) {
        Inventory inventory = Bukkit.createInventory(null, 27, ChatColor.translateAlternateColorCodes('&', chestName));
        String[][] itemsArray = dataRS.readItems(EventChestEditModeChest.get(player.getUniqueId()));
        for (String[] itemInfo : itemsArray) {
            int itemSlot = Integer.parseInt(itemInfo[0]); // Slot number
            String item = itemInfo[1]; // Item
            if (!item.isEmpty()) {
                ItemStack newItemStack = ItemStackUtils.deserializeItemStack(item);
                inventory.setItem(itemSlot, newItemStack);
            }
        }
        EventChestEditMode.put(player.getUniqueId(), true);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission(ECPerm) && ECEnabled) {
            if (event.getBlock().getType().equals(Material.BARREL)) {
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                if (itemInHand.getType() != Material.AIR) {
                    ItemMeta itemMeta = itemInHand.getItemMeta();
                    if (itemMeta != null) {
                        if (itemMeta.hasEnchant(Enchantment.DIG_SPEED) && itemMeta.getEnchantLevel(Enchantment.DIG_SPEED) == 10 && itemMeta.hasLore()) {
                            List<String> lore = itemMeta.getLore();
                            if (lore != null) {
                                if (lore.contains("§9§lDies ist ein EventChest-Item")) {
                                    if (dataRS.getChestsCount() > 0) {
                                        if (dataRS.checkChestEntry(EventChestEditModeChest.getOrDefault(player.getUniqueId(), "null"))) {
                                            if (dataRS.placeChest(EventChestEditModeChest.get(player.getUniqueId()), event.getBlock().getLocation())) {
                                                player.sendMessage(Prefix + "§aDie §eEventChest §awurde platziert.");
                                            } else {
                                                player.sendMessage(Prefix + "§cFehler beim Platzieren der EventChest.");
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockDestroy(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (block.getType().equals(Material.BARREL) && ECEnabled) {
            Location EventBlockLocation = event.getBlock().getLocation();
            if(dataRS.readChestLocation(EventBlockLocation) != null) {
                event.setCancelled(true);
                if (player.hasPermission(ECPerm)) {
                    if(dataRS.deleteChestLocation(EventBlockLocation)) {
                        event.getBlock().setType(Material.AIR);
                        player.sendMessage(Prefix + "§aDie §eEventChest §awurde entfernt.");
                    } else {
                        player.sendMessage(Prefix + "§cDie §4EventChest §ckonnte nicht entfernt werden. Weitere Details in der Konsole!");
                    }
                }
            }
        }
    }
}


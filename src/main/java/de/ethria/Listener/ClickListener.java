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
                    if (FDragonEggName.contains(itemMeta.getDisplayName()) && FDragonEggID.contains(String.valueOf(itemMeta.getCustomModelData())) && FDragonEggMaterial.contains(itemStack.getType().toString())) {
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
            }
        }
        // Weitere Logik für andere Interaktionen
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
        // Logik für das Klicken in Inventaren
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Logik für das Platzieren von Blöcken
    }

    @EventHandler
    public void onBlockDestroy(BlockBreakEvent event) {
        // Logik für das Zerstören von Blöcken
    }
}

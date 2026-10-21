package de.ethria.Listener;

import com.plotsquared.bukkit.util.BukkitUtil;
import com.plotsquared.core.plot.Plot;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ClickListener implements Listener {

    private String DependPlotSquaredWorld;
    private boolean DependPlotSquard, FDragonEgg;

    private ArrayList<String> FDragonEggMaterial;
    private ArrayList<String> FDragonEggID;
    private ArrayList<String> FDragonEggName;
    private ArrayList<String> FDragonEggLore;


    private HashMap<UUID, Long> PlayerHashTime = new HashMap<>();
    public ClickListener(boolean dependPlotSquared, String dependPlotSquaredWorlds, boolean fDragonEgg,
                         ArrayList<String> fDragonEggMaterial, ArrayList<String> fDragonEggID,
                         ArrayList<String> fDragonEggName, ArrayList<String> fDragonEggLore) {
        this.DependPlotSquard = dependPlotSquared;
        this.DependPlotSquaredWorld = dependPlotSquaredWorlds;

        // DragonEgg
        this.FDragonEgg = fDragonEgg;
        this.FDragonEggMaterial = fDragonEggMaterial;
        this.FDragonEggID = fDragonEggID;
        this.FDragonEggName = fDragonEggName;
        this.FDragonEggLore = fDragonEggLore;
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
                ItemStack itemStack = player.getInventory().getItemInMainHand();
                ItemMeta itemMeta = itemStack.getItemMeta();
                if (itemMeta != null && itemMeta.hasCustomModelData() && itemMeta.hasLore()) {
                    if (matchesConfiguredTool(itemMeta)) {
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

    private boolean matchesConfiguredTool(ItemMeta itemMeta) {
        List<String> itemLore = itemMeta.getLore();
        if (itemLore == null) return false;
        int configCount = Math.min(FDragonEggID.size(), FDragonEggLore.size());
        for (int i = 0; i < configCount; i++) {
            try {
                int configuredId = Integer.parseInt(FDragonEggID.get(i).trim());
                String configuredLore = ChatColor.translateAlternateColorCodes('&', FDragonEggLore.get(i));
                if (itemMeta.getCustomModelData() == configuredId && itemLore.contains(configuredLore)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // Ignore invalid model IDs in the configuration.
            }
        }
        return false;
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

}

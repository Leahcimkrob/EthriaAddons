package de.ethria.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import de.ethria.Language.LanguageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class LocateBiome implements CommandExecutor {

    private int LBTimeout;
    private boolean LBEnabled;
    private double LBMoney;
    private final JavaPlugin plugin;
    private final LanguageManager language;
    private final HashMap<UUID, Long> PlayerTimerLB = new HashMap<UUID, Long>();

    public LocateBiome(JavaPlugin plugin, boolean lbenabled, double lbmoney, int lbtimeout, LanguageManager language) {
        this.plugin = plugin;
        this.LBEnabled = lbenabled;
        this.LBMoney = lbmoney;
        this.LBTimeout = lbtimeout;
        this.language = language;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            if(LBEnabled) {
                Player player = (Player) sender;
                if (player.hasPermission("ethriaaddons.command.locatebiome.use")) {
                    if (args.length == 1) {

                        Biome targetBiome;

                        try {
                            targetBiome = Biome.valueOf(args[0].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            player.sendMessage(language.message("locatebiome.invalid-biome"));
                            return false;
                        }

                        player.sendMessage(language.message("locatebiome.processing"));

                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            Location nearestBiome = findNearestBiome(player.getWorld(), player.getLocation(), targetBiome);

                            if (nearestBiome != null) {
                                Economy economy;
                                RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
                                assert rsp != null;
                                economy = rsp.getProvider();
                                double currentBalance = economy.getBalance(player);
                                if (hasTimeoutPassed(player.getUniqueId())) {
                                    if (currentBalance >= LBMoney && economy.withdrawPlayer(player, LBMoney).transactionSuccess()) {
                                        Location highestLocation = getHighestNonLiquidBlock(nearestBiome);

                                        player.sendMessage(language.message("locatebiome.found", "biome", targetBiome.getKey().getKey()));

                        Component message = language.message("locatebiome.coordinates", "x", String.valueOf(highestLocation.getBlockX()), "y", String.valueOf(highestLocation.getBlockY()), "z", String.valueOf(highestLocation.getBlockZ()))
                                .hoverEvent(HoverEvent.showText(language.message("locatebiome.teleport-hover")))
                                .clickEvent(ClickEvent.runCommand("/teleport "
                                        + highestLocation.getBlockX() + " "
                                        + highestLocation.getBlockY() + " "
                                        + highestLocation.getBlockZ()));

                        player.sendMessage(message);
                                        player.sendMessage(language.message("locatebiome.money-charged", "amount", String.valueOf(LBMoney)));
                                        PlayerTimerLB.put(player.getUniqueId(), System.currentTimeMillis());
                                    } else {
                                        player.sendMessage(language.message("locatebiome.not-enough-money", "amount", String.valueOf(LBMoney)));
                                    }
                                } else {
                                    player.sendMessage(language.message("locatebiome.cooldown", "time", getRemainingTime(player.getUniqueId())));
                                }
                            } else {
                                player.sendMessage(language.message("locatebiome.not-found"));
                            }
                        });
                        return true;
                    } else {
                        player.sendMessage(language.message("locatebiome.usage", "label", label));
                    }
                } else {
                    player.sendMessage(language.message("command.no-permission"));
                }
            } else {
                sender.sendMessage(language.message("locatebiome.disabled"));
            }
        } else {
            sender.sendMessage(language.message("command.player-only"));
        }
        return false;
    }

    private Location findNearestBiome(World world, Location startLocation, Biome targetBiome) {
        int radius = 10000;
        for (int x = -radius; x <= radius; x += 16) {
            for (int z = -radius; z <= radius; z += 16) {
                Location location = new Location(world, startLocation.getX() + x, 0, startLocation.getZ() + z);
                Biome biome = world.getBiome(location.getBlockX(), location.getBlockY(), location.getBlockZ());
                if (biome == targetBiome) {
                    return location;
                }
            }
        }
        return null;
    }

    private Location getHighestNonLiquidBlock(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX();
        int z = location.getBlockZ();

        int y = world.getHighestBlockYAt(x, z);

        while (y > 0) {
            Location blockLocation = new Location(world, x, y, z);
            Material blockType = blockLocation.getBlock().getType();
            if (!blockType.equals(Material.LAVA) && !blockType.equals(Material.WATER) && blockType.isSolid()) {
                return blockLocation.add(0, 1, 0);
            }
            y--;
        }

        return new Location(world, x, world.getHighestBlockYAt(x, z), z);
    }

    public boolean hasTimeoutPassed(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        long lastUsageTime = PlayerTimerLB.getOrDefault(playerId, 0L);

        long timeoutMillis = TimeUnit.MINUTES.toMillis(LBTimeout);
        return (currentTime - lastUsageTime) >= timeoutMillis;
    }

    public String getRemainingTime(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        long lastUsageTime = PlayerTimerLB.getOrDefault(playerId, 0L);

        long timeoutMillis = TimeUnit.MINUTES.toMillis(LBTimeout);
        long timeRemaining = timeoutMillis - (currentTime - lastUsageTime);

        if (timeRemaining <= 0) {
            return null;
        }

        long hours = TimeUnit.MILLISECONDS.toHours(timeRemaining);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) - TimeUnit.HOURS.toMinutes(hours);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemaining) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(timeRemaining));

        return String.format("%dh %dm %ds", hours, minutes, seconds);
    }
}

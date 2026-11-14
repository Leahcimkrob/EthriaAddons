package de.ethria.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.ClickCallback;
import de.ethria.Language.LanguageManager;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
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


public final class LocateBiomeCommand implements CommandExecutor {

    private int LBTimeout;
    private boolean LBEnabled;
    private double LBMoney;
    private final JavaPlugin plugin;
    private final LanguageManager language;
    private final HashMap<UUID, Long> PlayerTimerLB = new HashMap<UUID, Long>();

    public LocateBiomeCommand(JavaPlugin plugin, boolean lbenabled, double lbmoney, int lbtimeout, LanguageManager language) {
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

                        Biome targetBiome = resolveBiome(args[0]);
                        if (targetBiome == null) {
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
                                        if (highestLocation == null) {
                                            player.sendMessage(language.message("locatebiome.not-found"));
                                            return;
                                        }

                                        player.sendMessage(language.message("locatebiome.found", "biome", targetBiome.getKey().getKey()));

                        Component message = language.message("locatebiome.coordinates", "x", String.valueOf(highestLocation.getBlockX()), "y", String.valueOf(highestLocation.getBlockY()), "z", String.valueOf(highestLocation.getBlockZ()))
                                .hoverEvent(HoverEvent.showText(language.message("locatebiome.teleport-hover")))
                                .clickEvent(ClickEvent.callback(audience -> {
                                    if (audience instanceof Player) {
                                        Player clickingPlayer = (Player) audience;
                                                Location safeLocation = getHighestNonLiquidBlock(nearestBiome);
                                                if (safeLocation != null) {
                                                    clickingPlayer.teleportAsync(safeLocation);
                                                }
                                    }
                                }));

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

    private Biome resolveBiome(String input) {
        NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(java.util.Locale.ROOT));
        if (key != null) {
            Biome biome = Registry.BIOME.get(key);
            if (biome != null) {
                return biome;
            }
        }

        // Keep compatibility with input such as "DESERT" as well as "minecraft:desert".
        return Registry.BIOME.get(NamespacedKey.minecraft(input.toLowerCase(java.util.Locale.ROOT)));
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
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 2;
        if (world.getEnvironment() == World.Environment.NETHER) {
            // Keep feet and head below the Nether roof (normally the roof begins at Y=128).
            maxY = Math.min(maxY, 123);
        }

        int startY = Math.min(world.getHighestBlockYAt(x, z), maxY);
        for (int y = startY; y >= minY; y--) {
            Material floor = world.getBlockAt(x, y, z).getType();
            Material feet = world.getBlockAt(x, y + 1, z).getType();
            Material head = world.getBlockAt(x, y + 2, z).getType();
            if (floor.isSolid() && floor != Material.LAVA && floor != Material.WATER
                    && feet.isAir() && head.isAir()) {
                return new Location(world, x + 0.5, y + 1, z + 0.5);
            }
        }

        // Do not teleport into an unsafe position if no clear two-block space exists here.
        return null;
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

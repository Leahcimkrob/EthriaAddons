package de.ethria.Commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
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

import static de.ethria.main.Main.Prefix;

public class LocateBiome implements CommandExecutor {

    private String Perm;
    private int LBTimeout;
    private boolean LBEnabled;
    private double LBMoney;
    private final JavaPlugin plugin;
    private final HashMap<UUID, Long> PlayerTimerLB = new HashMap<UUID, Long>();

    public LocateBiome(JavaPlugin plugin, boolean lbenabled, String perm, double lbmoney, int lbtimeout) {
        this.plugin = plugin;
        this.LBEnabled = lbenabled;
        this.Perm = perm;
        this.LBMoney = lbmoney;
        this.LBTimeout = lbtimeout;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (sender instanceof Player) {
            if(LBEnabled) {
                Player player = (Player) sender;
                if (player.hasPermission(Perm)) {
                    if (args.length == 1) {

                        Biome targetBiome;

                        try {
                            targetBiome = Biome.valueOf(args[0].toUpperCase());
                        } catch (IllegalArgumentException e) {
                            player.sendMessage(Prefix + "§cDieses Biome existiert nicht!");
                            return false;
                        }

                        player.sendMessage(Prefix + "§eVerarbeite...");

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

                                        player.sendMessage(Prefix + "§eNächstes " + targetBiome.name() + " Biome ist bei:");

                                        TextComponent message = new TextComponent(TextComponent.fromLegacyText(Prefix + "§c"
                                                + highestLocation.getBlockX() + ", "
                                                + highestLocation.getBlockY() + ", "
                                                + highestLocation.getBlockZ()));
                                        HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                                                new TextComponent("§e§oTeleportieren")
                                        });
                                        message.setHoverEvent(hover);
                                        message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/teleport "
                                                + highestLocation.getBlockX() + " "
                                                + highestLocation.getBlockY() + " "
                                                + highestLocation.getBlockZ()));

                                        player.spigot().sendMessage(message);
                                        player.sendMessage(Prefix + "§eDir wurden §a" + LBMoney + "€ §eabgezogen.");
                                        PlayerTimerLB.put(player.getUniqueId(), System.currentTimeMillis());
                                    } else {
                                        player.sendMessage(Prefix + "§cDazu hast du nicht ausreichend Geld. Du benötigtst §e" + LBMoney + "€");
                                    }
                                } else {
                                    player.sendMessage(getRemainingTime(player.getUniqueId()));
                                }
                            } else {
                                player.sendMessage(Prefix + "§cDieses Biome wurde nicht gefunden!");
                            }
                        });
                        return true;
                    } else {
                        player.sendMessage(Prefix + "§eBenutze: /locatebiome <Biome>");
                    }
                } else {
                    player.sendMessage(Prefix + "§cDazu hast du keine Rechte!");
                }
            } else {
                sender.sendMessage(Prefix + "§cDieser Befehl ist momentan deaktiviert.");
            }
        } else {
            sender.sendMessage(Prefix + "§cDu musst ein Spieler sein, um diesen Befehl nutzen zu können");
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

        return String.format(Prefix + "§cDu musst noch §e%dh %dm %ds §cwarten, bevor du den Befehl erneut nutzen kannst.", hours, minutes, seconds);
    }
}

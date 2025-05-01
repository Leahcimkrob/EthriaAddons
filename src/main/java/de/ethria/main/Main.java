package de.ethria.main;

import de.ethria.Commands.EventChests;
import de.ethria.Commands.EventChestsCompleter;
import de.ethria.Commands.LocateBiome;
import de.ethria.Commands.LocateBiomeCompleter;
import de.ethria.Config.BarrierBedrock;
import de.ethria.Config.Config;
import de.ethria.Config.DragonEgg;
import de.ethria.Config.EventChests.EventChestsConf;
import de.ethria.Config.LocateBiomeConf;
import de.ethria.Listener.ClickListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public final class Main extends JavaPlugin {

    public static String Prefix, LBPerm;
    private int LBTimeout;
    private double LBMoney;
    private String DependPlotSquaredWorld, ECPerm;
    private boolean DependPlotSquared, FDragonEgg, FBarrierBedrock, ECEnabled, LBEnabled;

    private ArrayList<String> FDragonEggMaterial = new ArrayList<>();
    private ArrayList<String> FDragonEggID = new ArrayList<>();
    private ArrayList<String> FDragonEggName = new ArrayList<>();
    private ArrayList<String> FDragonEggLore = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockMaterial = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockID = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockName = new ArrayList<>();
    private ArrayList<String> FBarrierBedrockLore = new ArrayList<>();

    HashMap<UUID, Boolean> EventChestEditMode = new HashMap<>();
    HashMap<UUID, String> EventChestEditModeChest = new HashMap<>();

    @Override
    public void onEnable() {
        // Config laden
        Config cc = new Config();
        cc.createFolder();
        cc.createYML();
        Prefix = cc.ReadYML("Prefix");
        FDragonEgg = Boolean.parseBoolean(cc.ReadYML("DragonEgg"));
        FBarrierBedrock = Boolean.parseBoolean(cc.ReadYML("BarrierBedrock"));
        DependPlotSquared = Boolean.parseBoolean(cc.ReadYML("PlotSquaredCheck"));
        DependPlotSquaredWorld = cc.ReadYML("PlotSquaredWorld");

        // DragonEgg-Settings laden
        DragonEgg dragonegg = new DragonEgg(FDragonEggMaterial, FDragonEggID, FDragonEggName, FDragonEggLore);
        dragonegg.createFolder();
        dragonegg.createConfig();
        dragonegg.readConfig();
        // BarrierBedrock-Settings laden
        BarrierBedrock barrierbedrock = new BarrierBedrock(FBarrierBedrockMaterial, FBarrierBedrockID, FBarrierBedrockName, FBarrierBedrockLore);
        barrierbedrock.createFolder();
        barrierbedrock.createConfig();
        barrierbedrock.readConfig();
        // EventChests-Settings laden
        EventChestsConf ecc = new EventChestsConf();
        ECEnabled = Boolean.parseBoolean(ecc.ReadYML("Enabled"));
        ECPerm = ecc.ReadYML("Perm");
        // LocateBiome-Settings laden
        LocateBiomeConf lbc = new LocateBiomeConf();
        LBEnabled = Boolean.parseBoolean(lbc.ReadYML("Enabled"));
        LBPerm = lbc.ReadYML("Perm");
        LBMoney = Double.parseDouble(lbc.ReadYML("Money"));
        LBTimeout = Integer.parseInt(lbc.ReadYML("Timeout"));

        DependPlotSquared = checkRequirements();
        getServer().getPluginManager().registerEvents(new ClickListener(Prefix, DependPlotSquared, DependPlotSquaredWorld, FDragonEgg, FDragonEggMaterial, FDragonEggID, FDragonEggName, FDragonEggLore, FBarrierBedrock, FBarrierBedrockMaterial, FBarrierBedrockID, FBarrierBedrockName, FBarrierBedrockLore, EventChestEditMode, EventChestEditModeChest, ECEnabled, ECPerm), this);
        getCommand("eventchests").setExecutor(new EventChests(Prefix, EventChestEditMode, EventChestEditModeChest, ECEnabled, ECPerm));
        getCommand("eventchests").setTabCompleter(new EventChestsCompleter());
        getCommand("locatebiome").setExecutor(new LocateBiome(this, LBEnabled, LBPerm, LBMoney, LBTimeout));
        getCommand("locatebiome").setTabCompleter(new LocateBiomeCompleter(LBPerm));
        Bukkit.getConsoleSender().sendMessage(Prefix + "§a§lEthriaAddons aktiviert! §2(v" + this.getDescription().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(Prefix + "§c§lEthriaAddons deaktiviert! §4(v" + this.getDescription().getVersion() + ")");
    }

    private boolean checkRequirements() {
        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            if (plugin.getName().startsWith("PlotSquared")) {
                return true;
            }
        }
        return false;
    }
}

package de.ethria.main;

import de.ethria.Commands.EthriaAddonCommand;
import de.ethria.Commands.HoloPanelSubcommands.Editor;
import de.ethria.HoloPanel.HoloPanelEngine;
import de.ethria.Commands.LocateBiome;
import de.ethria.Commands.LocateBiomeCompleter;
import de.ethria.Commands.HoloPanel;
import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.Config.DragonEgg;
import de.ethria.Config.LocateBiomeConf;
import de.ethria.Listener.ClickListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.sql.SQLException;
import java.util.logging.Level;

public final class Main extends JavaPlugin {

    private static final Set<String> ALIAS_TARGETS = Set.of("ethriaaddon", "holopanel", "locatebiome");
    private final Map<String, Command> registeredAliases = new LinkedHashMap<>();

    public static String Prefix;
    public static NamespacedKey HOLOPANEL_ID_KEY;
    private DatabaseManager databaseManager;
    private LanguageManager languageManager;
    private Editor holoPanelEditor;
    private int LBTimeout;
    private double LBMoney;
    private String DependPlotSquaredWorld;
    private boolean DependPlotSquared, FDragonEgg, LBEnabled, HoloPanelEnabled;

    private ArrayList<String> FDragonEggMaterial = new ArrayList<>();
    private ArrayList<String> FDragonEggID = new ArrayList<>();
    private ArrayList<String> FDragonEggName = new ArrayList<>();
    private ArrayList<String> FDragonEggLore = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        HOLOPANEL_ID_KEY = new NamespacedKey(this, "holopanel_id");
        languageManager = new LanguageManager(this);
        languageManager.load();
        getCommand("ethriaaddon").setExecutor(new EthriaAddonCommand(this, languageManager));
        getCommand("ethriaaddon").setTabCompleter((org.bukkit.command.TabCompleter) getCommand("ethriaaddon").getExecutor());
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
        } catch (SQLException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE, "Could not initialize the HoloPanel database.", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("Prefix", "&9&lEthria &8≫ "));
        FDragonEgg = getConfig().getBoolean("DragonEgg", true);
        LBEnabled = getConfig().getBoolean("LocateBiome", true);
        HoloPanelEnabled = getConfig().getBoolean("HoloPanel", true);
        DependPlotSquared = getConfig().getBoolean("PlotSquaredCheck", true);
        DependPlotSquaredWorld = getConfig().getString("PlotSquaredWorld", "world");

        // DragonEgg-Settings laden
        DragonEgg dragonegg = new DragonEgg(this, FDragonEggMaterial, FDragonEggID, FDragonEggName, FDragonEggLore);
        dragonegg.createConfig();
        dragonegg.readConfig();
        // LocateBiome-Settings laden
        LocateBiomeConf lbc = new LocateBiomeConf(this);
        LBEnabled = LBEnabled && Boolean.parseBoolean(lbc.ReadYML("Enabled"));
        LBMoney = Double.parseDouble(lbc.ReadYML("Money"));
        LBTimeout = Integer.parseInt(lbc.ReadYML("Timeout"));

        DependPlotSquared = DependPlotSquared && checkRequirements();
        getServer().getPluginManager().registerEvents(new ClickListener(DependPlotSquared, DependPlotSquaredWorld, FDragonEgg, FDragonEggMaterial, FDragonEggID, FDragonEggName, FDragonEggLore), this);
        getCommand("locatebiome").setExecutor(new LocateBiome(this, LBEnabled, LBMoney, LBTimeout, languageManager));
        getCommand("locatebiome").setTabCompleter(new LocateBiomeCompleter());
        HoloPanel holoPanelCommand;
        if (HoloPanelEnabled) {
            HoloPanelEngine holoPanelEngine = new HoloPanelEngine(this, databaseManager);
            try {
                holoPanelEngine.restoreAll();
            } catch (SQLException exception) {
                getLogger().log(Level.WARNING, "Could not restore saved HoloPanels.", exception);
            }
            holoPanelEditor = new Editor(this, databaseManager, languageManager, holoPanelEngine);
            getServer().getPluginManager().registerEvents(holoPanelEditor, this);
            holoPanelCommand = new HoloPanel(this, databaseManager, languageManager, holoPanelEditor);
        } else {
            holoPanelCommand = new HoloPanel(this, databaseManager, languageManager, null);
        }
        getCommand("holopanel").setExecutor(holoPanelCommand);
        getCommand("holopanel").setTabCompleter(holoPanelCommand);
        registerCommandAliases();
        Bukkit.getConsoleSender().sendMessage(Prefix + "§a§lEthriaAddons aktiviert! §2(v" + this.getDescription().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(Prefix + "§c§lEthriaAddons deaktiviert! §4(v" + this.getDescription().getVersion() + ")");
    }

    public void reloadPluginConfiguration() {
        org.bukkit.configuration.file.FileConfiguration previousConfig = getConfig();
        String previousPrefix = Prefix;
        reloadConfig();
        try {
            getConfig().options().copyDefaults(true);
            saveConfig();
            languageManager.reload();
            new DragonEgg(this, FDragonEggMaterial, FDragonEggID, FDragonEggName, FDragonEggLore).readConfig();
            new LocateBiomeConf(this);
            if (databaseManager != null) databaseManager.reloadConfigDefaults();
            if (holoPanelEditor != null) holoPanelEditor.reloadGuiConfig();
            Prefix = ChatColor.translateAlternateColorCodes('&', getConfig().getString("Prefix", "&9&lEthria &8≫ "));
        } catch (RuntimeException exception) {
            getConfig().set("Prefix", previousConfig.getString("Prefix", "&9&lEthria &8≫ "));
            Prefix = previousPrefix;
            throw exception;
        }
        registerCommandAliases();
    }

    private void registerCommandAliases() {
        for (Command alias : registeredAliases.values()) {
            getServer().getCommandMap().getKnownCommands().values().removeIf(registered -> registered == alias);
        }
        registeredAliases.clear();

        if (getConfig().getConfigurationSection("command_aliases") == null) {
            return;
        }

        getConfig().getConfigurationSection("command_aliases").getValues(false).forEach((configuredAlias, configuredTarget) -> {
            if (!(configuredTarget instanceof String targetValue)) {
                getLogger().warning("Ignoring command alias '" + configuredAlias + "': target must be a string.");
                return;
            }
            String alias = configuredAlias.toLowerCase(Locale.ROOT).trim();
            String[] targetParts = targetValue.trim().split("\\s+");
            if (targetParts.length == 0 || targetParts[0].isBlank()) {
                getLogger().warning("Ignoring command alias '" + configuredAlias + "': target is empty.");
                return;
            }
            String rootCommand = targetParts[0].toLowerCase(Locale.ROOT);
            if (!alias.matches("[a-z0-9_-]+") || ALIAS_TARGETS.contains(alias) || !ALIAS_TARGETS.contains(rootCommand)) {
                getLogger().warning("Ignoring invalid or conflicting command alias '" + configuredAlias + "'.");
                return;
            }

            Command target = getCommand(rootCommand);
            if (target == null) {
                getLogger().warning("Ignoring command alias '" + alias + "': command '" + rootCommand + "' is not registered.");
                return;
            }
            String[] fixedArgs = java.util.Arrays.copyOfRange(targetParts, 1, targetParts.length);
            Command aliasCommand = new Command(alias) {
                @Override
                public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                    String[] forwarded = new String[fixedArgs.length + args.length];
                    System.arraycopy(fixedArgs, 0, forwarded, 0, fixedArgs.length);
                    System.arraycopy(args, 0, forwarded, fixedArgs.length, args.length);
                    return target.execute(sender, commandLabel, forwarded);
                }

                @Override
                public java.util.List<String> tabComplete(CommandSender sender, String commandLabel, String[] args) {
                    String[] forwarded = new String[fixedArgs.length + args.length];
                    System.arraycopy(fixedArgs, 0, forwarded, 0, fixedArgs.length);
                    System.arraycopy(args, 0, forwarded, fixedArgs.length, args.length);
                    java.util.List<String> completions = target.tabComplete(sender, commandLabel, forwarded);
                    return completions == null ? java.util.List.of() : completions;
                }
            };
            aliasCommand.setDescription("Alias for /" + targetValue);
            aliasCommand.setUsage("/" + alias + (fixedArgs.length == 0 ? "" : " " + String.join(" ", fixedArgs)));
            if (!getServer().getCommandMap().getKnownCommands().containsKey(alias)
                    && getServer().getCommandMap().register("ethriaaddons", aliasCommand)) {
                registeredAliases.put(alias, aliasCommand);
            } else {
                getLogger().warning("Could not register command alias '" + alias + "': name is already in use.");
            }
        });
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

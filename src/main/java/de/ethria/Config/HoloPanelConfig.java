package de.ethria.Config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

/** Loads and migrates the HoloPanel database and GUI configuration files. */
public final class HoloPanelConfig {
    private static final String DATABASE_RESOURCE = "Addons/HoloPanel/holopanel.yml";
    private static final String GUI_RESOURCE = "Addons/HoloPanel/HolopanelGUi.yml";
    private static final String[] MIGRATED_SETTINGS = {
            "database.type", "database.sqlite.file", "database.mysql.host", "database.mysql.port",
            "database.mysql.database", "database.mysql.username", "database.mysql.password", "database.mysql.useSSL"
    };

    private final JavaPlugin plugin;

    public HoloPanelConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public YamlConfiguration loadDatabaseConfig() {
        File file = new File(plugin.getDataFolder(), DATABASE_RESOURCE);
        boolean created = !file.exists();
        YamlConfiguration config = YamlDefaults.loadAndUpdate(plugin, DATABASE_RESOURCE, file);
        FileConfiguration pluginConfig = plugin.getConfig();
        boolean migrated = false;
        for (String path : MIGRATED_SETTINGS) {
            if (pluginConfig.isSet(path) && (created || !config.isSet(path))) {
                config.set(path, pluginConfig.get(path));
                migrated = true;
            }
        }
        boolean migrationSaved = true;
        if (migrated) {
            try {
                config.save(file);
            } catch (IOException exception) {
                migrationSaved = false;
                plugin.getLogger().warning("Could not migrate HoloPanel database settings: " + exception.getMessage());
            }
        }
        if (migrationSaved && pluginConfig.isConfigurationSection("database")) {
            pluginConfig.set("database", null);
            plugin.saveConfig();
        }
        return config;
    }

    public YamlConfiguration loadGuiConfig() {
        File file = new File(plugin.getDataFolder(), GUI_RESOURCE);
        boolean created = !file.exists();
        YamlConfiguration gui = YamlDefaults.loadAndUpdate(plugin, GUI_RESOURCE, file);
        if (created) migrateLegacyGuiSettings(gui, file);
        if (migrateGuiLayout(gui)) save(gui, file, "GUI layout");
        return gui;
    }

    private boolean migrateGuiLayout(YamlConfiguration gui) {
        boolean migrated = copyGuiValues(gui, "size.rows", "overview.rows");
        if (gui.getInt("button.back.slot", -1) == 48) {
            gui.set("button.back.slot", 49);
            migrated = true;
        }
        migrated |= migrateGuiButton(gui, "overview.previous", "button.previous");
        migrated |= migrateGuiButton(gui, "overview.back", "button.back");
        migrated |= migrateGuiButton(gui, "overview.close", "button.close");
        migrated |= migrateGuiButton(gui, "overview.next-page", "button.next-page");
        migrated |= migrateGuiButton(gui, "overview.next", "button.next-page");
        for (String button : List.of("item", "edit", "delete", "tools")) {
            migrated |= migrateGuiButton(gui, "detail." + button, "detail.button." + button);
        }
        for (String path : List.of("detail.back", "detail.close", "detail.button.back", "detail.button.close")) {
            if (gui.contains(path)) {
                gui.set(path, null);
                migrated = true;
            }
        }
        if (migrated) plugin.getLogger().info("Migrated HoloPanel GUI settings to the current button layout.");
        return migrated;
    }

    private boolean migrateGuiButton(YamlConfiguration gui, String oldPath, String newPath) {
        boolean migrated = copyGuiValues(gui, oldPath, newPath);
        if (migrated) gui.set(oldPath, null);
        return migrated;
    }

    private boolean copyGuiValues(YamlConfiguration gui, String oldPath, String newPath) {
        if (!gui.contains(oldPath)) return false;
        ConfigurationSection oldSection = gui.getConfigurationSection(oldPath);
        if (oldSection == null) {
            if (!gui.contains(newPath)) gui.set(newPath, gui.get(oldPath));
        } else {
            for (String key : oldSection.getKeys(true)) {
                String oldValuePath = oldPath + "." + key;
                if (gui.isConfigurationSection(oldValuePath)) continue;
                String newValuePath = newPath + "." + key;
                if (!gui.contains(newValuePath)) gui.set(newValuePath, gui.get(oldValuePath));
            }
        }
        return true;
    }

    private void migrateLegacyGuiSettings(YamlConfiguration gui, File guiFile) {
        File legacyFile = new File(plugin.getDataFolder(), DATABASE_RESOURCE);
        if (!legacyFile.isFile()) return;
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection section = legacy.getConfigurationSection("gui");
        if (section == null) return;

        for (String path : section.getKeys(true)) {
            String fullPath = "gui." + path;
            if (legacy.isConfigurationSection(fullPath)) continue;
            gui.set(path, legacy.get(fullPath));
        }
        try {
            gui.save(guiFile);
            legacy.set("gui", null);
            legacy.save(legacyFile);
            plugin.getLogger().info("Migrated HoloPanel GUI settings to HolopanelGUi.yml.");
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not migrate legacy HoloPanel GUI settings: " + exception.getMessage());
        }
    }

    private void save(YamlConfiguration config, File file, String description) {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save migrated HoloPanel " + description + ": " + exception.getMessage());
        }
    }
}

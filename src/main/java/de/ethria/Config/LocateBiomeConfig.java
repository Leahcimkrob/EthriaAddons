package de.ethria.Config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/** Loads the LocateBiome add-on settings from its runtime YAML file. */
public final class LocateBiomeConfig {
    private static final String RESOURCE = "Addons/locatebiome.yml";

    private final YamlConfiguration config;

    public LocateBiomeConfig(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        this.config = YamlDefaults.loadAndUpdate(plugin, RESOURCE, file);
    }

    public boolean isEnabled() {
        return config.getBoolean("Enabled", true);
    }

    public double getMoney() {
        return config.getDouble("Money", 125000.0);
    }

    public int getTimeoutMinutes() {
        return config.getInt("Timeout", 180);
    }
}

package de.ethria.Config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class LocateBiomeConf {
    private static final String RESOURCE = "Addons/locatebiome.yml";
    private final JavaPlugin plugin;
    private YamlConfiguration config;

    public LocateBiomeConf(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        this.config = YamlDefaults.loadAndUpdate(plugin, RESOURCE, file);
    }

    public String ReadYML(String key) {
        String value = config.getString(key);
        return value == null ? null : ChatColor.translateAlternateColorCodes('&', value);
    }
}

package de.ethria.Config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;

public final class DragonEgg {
    private static final String RESOURCE = "Addons/dragonegg.yml";

    private final JavaPlugin plugin;
    private final ArrayList<String> materials;
    private final ArrayList<String> ids;
    private final ArrayList<String> names;
    private final ArrayList<String> lore;

    public DragonEgg(JavaPlugin plugin, ArrayList<String> materials, ArrayList<String> ids,
                     ArrayList<String> names, ArrayList<String> lore) {
        this.plugin = plugin;
        this.materials = materials;
        this.ids = ids;
        this.names = names;
        this.lore = lore;
    }

    public void createConfig() {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        if (!file.exists()) {
            plugin.saveResource(RESOURCE, false);
        }
    }

    public void readConfig() {
        File file = new File(plugin.getDataFolder(), RESOURCE);
        materials.clear();
        ids.clear();
        names.clear();
        lore.clear();
        YamlConfiguration config = YamlDefaults.loadAndUpdate(plugin, RESOURCE, file);
        for (String material : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(material);
            if (section == null) {
                continue;
            }
            materials.add(material);
            names.add(section.getString("NAME", ""));
            ids.add(section.getString("ID", ""));
            lore.add(section.getString("Lore", ""));
        }
    }
}

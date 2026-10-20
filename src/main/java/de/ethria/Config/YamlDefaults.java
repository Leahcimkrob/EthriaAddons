package de.ethria.Config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Merges bundled YAML defaults into an existing user config without replacing user values. */
public final class YamlDefaults {
    private YamlDefaults() { }

    public static YamlConfiguration loadAndUpdate(JavaPlugin plugin, String resource, File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create configuration directory " + parent);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream input = plugin.getResource(resource)) {
            if (input == null) throw new IllegalStateException("Missing bundled configuration " + resource);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(input, StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.save(file);
            return config;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update configuration " + file, exception);
        }
    }
}

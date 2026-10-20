package de.ethria.Language;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import de.ethria.Config.YamlDefaults;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class LanguageManager {
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public LanguageManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String locale = plugin.getConfig().getString("language", "de-DE");
        if (!"de-DE".equalsIgnoreCase(locale) && !"en-US".equalsIgnoreCase(locale)) {
            plugin.getLogger().warning("Unsupported language '" + locale + "'; falling back to de-DE.");
            locale = "de-DE";
        }
        File folder = new File(plugin.getDataFolder(), "language");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create language directory.");
        }
        File file = new File(folder, locale + ".yml");
        YamlConfiguration loadedMessages = YamlDefaults.loadAndUpdate(plugin, "language/" + locale + ".yml", file);
        boolean migratedLore = false;
        for (String key : List.of("holopanel.action-item-lore", "holopanel.action-edit-lore",
                "holopanel.action-delete-lore", "holopanel.action-tools-lore",
                "holopanel.set-tool-lore", "holopanel.delete-tool-lore")) {
            if (!loadedMessages.isList(key) && loadedMessages.getDefaults() != null
                    && loadedMessages.getDefaults().isList(key)) {
                loadedMessages.set(key, loadedMessages.getDefaults().getStringList(key));
                migratedLore = true;
            }
        }
        if (migratedLore) {
            try {
                loadedMessages.save(file);
            } catch (java.io.IOException exception) {
                plugin.getLogger().warning("Could not migrate HoloPanel lore in " + file.getName() + ": " + exception.getMessage());
            }
        }
        if (loadedMessages.getKeys(true).isEmpty()) {
            throw new IllegalArgumentException("Language file is empty or invalid: " + file.getName());
        }
        messages = loadedMessages;
    }

    public void reload() {
        YamlConfiguration previousMessages = messages;
        try {
            load();
        } catch (RuntimeException exception) {
            messages = previousMessages;
            throw exception;
        }
    }

    public Component message(String key) {
        String raw = messages == null ? null : messages.getString(key);
        if (raw == null) {
            raw = key;
        }
        return miniMessage.deserialize(raw);
    }

    public Component message(String key, String placeholder, String value) {
        return message(key, new String[]{placeholder, value});
    }

    public Component message(String key, String... placeholders) {
        String raw = messages == null ? null : messages.getString(key);
        if (raw == null) {
            raw = key;
        }
        TagResolver.Builder resolver = TagResolver.builder();
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            resolver.resolver(Placeholder.unparsed(placeholders[i], placeholders[i + 1]));
        }
        return miniMessage.deserialize(raw, resolver.build());
    }

    public List<Component> messages(String key) {
        if (messages == null) return List.of(miniMessage.deserialize(key));
        List<String> values = messages.getStringList(key);
        if (values.isEmpty()) {
            String single = messages.getString(key);
            if (single == null) single = key;
            values = new ArrayList<>(List.of(single.split("\\n", -1)));
        }
        return values.stream().map(miniMessage::deserialize).toList();
    }
}

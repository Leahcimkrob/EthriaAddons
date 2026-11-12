package de.ethria.Config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

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
            String bundledText;
            try (InputStream formattingInput = plugin.getResource(resource)) {
                if (formattingInput == null) throw new IllegalStateException("Missing bundled configuration " + resource);
                bundledText = new String(formattingInput.readAllBytes(), StandardCharsets.UTF_8);
            }
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            config.save(file);
            if ("Addons/AutoMesssage/automessage.yml".equals(resource)) restoreAutoMessageFormatting(file, bundledText);
            if (resource.startsWith("language/")) restoreLanguageFormatting(file, bundledText);
            return config;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update configuration " + file, exception);
        }
    }

    private static void restoreLanguageFormatting(File file, String bundledText) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> defaults = bundledText.lines().toList();
        for (int i = 1; i < defaults.size(); i++) {
            if (!defaults.get(i).isBlank() || defaults.get(i - 1).isBlank()) continue;
            String previous = defaults.get(i - 1).strip();
            if (!previous.matches("[A-Za-z0-9_-]+:")) continue;
            int sectionIndex = findKey(lines, previous);
            if (sectionIndex < 0 || sectionIndex + 1 >= lines.size() || lines.get(sectionIndex + 1).isBlank()) continue;
            lines.add(sectionIndex + 1, "");
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static void restoreAutoMessageFormatting(File file, String bundledText) throws IOException {
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        List<String> bundledLines = bundledText.lines().toList();
        restoreCommentAndSpacing(lines, bundledLines, "AutomessageInterval:");
        restoreCommentAndSpacing(lines, bundledLines, "AutomessageView:");
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static void restoreCommentAndSpacing(List<String> target, List<String> bundled, String keyPrefix) {
        int targetIndex = findKey(target, keyPrefix);
        int bundledIndex = findKey(bundled, keyPrefix);
        if (targetIndex < 0 || bundledIndex < 0) return;
        int bundledCommentIndex = bundledIndex - 1;
        while (bundledCommentIndex >= 0 && bundled.get(bundledCommentIndex).isBlank()) bundledCommentIndex--;
        if (bundledCommentIndex < 0 || !bundled.get(bundledCommentIndex).stripLeading().startsWith("#")) return;
        String comment = bundled.get(bundledCommentIndex);
        if (targetIndex == 0 || !target.get(targetIndex - 1).trim().equals(comment.trim())) {
            target.add(targetIndex++, comment);
        }
        int blankCount = 0;
        for (int i = bundledCommentIndex - 1; i >= 0 && bundled.get(i).isBlank(); i--) blankCount++;
        int existingBlanks = 0;
        for (int i = targetIndex - 1; i >= 0 && target.get(i).isBlank(); i--) existingBlanks++;
        while (existingBlanks < blankCount) {
            target.add(targetIndex - existingBlanks - 1, "");
            existingBlanks++;
            targetIndex++;
        }
    }

    private static int findKey(List<String> lines, String keyPrefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).stripLeading().startsWith(keyPrefix)) return i;
        }
        return -1;
    }
}

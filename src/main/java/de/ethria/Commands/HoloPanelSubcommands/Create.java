package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.logging.Level;

/** Creates only the HoloPanel name record. Placement and editing are handled separately. */
public final class Create {
    private final Main plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public Create(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin;
        this.database = database;
        this.language = language;
    }

    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(usage(label));
            return true;
        }

        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        if (name.isEmpty()) {
            sender.sendMessage(usage(label));
            return true;
        }

        try {
            database.createPanelName(name);
            sender.sendMessage(language.message("command.created", "name", name));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not create HoloPanel name '" + name + "'.", exception);
            sender.sendMessage(language.message("command.create-failed"));
        }
        return true;
    }

    public net.kyori.adventure.text.Component usage(String label) {
        return language.message("command.usage", "label", label);
    }

    public net.kyori.adventure.text.Component noPermission() {
        return language.message("command.no-permission");
    }
}

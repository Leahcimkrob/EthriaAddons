package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;

/** Creates an AutoMessage record. */
public final class Create {
    private final Main plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public Create(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin;
        this.database = database;
        this.language = language;
    }

    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length != 2 || args[1].trim().isEmpty()) {
            sender.sendMessage(language.message("automessage.create-usage", "label", label));
            return;
        }
        String name = args[1].trim();
        try {
            long id = database.createAutoMessage(name);
            sender.sendMessage(language.message("automessage.create-success", "name", name, "id", Long.toString(id)));
        } catch (SQLException exception) {
            sender.sendMessage(language.message("automessage.create-failed"));
            plugin.getLogger().warning("Could not create AutoMessage '" + name + "': " + exception.getMessage());
        }
    }
}

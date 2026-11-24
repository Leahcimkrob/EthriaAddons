package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.logging.Level;

/** Broadcasts an AutoMessage to all online players. */
public final class View {
    private final Main plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public View(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin; this.database = database; this.language = language;
    }

    public void execute(CommandSender sender, String name) {
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            if (message == null) { sender.sendMessage(language.message("automessage.not-found", "name", name)); return; }
            if (message.description() == null || message.description().isBlank()) { sender.sendMessage(language.message("automessage.empty-message", "name", name)); return; }
            String prefix = plugin.getConfig().getString("AutomessagePrefix", "");
            for (String line : message.description().split("\\n", -1)) Bukkit.broadcast(MiniMessage.miniMessage().deserialize(prefix + line));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load AutoMessage '" + name + "'.", exception);
            sender.sendMessage(language.message("automessage.database-error"));
        }
    }
}

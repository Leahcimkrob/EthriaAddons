package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.logging.Level;

/** Shows an AutoMessage only to the sender. */
public final class Preview {
    private final Main plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public Preview(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin; this.database = database; this.language = language;
    }

    public void execute(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) { sender.sendMessage(language.message("command.player-only")); return; }
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            if (message == null) { player.sendMessage(language.message("automessage.not-found", "name", name)); return; }
            if (message.description() == null || message.description().isBlank()) { player.sendMessage(language.message("automessage.empty-message", "name", name)); return; }
            player.sendMessage(language.message("automessage.preview-divider"));
            String prefix = plugin.getConfig().getString("AutomessagePrefix", "");
            for (String line : message.description().split("\\n", -1)) player.sendMessage(MiniMessage.miniMessage().deserialize(prefix + line));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not preview AutoMessage '" + name + "'.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }
}

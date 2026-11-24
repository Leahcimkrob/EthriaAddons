package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import org.bukkit.command.CommandSender;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class Convert {
    private static final String PERMISSION = "ethriaaddons.command.holopanel.convert";
    private final DatabaseManager database;
    private final LanguageManager language;
    public Convert(DatabaseManager database, LanguageManager language) { this.database = database; this.language = language; }
    public void execute(CommandSender sender, String from, String to) {
        if (!sender.hasPermission(PERMISSION)) { sender.sendMessage(language.message("database.no-permission")); return; }
        try { database.convert("holopanel", from, to); sender.sendMessage(language.message("database.converted", "from", from, "to", to)); }
        catch (IllegalArgumentException e) { sender.sendMessage(language.message("database.convert-invalid")); }
        catch (SQLException e) { sender.sendMessage(language.message("database.convert-failed")); database.getPluginLogger().log(Level.SEVERE, "HoloPanel database conversion failed", e); }
    }
    public List<String> complete(String prefix) { return List.of("sqlite", "mysql").stream().filter(s -> s.startsWith(prefix.toLowerCase(Locale.ROOT))).toList(); }
}

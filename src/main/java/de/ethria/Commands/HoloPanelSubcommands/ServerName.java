package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Database.DatabaseManager;
import org.bukkit.command.CommandSender;

/** Reports the backend server name resolved from LuckPerms. */
public final class ServerName {
    private final DatabaseManager database;

    public ServerName(DatabaseManager database) {
        this.database = database;
    }

    public void execute(CommandSender sender) {
        sender.sendMessage("§aLuckPerms-Servername: §f" + database.getServerName());
    }
}

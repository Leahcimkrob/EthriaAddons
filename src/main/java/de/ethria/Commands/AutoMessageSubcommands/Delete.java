package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.Database.DatabaseManager;
import de.ethria.Language.LanguageManager;
import de.ethria.main.Main;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.dialog.Dialog;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;

/** Deletes an AutoMessage after Paper dialog confirmation. */
public final class Delete {
    private final Main plugin;
    private final DatabaseManager database;
    private final LanguageManager language;

    public Delete(Main plugin, DatabaseManager database, LanguageManager language) {
        this.plugin = plugin;
        this.database = database;
        this.language = language;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(language.message("command.player-only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(language.message("automessage.delete-usage"));
            return;
        }
        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        try {
            DatabaseManager.AutoMessage message = database.getAutoMessage(name);
            if (message == null) {
                player.sendMessage(language.message("automessage.not-found", "name", name));
                return;
            }
            confirm(player, message);
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not load AutoMessage for deletion: " + name, exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }

    private void confirm(Player player, DatabaseManager.AutoMessage message) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(language.message("automessage.delete-title", "name", message.name()))
                        .canCloseWithEscape(true).build())
                .type(DialogType.confirmation(
                        ActionButton.create(language.message("automessage.delete-confirm"), null, 150,
                                DialogAction.customClick((response, audience) -> {
                                    if (audience instanceof Player clicker) delete(clicker, message);
                                }, ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(language.message("automessage.cancel"), null, 150, null))));
        player.showDialog(dialog);
    }

    private void delete(Player player, DatabaseManager.AutoMessage message) {
        try {
            database.deleteAutoMessage(message.id());
            player.sendMessage(language.message("automessage.deleted", "name", message.name()));
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not delete AutoMessage '" + message.name() + "'.", exception);
            player.sendMessage(language.message("automessage.database-error"));
        }
    }
}

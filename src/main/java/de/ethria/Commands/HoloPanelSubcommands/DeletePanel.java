package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Language.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Starts and handles the chat confirmation flow for panel deletion. */
public final class DeletePanel {
    private final Editor editor;
    private final LanguageManager language;

    public DeletePanel(Editor editor, LanguageManager language) {
        this.editor = editor;
        this.language = language;
    }

    public void execute(CommandSender sender, String panelName) {
        if (!sender.hasPermission("ethriaaddons.command.holopanel.delete")) {
            sender.sendMessage(language.message("command.no-permission"));
        } else if (!(sender instanceof Player player)) {
            sender.sendMessage(language.message("command.player-only"));
        } else {
            editor.beginDeleteConfirmation(player, panelName);
        }
    }

    public void confirm(CommandSender sender, String idArgument, boolean confirm) {
        if (!sender.hasPermission("ethriaaddons.command.holopanel.delete")) {
            sender.sendMessage(language.message("command.no-permission"));
            return;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(language.message("command.player-only"));
            return;
        }
        try {
            editor.handleDeleteConfirmation(player, Long.parseLong(idArgument), confirm);
        } catch (NumberFormatException exception) {
            sender.sendMessage(language.message("holopanel.delete-confirmation-missing"));
        }
    }
}

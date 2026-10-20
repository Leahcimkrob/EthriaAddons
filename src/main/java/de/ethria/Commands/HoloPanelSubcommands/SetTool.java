package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Language.LanguageManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Gives a player the placement tool for a named HoloPanel. */
public final class SetTool {
    private final Editor editor;
    private final LanguageManager language;

    public SetTool(Editor editor, LanguageManager language) {
        this.editor = editor;
        this.language = language;
    }

    public void execute(CommandSender sender, String panelName) {
        if (!sender.hasPermission("ethriaaddons.command.holopanel.set")) {
            sender.sendMessage(language.message("command.no-permission"));
        } else if (!(sender instanceof Player player)) {
            sender.sendMessage(language.message("command.player-only"));
        } else {
            editor.givePanelTool(player, panelName, (byte) 1);
        }
    }
}

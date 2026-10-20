package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Language.LanguageManager;
import org.bukkit.command.CommandSender;

/** Reloads every saved hologram position for the current server. */
public final class ReloadHolograms {
    private final Editor editor;
    private final LanguageManager language;

    public ReloadHolograms(Editor editor, LanguageManager language) {
        this.editor = editor;
        this.language = language;
    }

    public void execute(CommandSender sender) {
        if (!sender.hasPermission("ethriaaddons.command.holopanel.reload")) {
            sender.sendMessage(language.message("command.no-permission"));
            return;
        }
        editor.reloadHolograms(sender);
    }
}

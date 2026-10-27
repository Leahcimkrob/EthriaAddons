package de.ethria.Commands.HoloPanelSubcommands;

import de.ethria.Language.LanguageManager;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Displays the localized HoloPanel command reference. */
public final class Help {
    private static final List<String> KEYS = List.of("holopanel.help-title", "holopanel.help-reload",
            "holopanel.help-set", "holopanel.help-remove", "holopanel.help-delete",
            "holopanel.help-create", "holopanel.help-editor", "holopanel.help-servername");
    private final LanguageManager language;

    public Help(LanguageManager language) { this.language = language; }

    public void execute(CommandSender sender) {
        KEYS.forEach(key -> sender.sendMessage(language.message(key)));
    }
}

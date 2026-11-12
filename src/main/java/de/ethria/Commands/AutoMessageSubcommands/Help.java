package de.ethria.Commands.AutoMessageSubcommands;

import de.ethria.Language.LanguageManager;
import org.bukkit.command.CommandSender;

import java.util.List;

/** Displays localized AutoMessage command help. */
public final class Help {
    private static final List<String> KEYS = List.of("automessage.help-title", "automessage.help-create",
            "automessage.help-editor", "automessage.help-view", "automessage.help-delete",
            "automessage.help-config", "automessage.help-debug", "automessage.help-reload");
    private final LanguageManager language;

    public Help(LanguageManager language) { this.language = language; }

    public void execute(CommandSender sender) { KEYS.forEach(key -> sender.sendMessage(language.message(key))); }
}

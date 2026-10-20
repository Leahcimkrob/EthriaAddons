package de.ethria.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class HoloPanel implements CommandExecutor {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eVerwendung: /" + label + " <MiniMessage>");
            sender.sendMessage("§7Beispiel: /" + label + " <gold>Hallo <bold>Welt</bold></gold>");
            return true;
        }

        sender.sendMessage(miniMessage.deserialize(String.join(" ", args)));
        return true;
    }
}

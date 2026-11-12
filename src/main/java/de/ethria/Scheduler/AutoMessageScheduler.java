package de.ethria.Scheduler;

import de.ethria.Database.DatabaseManager;
import de.ethria.main.Main;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/** Periodically broadcasts active AutoMessages belonging to this server. */
public final class AutoMessageScheduler {
    private final Main plugin;
    private final DatabaseManager database;
    private BukkitTask task;
    private int sequence;
    private long nextRunAtMillis;

    public AutoMessageScheduler(Main plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void start() { restart(); }

    public void restart() {
        stop();
        int interval = Math.max(1, plugin.autoMessageConfig().getInt("AutomessageInterval", 30));
        long ticks = interval * 60L * 20L;
        nextRunAtMillis = System.currentTimeMillis() + interval * 60_000L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, ticks, ticks);
    }

    public long secondsUntilNextMessage() {
        return Math.max(0L, (nextRunAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void broadcastNext() {
        int interval = Math.max(1, plugin.autoMessageConfig().getInt("AutomessageInterval", 30));
        nextRunAtMillis = System.currentTimeMillis() + interval * 60_000L;
        try {
            List<DatabaseManager.AutoMessage> messages = database.getAutoMessages().stream()
                    .filter(DatabaseManager.AutoMessage::active)
                    .filter(message -> message.description() != null && !message.description().isBlank())
                    .filter(message -> database.getServerName().equalsIgnoreCase(message.serverName()))
                    .sorted(Comparator.comparingLong(DatabaseManager.AutoMessage::id))
                    .toList();
            if (messages.isEmpty()) return;
            String mode = plugin.autoMessageConfig().getString("AutomessageView", "RANDOM").toUpperCase(Locale.ROOT);
            DatabaseManager.AutoMessage selected;
            if (mode.equals("ASC") || mode.equals("DESC")) {
                List<DatabaseManager.AutoMessage> sorted = messages;
                if (mode.equals("DESC")) sorted = messages.reversed();
                selected = sorted.get(Math.floorMod(sequence++, sorted.size()));
            } else {
                selected = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
            }
            String prefix = plugin.getConfig().getString("AutomessagePrefix", "");
            for (String line : selected.description().split("\\n", -1)) {
                Bukkit.broadcast(MiniMessage.miniMessage().deserialize(prefix + line));
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not select an automatic message.", exception);
        }
    }
}

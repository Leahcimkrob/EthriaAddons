package de.ethria.HoloPanel;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Aligns CMI's generated display so its visible top edge matches the requested position. */
final class CmiHologramTopAligner {
    private static final class Pending {
        final Object hologram;
        final double topY;
        Location location;
        CompletableFuture<?> update;
        int corrections;

        Pending(Object hologram, Location location, double topY, CompletableFuture<?> update) {
            this.hologram = hologram;
            this.topY = topY;
            this.location = location.clone();
            this.update = update;
        }
    }

    private final JavaPlugin plugin;
    private final Map<String, Pending> pending = new HashMap<>();
    private BukkitTask task;

    CmiHologramTopAligner(JavaPlugin plugin) { this.plugin = plugin; }

    void align(String name, Object hologram, Location topLocation, Location initialLocation) throws ReflectiveOperationException {
        CompletableFuture<?> update = (CompletableFuture<?>) call(hologram, "requestFullUpdate");
        pending.put(name, new Pending(hologram, initialLocation, topLocation.getY(), update));
        if (task == null) task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::alignPending, 1L, 2L);
    }

    void remove(String name) { pending.remove(name); }

    private void alignPending() {
        pending.entrySet().removeIf(entry -> {
            try {
                return align(entry.getValue());
            } catch (ReflectiveOperationException | RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not align CMI HoloPanel '" + entry.getKey() + "'.", exception);
                return true;
            }
        });
        if (pending.isEmpty() && task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean align(Pending pending) throws ReflectiveOperationException {
        if (!pending.update.isDone()) return false;
        pending.update.join();
        Object viewers = call(pending.hologram, "getPlayersFromVisibilityRange");
        if (!(viewers instanceof Iterable<?> ids)) return false;
        for (Object id : ids) {
            if (!(id instanceof UUID uuid)) continue;
            Object data = pending.hologram.getClass().getMethod("getData", UUID.class).invoke(pending.hologram, uuid);
            Object batch = call(data, "getHologramBatch");
            if (batch == null || !batch.getClass().getSimpleName().equals("CMIHologramBatchDisplay")) continue;
            Object display = call(batch, "getFrontDisplay");
            if (display == null || !(call(display, "getDisplayEntity") instanceof TextDisplay text)) continue;
            text.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            text.setDefaultBackground(false);
            Transformation transformation = (Transformation) call(display, "getTransformation");
            Location origin = (Location) call(display, "getLocation");
            int lines = text.getText().split("\\n", -1).length;
            float height = lines * 10 / 40f;
            Matrix4f matrix = new Matrix4f().translation(transformation.getTranslation())
                    .rotate(transformation.getLeftRotation()).scale(transformation.getScale())
                    .rotate(transformation.getRightRotation());
            float bottom = matrix.transformPosition(new Vector3f(0, 0, 0)).y;
            float top = matrix.transformPosition(new Vector3f(0, height, 0)).y;
            double shift = pending.topY - (origin.getY() + Math.max(bottom, top));
            if (Math.abs(shift) > 0.001) {
                if (++pending.corrections > 4) throw new IllegalStateException("CMI display did not settle at the requested top edge.");
                Location location = pending.location.clone().add(0, shift, 0);
                pending.hologram.getClass().getMethod("setLocation", Location.class).invoke(pending.hologram, location);
                pending.location = location;
                pending.update = (CompletableFuture<?>) call(pending.hologram, "requestFullUpdate");
                return false;
            }
            return true;
        }
        return false;
    }

    private Object call(Object instance, String method) throws ReflectiveOperationException {
        return instance.getClass().getMethod(method).invoke(instance);
    }
}

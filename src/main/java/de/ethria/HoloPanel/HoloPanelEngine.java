package de.ethria.HoloPanel;

import de.ethria.Database.DatabaseManager;
import de.ethria.main.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import de.ethria.Database.DatabaseManager.PanelLocation;
import java.sql.SQLException;
import java.util.logging.Level;

/** Renders a HoloPanel through CMI, DecentHolograms, or Paper TextDisplay. */
public final class HoloPanelEngine {
    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final CmiHologramTopAligner cmiTopAligner;
    private final Map<Long, Integer> segmentCounts = new HashMap<>();

    public HoloPanelEngine(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.cmiTopAligner = new CmiHologramTopAligner(plugin);
    }

    public void render(long id, String text, Location location, double widthBlocks, double heightBlocks) {
        remove(id);
        Engine engine = resolveEngine();
        List<HoloPanelText.Segment> segments = HoloPanelText.parse(text);
        List<String> lines = segments.stream().flatMap(segment -> segment.lines().stream()).toList();
        int maximumLineWidth = measureMaximumLineWidth(lines);
        float scale = calculateScale(widthBlocks, heightBlocks, maximumLineWidth, lines.size());
        segmentCounts.put(id, segments.size());
        try {
            switch (engine) {
                case CMI -> renderCmi(id, segments, location, scale, maximumLineWidth);
                case DECENT_HOLOGRAMS -> renderDecent(id, segments, location, scale, maximumLineWidth);
                case TEXT_DISPLAY -> renderTextDisplay(id, segments, location, scale, maximumLineWidth);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not render HoloPanel " + id + " with " + engine
                    + "; falling back to Paper TextDisplay.", exception);
            removeExternal(id);
            renderTextDisplay(id, segments, location, scale, maximumLineWidth);
        }
    }

    public void remove(long id) {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().getOrDefault(Main.HOLOPANEL_ID_KEY,
                        PersistentDataType.LONG, -1L) == id) entity.remove();
            }
        }
        removeExternal(id);
    }

    public String hologramName(long id) {
        return "ethriaaddons_holopanel_" + id;
    }

    public void restoreAll() throws SQLException {
        for (DatabaseManager.Panel panel : database.getPanels()) {
            String text = HoloPanelText.fromPanel(panel);
            for (PanelLocation position : panel.locations()) {
                if (position.server() != null && !position.server().isBlank()
                        && !plugin.getServer().getName().equalsIgnoreCase(position.server())) continue;
                World world = Bukkit.getWorld(position.world());
                if (world == null) continue;
                Location location = new Location(world, position.x(), position.y(), position.z(),
                        position.yaw() == null ? 0f : position.yaw(), position.pitch() == null ? 0f : position.pitch());
                render(position.id(), text, location, position.widthBlocks(), position.heightBlocks());
            }
        }
    }

    private Engine resolveEngine() {
        String requested = database.hologramEngine();
        boolean cmiEnabled = enabled("CMI");
        boolean decentEnabled = enabled("DecentHolograms");
        return switch (requested) {
            case "cmi" -> cmiEnabled ? Engine.CMI : unavailable(requested);
            case "decent", "decentholograms", "decent-holograms" -> decentEnabled
                    ? Engine.DECENT_HOLOGRAMS : unavailable(requested);
            case "textdisplay", "paper", "vanilla" -> Engine.TEXT_DISPLAY;
            default -> cmiEnabled ? Engine.CMI : decentEnabled ? Engine.DECENT_HOLOGRAMS : Engine.TEXT_DISPLAY;
        };
    }

    private Engine unavailable(String requested) {
        plugin.getLogger().warning("Configured HoloPanel engine '" + requested
                + "' is not enabled. Falling back to Paper TextDisplay.");
        return Engine.TEXT_DISPLAY;
    }

    private boolean enabled(String pluginName) {
        Plugin provider = Bukkit.getPluginManager().getPlugin(pluginName);
        return provider != null && provider.isEnabled();
    }

    private void renderTextDisplay(long id, List<HoloPanelText.Segment> segments, Location location, float scale, int lineWidth) {
        if (location.getWorld() == null) throw new IllegalArgumentException("HoloPanel location has no loaded world.");
        double lineHeight = 0.25 * scale;
        int totalLines = lineCount(segments);
        int precedingLines = 0;
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            HoloPanelText.Segment segment = segments.get(segmentIndex);
            Location displayLocation = location.clone().subtract(0, (totalLines - precedingLines) * lineHeight, 0);
            location.getWorld().spawn(displayLocation, TextDisplay.class, entity -> {
                entity.text(MiniMessage.miniMessage().deserialize(String.join("\n", segment.lines())));
                entity.setLineWidth(Math.max(1, lineWidth));
                entity.setAlignment(segment.alignment() == HoloPanelText.Alignment.LEFT
                        ? TextDisplay.TextAlignment.LEFT : TextDisplay.TextAlignment.CENTER);
                entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                entity.setDefaultBackground(false);
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
                entity.setPersistent(true);
                entity.getPersistentDataContainer().set(Main.HOLOPANEL_ID_KEY, PersistentDataType.LONG, id);
            });
            precedingLines += segment.lines().size();
        }
    }

    private void renderDecent(long id, List<HoloPanelText.Segment> segments, Location location, float scale, int maximumLineWidth) throws ReflectiveOperationException {
        provider("DecentHolograms");
        double lineHeight = 0.25 * scale;
        int totalLines = lineCount(segments);
        int precedingLines = 0;
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            HoloPanelText.Segment segment = segments.get(segmentIndex);
            Location displayLocation = location.clone().subtract(0, (totalLines - precedingLines) * lineHeight, 0);
            String name = segmentName(id, segmentIndex);
            invokeDecentRenderer("render", name, displayLocation,
                    segment.lines().stream().map(this::asLegacy).toList(), scale, scale,
                    maximumLineWidth, segment.alignment().name());
            precedingLines += segment.lines().size();
        }
    }

    private void renderCmi(long id, List<HoloPanelText.Segment> segments, Location location, float scale, int maximumLineWidth) throws ReflectiveOperationException {
        Plugin provider = provider("CMI");
        ClassLoader loader = provider.getClass().getClassLoader();
        Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI", true, loader);
        Object cmi = cmiClass.getMethod("getInstance").invoke(null);
        Object manager = cmiClass.getMethod("getHologramManager").invoke(cmi);
        int totalLines = lineCount(segments);
        int precedingLines = 0;
        Class<?> hologramClass = Class.forName("com.Zrips.CMI.Modules.Holograms.CMIHologram", true, loader);
        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            HoloPanelText.Segment segment = segments.get(segmentIndex);
            String name = segmentName(id, segmentIndex);
            Object hologram = invoke(manager, "getByName", name);
            if (hologram != null) invoke(hologram, "delete");
            Constructor<?> constructor = hologramClass.getConstructor(String.class, Location.class);
            Location segmentTop = location.clone().subtract(0, precedingLines * 0.25 * scale, 0);
            Location initialLocation = segmentTop.clone().subtract(0, 1, 0);
            hologram = constructor.newInstance(name, initialLocation);
            invoke(hologram, "setLocation", initialLocation);
            Object pages = invoke(hologram, "getPages");
            invoke(pages, "setLines", segment.lines().stream().map(this::asLegacy).toList());
            Object textSettings = invoke(hologram, "getTextSettings");
            Class<?> alignmentClass = Class.forName("com.Zrips.CMI.Modules.Display.CMITextAlignment", true, loader);
            Object cmiAlignment = java.util.Arrays.stream(alignmentClass.getEnumConstants())
                    .filter(value -> ((Enum<?>) value).name().equals(segment.alignment().name()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "CMI does not support text alignment " + segment.alignment()));
            invoke(textSettings, "setTextAlignment", cmiAlignment);
            invoke(textSettings, "setLineWidth", maximumLineWidth);
            invoke(textSettings, "setBackgroundAlpha", 0);
            Object settings = invoke(hologram, "getSettings");
            invoke(settings, "setSaveToFile", false);
            Class<?> vectorClass = Class.forName("net.Zrips.CMILib.Container.CMIVector2D", true, loader);
            Object vector = vectorClass.getConstructor(double.class, double.class).newInstance((double) scale, (double) scale);
            invoke(settings, "setScale", vector);
            Class<?> billboardClass = Class.forName("com.Zrips.CMI.Modules.Display.CMIBillboard", true, loader);
            invoke(settings, "setBillboard", billboardClass.getField("FIXED").get(null));
            invoke(settings, "setYaw", (double) location.getYaw());
            invoke(hologram, "show");
            cmiTopAligner.align(name, hologram, segmentTop, initialLocation, segment.alignment());
            precedingLines += segment.lines().size();
        }
    }

    private int lineCount(List<HoloPanelText.Segment> segments) {
        return segments.stream().mapToInt(segment -> segment.lines().size()).sum();
    }

    private String segmentName(long id, int segmentIndex) {
        return hologramName(id) + "_segment_" + segmentIndex;
    }

    private Object invokeDecentRenderer(String method, Object... arguments) throws ReflectiveOperationException {
        try {
            Class<?> renderer = Class.forName("de.ethria.HoloPanel.DecentHologramRenderer", true,
                    plugin.getClass().getClassLoader());
            Class<?>[] parameterTypes = new Class<?>[arguments.length];
            parameterTypes[0] = String.class;
            parameterTypes[1] = Location.class;
            parameterTypes[2] = List.class;
            parameterTypes[3] = float.class;
            parameterTypes[4] = float.class;
            parameterTypes[5] = int.class;
            parameterTypes[6] = String.class;
            return renderer.getMethod(method, parameterTypes).invoke(null, arguments);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) throw reflective;
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("DecentHolograms display rendering failed.", cause);
        }
    }

    private int measureMaximumLineWidth(List<String> lines) {
        return lines.stream().map(this::asLegacy).mapToInt(this::measureLineWidth).max().orElse(1);
    }

    private int measureLineWidth(String line) {
        int width = 0;
        boolean bold = false;
        for (int offset = 0; offset < line.length();) {
            char character = line.charAt(offset);
            if ((character == '&' || character == '\u00a7') && offset + 1 < line.length()) {
                char code = Character.toLowerCase(line.charAt(offset + 1));
                if ("0123456789abcdefklmnorx".indexOf(code) >= 0) {
                    if (code == 'l') bold = true;
                    else if (code == 'r' || "0123456789abcdefx".indexOf(code) >= 0) bold = false;
                    offset += 2;
                    continue;
                }
            }
            int codePoint = line.codePointAt(offset);
            MapFont.CharacterSprite glyph = codePoint <= Character.MAX_VALUE
                    ? MinecraftFont.Font.getChar((char) codePoint) : null;
            width += (glyph == null ? 5 : glyph.getWidth()) + 1 + (bold ? 1 : 0);
            offset += Character.charCount(codePoint);
        }
        return Math.max(1, width);
    }

    private float calculateScale(double widthBlocks, double heightBlocks, int maximumLineWidth, int lineCount) {
        double widthScale = widthBlocks * 0.9 / Math.max(0.025, maximumLineWidth * 0.025);
        double heightScale = heightBlocks * 0.9 / Math.max(0.25, lineCount * 0.25);
        return (float) Math.max(0.01, Math.min(1.0, Math.min(widthScale, heightScale)));
    }

    private void removeExternal(long id) {
        String name = hologramName(id);
        int segments = segmentCounts.getOrDefault(id, 1);
        for (int index = 0; index < segments; index++) cmiTopAligner.remove(segmentName(id, index));
        try {
            Plugin decent = Bukkit.getPluginManager().getPlugin("DecentHolograms");
            if (decent != null && decent.isEnabled()) {
                Class<?> renderer = Class.forName("de.ethria.HoloPanel.DecentHologramRenderer", true,
                        plugin.getClass().getClassLoader());
                for (int index = 0; index < segments; index++)
                    renderer.getMethod("remove", String.class).invoke(null, segmentName(id, index));
                renderer.getMethod("remove", String.class).invoke(null, name);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Could not remove DecentHolograms hologram " + name + ".", exception);
        }
        try {
            Plugin cmi = Bukkit.getPluginManager().getPlugin("CMI");
            if (cmi != null && cmi.isEnabled()) {
                ClassLoader loader = cmi.getClass().getClassLoader();
                Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI", true, loader);
                Object manager = cmiClass.getMethod("getHologramManager").invoke(cmiClass.getMethod("getInstance").invoke(null));
                for (int index = 0; index < segments; index++) {
                    Object hologram = invoke(manager, "getByName", segmentName(id, index));
                    if (hologram != null) invoke(hologram, "delete");
                }
                Object legacy = invoke(manager, "getByName", name);
                if (legacy != null) invoke(legacy, "delete");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().fine("Could not remove CMI hologram " + name + ": " + exception.getMessage());
        }
    }

    private String asLegacy(String text) {
        Component component = MiniMessage.miniMessage().deserialize(text);
        return LegacyComponentSerializer.legacyAmpersand().serialize(component);
    }

    private Plugin provider(String name) throws ReflectiveOperationException {
        Plugin provider = Bukkit.getPluginManager().getPlugin(name);
        if (provider == null || !provider.isEnabled()) throw new ReflectiveOperationException(name + " is not enabled.");
        return provider;
    }

    private Object invoke(Object target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), methodName, args);
        return method.invoke(target, args);
    }

    private Object invokeStatic(Class<?> target, String methodName, Object... args) throws ReflectiveOperationException {
        Method method = findMethod(target, methodName, args);
        return method.invoke(null, args);
    }

    private Method findMethod(Class<?> type, String name, Object... args) throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < args.length; index++) {
                if (args[index] != null && !wrap(parameters[index]).isAssignableFrom(args[index].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name + "(" + args.length + " args)");
    }

    private Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private enum Engine { CMI, DECENT_HOLOGRAMS, TEXT_DISPLAY }
}

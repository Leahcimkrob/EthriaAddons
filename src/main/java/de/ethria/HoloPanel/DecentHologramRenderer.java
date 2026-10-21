package de.ethria.HoloPanel;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.display.DisplayBase;
import eu.decentsoftware.holograms.display.DisplaySettings;
import eu.decentsoftware.holograms.display.DisplayService;
import eu.decentsoftware.holograms.display.TextDisplay;
import eu.decentsoftware.holograms.display.attribute.DisplayAttribute;
import eu.decentsoftware.holograms.display.attribute.definition.BillboardAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.definition.ScaleAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.definition.TextLineWidthAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.definition.TextBackgroundColorAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.definition.TextAlignmentAttributeDefinition;
import eu.decentsoftware.holograms.display.attribute.value.display.BillboardConstraintsValue;
import eu.decentsoftware.holograms.display.attribute.value.primitives.IntegerValue;
import eu.decentsoftware.holograms.display.attribute.value.primitives.Vector3fValue;
import eu.decentsoftware.holograms.display.attribute.value.color.RgbaValue;
import eu.decentsoftware.holograms.display.attribute.value.display.TextAlignmentValue;
import eu.decentsoftware.holograms.platform.api.data.display.TextDisplayAlignment;
import eu.decentsoftware.holograms.platform.api.data.DecentLocation;
import eu.decentsoftware.holograms.platform.api.data.display.DisplayBillboardConstraints;
import org.bukkit.Location;

import java.util.List;

/** DecentHolograms 2.x display renderer, kept isolated from the optional plugin. */
public final class DecentHologramRenderer {
    private DecentHologramRenderer() { }

    public static void remove(String name) {
        Hologram old = DHAPI.getHologram(name);
        if (old != null) DHAPI.removeHologram(name);
        String displayName = name + "_display";
        DisplayService service = DecentHologramsAPI.get().getDisplayModule().getDisplayService();
        if (service.getDisplay(displayName) != null) service.deleteDisplay(displayName);
    }

    public static void render(String name, Location location, List<String> lines,
                              float scaleX, float scaleY, int maxLineWidth, String alignment) {
        String displayName = name + "_display";
        remove(name);
        DisplayService service = DecentHologramsAPI.get().getDisplayModule().getDisplayService();
        TextDisplay display = new TextDisplay(displayName, decentLocation(location), new DisplaySettings());
        display.setLines(lines);
        display.setAttribute(ScaleAttributeDefinition.KEY, new DisplayAttribute<>(ScaleAttributeDefinition.KEY,
                new Vector3fValue(scaleX, scaleY, 1f)));
        display.setAttribute(TextLineWidthAttributeDefinition.KEY,
                new DisplayAttribute<>(TextLineWidthAttributeDefinition.KEY, new IntegerValue(Math.max(1, maxLineWidth))));
        display.setAttribute(TextBackgroundColorAttributeDefinition.KEY,
                new DisplayAttribute<>(TextBackgroundColorAttributeDefinition.KEY, new RgbaValue(0, 0, 0, 0)));
        display.setAttribute(TextAlignmentAttributeDefinition.KEY,
                new DisplayAttribute<>(TextAlignmentAttributeDefinition.KEY,
                        new TextAlignmentValue(TextDisplayAlignment.valueOf(alignment))));
        display.setAttribute(BillboardAttributeDefinition.KEY,
                new DisplayAttribute<>(BillboardAttributeDefinition.KEY,
                        new BillboardConstraintsValue(DisplayBillboardConstraints.FIXED)));
        service.registerDisplay(display);
        service.updateDisplay(display);
    }

    private static DecentLocation decentLocation(Location location) {
        return new DecentLocation(location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), location.getYaw(), location.getPitch());
    }

    public static void updateLines(String name, List<String> lines) {
        DisplayService service = DecentHologramsAPI.get().getDisplayModule().getDisplayService();
        DisplayBase display = service.getDisplay(name + "_display");
        if (!(display instanceof TextDisplay text)) throw new IllegalStateException("HoloPanel display is missing.");
        text.setLines(lines);
        service.updateDisplay(text);
    }
}

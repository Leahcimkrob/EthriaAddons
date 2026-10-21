package de.ethria.HoloPanel;

import de.ethria.Database.DatabaseManager.Panel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Parses HoloPanel alignment tags into contiguous, consistently aligned line groups. */
public final class HoloPanelText {
    public enum Alignment { CENTER, LEFT }

    public record Segment(List<String> lines, Alignment alignment) { }

    private HoloPanelText() { }

    public static String fromPanel(Panel panel) {
        String headline = panel.headline() == null || panel.headline().isBlank() ? panel.name() : panel.headline();
        return panel.description() == null || panel.description().isBlank()
                ? headline : headline + "\n\n" + panel.description();
    }

    public static List<Segment> parse(String text) {
        List<String> lines = new ArrayList<>();
        List<Alignment> alignments = new ArrayList<>();
        Deque<Alignment> alignmentStack = new ArrayDeque<>();
        Alignment current = Alignment.CENTER;
        Alignment lineAlignment = current;
        StringBuilder line = new StringBuilder();

        for (int index = 0; index < text.length();) {
            if (text.charAt(index) == '<') {
                int end = text.indexOf('>', index + 1);
                if (end >= 0) {
                    String tag = text.substring(index + 1, end).trim().toLowerCase(Locale.ROOT);
                    if (tag.equals("left") || tag.equals("center")) {
                        alignmentStack.push(current);
                        current = tag.equals("left") ? Alignment.LEFT : Alignment.CENTER;
                        if (line.toString().isBlank()) lineAlignment = current;
                        index = end + 1;
                        continue;
                    }
                    if (tag.equals("/left") || tag.equals("/center")) {
                        if (!alignmentStack.isEmpty()) current = alignmentStack.pop();
                        if (line.toString().isBlank()) lineAlignment = current;
                        index = end + 1;
                        continue;
                    }
                }
            }

            char character = text.charAt(index++);
            if (character == '\n') {
                lines.add(line.toString());
                alignments.add(lineAlignment);
                line.setLength(0);
                lineAlignment = current;
            } else {
                if (line.toString().isBlank()) lineAlignment = current;
                line.append(character);
            }
        }
        lines.add(line.toString());
        alignments.add(lineAlignment);

        List<Segment> segments = new ArrayList<>();
        List<String> segmentLines = new ArrayList<>();
        Alignment segmentAlignment = alignments.getFirst();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            Alignment alignment = alignments.get(lineIndex);
            if (alignment != segmentAlignment) {
                segments.add(new Segment(List.copyOf(segmentLines), segmentAlignment));
                segmentLines.clear();
                segmentAlignment = alignment;
            }
            segmentLines.add(lines.get(lineIndex));
        }
        segments.add(new Segment(List.copyOf(segmentLines), segmentAlignment));
        return List.copyOf(segments);
    }
}

package com.berkeleybikebuilders.tubewinder.gcode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A generated program: the G-code lines plus what the generator worked out along the way.
 *
 * <p>The summary is here so a GUI can show pass count, run length and the tow-head numbers without
 * re-deriving anything, and so the numbers can be sanity-checked against the machine.
 */
public record GcodeProgram(List<String> lines, Summary summary) {

    public GcodeProgram {
        lines = List.copyOf(lines);
    }

    public String text() {
        return String.join("\n", lines) + "\n";
    }

    /**
     * Write the whole program in one shot, replacing anything already at that path.
     *
     * <p>Generator v7 opened the file in append mode once per line, which meant re-running with the
     * same filename silently concatenated the new program onto the old one. This does not.
     */
    public void writeTo(Path path) throws IOException {
        Files.writeString(path, text(), StandardCharsets.UTF_8);
    }

    /**
     * @param layers                 layers needed for the requested wall thickness
     * @param passes                 raw (fractional) pass count
     * @param passCount              passes actually emitted
     * @param tubeLengthMm           overall mandrel length
     * @param towHeadEnabled         whether offset compensation was applied
     * @param axialLeadNearMm        head lead at the near end of the tube
     * @param axialLeadFarMm         head lead at the far end of the tube
     * @param freeSpanNearMm         unsupported tow length at the near end, during a pass
     * @param computedTakeupNearDeg  rotation the geometry says takes up the slack at a turnaround
     * @param flipTakeupUsedDeg      rotation actually emitted per half-turnaround
     * @param minXOffsetMm           furthest the carriage goes one way from where it started (negative)
     * @param maxXOffsetMm           furthest it goes the other way (positive)
     */
    public record Summary(long layers,
                          double passes,
                          int passCount,
                          double tubeLengthMm,
                          boolean towHeadEnabled,
                          double axialLeadNearMm,
                          double axialLeadFarMm,
                          double freeSpanNearMm,
                          double computedTakeupNearDeg,
                          double flipTakeupUsedDeg,
                          double minXOffsetMm,
                          double maxXOffsetMm) {

        /** Rail needed on the first-pass side of the parked position. */
        public double clearanceTowardFirstPassMm() {
            return -minXOffsetMm;
        }

        /** Rail needed on the other side. */
        public double clearanceBehindMm() {
            return maxXOffsetMm;
        }


        public String report() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Layers ............. %d%n", layers));
            sb.append(String.format("Passes ............. %d (raw %.3f)%n", passCount, passes));
            sb.append(String.format("Tube length ........ %.2f mm%n", tubeLengthMm));
            sb.append(String.format("Carriage envelope .. %.2f mm to %.2f mm about the start%n",
                    minXOffsetMm, maxXOffsetMm));
            if (towHeadEnabled) {
                sb.append(String.format("Tow head lead ...... %.2f mm near end, %.2f mm far end%n",
                        axialLeadNearMm, axialLeadFarMm));
                sb.append(String.format("Free tow span ...... %.2f mm at the near end%n",
                        freeSpanNearMm));
                sb.append(String.format("Turnaround takeup .. %.1f deg emitted, %.1f deg computed%n",
                        flipTakeupUsedDeg * 2, computedTakeupNearDeg));
            } else {
                sb.append("Tow head lead ...... disabled (v7-compatible output)\n");
            }
            return sb.toString();
        }
    }
}

package com.berkeleybikebuilders.tubewinder.gcode;

import com.berkeleybikebuilders.tubewinder.WindingParameters;
import com.berkeleybikebuilders.tubewinder.geometry.TowHeadGeometry;
import com.berkeleybikebuilders.tubewinder.geometry.TraverseSegment;
import com.berkeleybikebuilders.tubewinder.geometry.TubeProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates winder G-code. Direct descendant of Generator v7 - same pass structure, same index
 * scheme, same layer and pass arithmetic - with the tow-head offset added and Z driven from the
 * wrap angle.
 *
 * <h2>Axes</h2>
 * <ul>
 *   <li><b>X</b> - carriage travel along the mandrel, mm.</li>
 *   <li><b>Y</b> - mandrel rotation, degrees (geared 1 unit = 1 degree).</li>
 *   <li><b>Z</b> - tow head angle, degrees (geared 1 unit = 1 degree). Zero is neutral; during a
 *       pass it sits at the wrap angle so the roller stays square to the tow and the tow does not
 *       walk off the roller edge.</li>
 * </ul>
 *
 * <p>Everything is emitted in {@code G21 G91} - millimetres, incremental - as v7 did.
 *
 * <h2>Direction convention</h2>
 *
 * v7's {@code direction} is +1 or -1 and its {@code lin_sec} emits <em>negative</em> X for
 * {@code direction == 1}. That is preserved: the sign of carriage travel is {@code -direction}.
 * Passes start at {@code direction = 1}.
 *
 * <h2>One pass, in the order the machine does it</h2>
 * <ol>
 *   <li><b>Lead-in.</b> The head runs ahead of the contact point by
 *       {@link TowHeadGeometry#axialLead}, and Z swings to the wrap angle. For the first pass this
 *       rides on the preamble; for every pass after it, it is the second half of the previous
 *       turnaround.</li>
 *   <li><b>Traverse.</b> X and Y move together at the wrap angle, one G1 per profile segment. Z is
 *       already at angle and holds.</li>
 *   <li><b>Turnaround.</b> The index rotation, a full wrap, then the head flips through neutral to
 *       the opposite angle while X recenters and then re-leads for the return, and Y turns enough
 *       to keep the tow taut.</li>
 * </ol>
 *
 * <h2>What changed from v7</h2>
 * <ol>
 *   <li>{@code Dest_z} was hardcoded to 45; it is now the wrap angle (which is what v6 had, and
 *       what the roller actually needs). At a 45 degree wrap the output is unchanged.</li>
 *   <li>{@code Dest_x} was 0 (20 in v6); it is now the derived tow-head lead, applied on the head
 *       flip so the head recenters and then re-leads.</li>
 *   <li>Taper steps are 1 mm rather than 5 mm, sampled at the midpoint.</li>
 *   <li>The return traverse walks the profile properly reversed, instead of reusing the outbound
 *       section lengths with swapped perimeters.</li>
 * </ol>
 *
 * Everything else - the index formula, the layer constant, the pass count, the traverse rotation -
 * is v7's, untouched.
 */
public final class GcodeGenerator {

    /**
     * Sign of carriage travel toward the left-hand end of the machine - the end a program starts
     * from and parks at.
     *
     * <p>Generator v7's first pass emits negative X, and the first pass runs to the right. So
     * negative X is right, positive X is left. Everything that needs to know which way is which
     * should read it from here rather than guessing.
     */
    public static final int LEFT_X_SIGN = +1;

    private final WindingParameters params;

    /** Carriage position and envelope, tracked while emitting so the startup procedure can use it. */
    private double xPosition;
    private double minX;
    private double maxX;

    public GcodeGenerator(WindingParameters params) {
        this.params = params;
    }

    public static GcodeProgram generate(WindingParameters params) {
        return new GcodeGenerator(params).generate();
    }

    public GcodeProgram generate() {
        List<String> out = new ArrayList<>();
        TubeProfile profile = params.profile();
        xPosition = 0;
        minX = 0;
        maxX = 0;

        if (params.includeComments()) {
            emitHeaderComments(out);
        }
        emitPreamble(out);

        int direction = 1;
        int passCount = params.passCount();
        for (int pass = 0; pass < passCount; pass++) {
            if (params.includeComments()) {
                out.add(String.format("; ---- pass %d/%d, %s ----",
                        pass + 1, passCount, direction == 1 ? "-X" : "+X"));
            }
            emitTraverse(out, direction);
            emitTurnaround(out, direction, pass == passCount - 1);
            direction = -direction;
        }

        return new GcodeProgram(out, buildSummary(profile));
    }

    // ------------------------------------------------------------------ emit

    private void emitHeaderComments(List<String> out) {
        TubeProfile profile = params.profile();
        TowHeadGeometry head = params.towHead();
        out.add("; Berkeley Bike Builders tube winder");
        out.add("; X = carriage (mm), Y = mandrel rotation (deg), Z = tow head angle (deg)");
        out.add("; " + profile.description());
        out.add(String.format("; wrap angle %.3f deg | tow width %.3f mm | wall %.3f mm | F%s",
                params.wrapAngleDeg(), params.towWidthMm(), params.wallThicknessMm(),
                Num.fmt(params.feedrate())));
        out.add(String.format("; layers %d | passes %d (raw %.3f)",
                params.layers(), params.passCount(), params.passes()));
        if (head.isEnabled()) {
            out.add(String.format("; tow head offset %.1f mm horizontal, %.1f mm vertical from the "
                            + "mandrel axis",
                    head.offsetHorizontalMm(), head.offsetVerticalMm()));
            out.add(String.format("; axial lead %.3f mm near, %.3f mm far | free span %.3f mm near",
                    head.axialLead(profile.nearPerimeterMm(), params.wrapAngleDeg()),
                    head.axialLead(profile.farPerimeterMm(), params.wrapAngleDeg()),
                    head.freeSpan(profile.nearPerimeterMm(), params.wrapAngleDeg())));
        } else {
            out.add("; tow head offset compensation DISABLED (v7-compatible)");
        }
    }

    /**
     * v7's preamble was {@code G21 G91 F<feed>} then {@code G01 Y360 Z<wrap>}: units and incremental
     * mode, then an anchoring wrap with the head swung to angle. The only addition is the initial
     * X lead-in, so the head starts ahead of the contact point rather than over it.
     */
    private void emitPreamble(List<String> out) {
        out.add("G21 G91 F" + Num.fmt(params.feedrate()));

        int direction = 1;
        double startPerimeter = params.profile().nearPerimeterMm();
        double lead = leadAt(startPerimeter);
        move(out, xSign(direction) * lead, 360.0, zForDirection(direction));
    }

    /**
     * One traverse. Each profile segment becomes a single G1 whose X is the axial travel and whose
     * Y is the mandrel rotation that produces the wrap angle over that segment.
     *
     * <p>On a tapered tube the lead changes with radius, so each segment also carries the change in
     * lead since the previous one. On a straight tube every one of those corrections is zero and the
     * output is v7's, exactly.
     */
    private void emitTraverse(List<String> out, int direction) {
        TubeProfile profile = params.profile();
        List<TraverseSegment> segments = profile.segmentsForDirection(direction);
        int sign = xSign(direction);

        // The head is already leading by this much, from the preamble or the last turnaround.
        double previousLead = leadAt(profile.endPerimeterForDirection(-direction));

        for (TraverseSegment segment : segments) {
            double segmentLead = leadAt(segment.perimeterMm());
            double leadDelta = params.compensateTaperLead() ? segmentLead - previousLead : 0.0;
            double x = sign * (segment.lengthMm() + leadDelta);
            double y = segment.rotationDegrees(params.wrapAngleDeg());
            move(out, x, y, null);
            previousLead = segmentLead;
        }
    }

    /**
     * The turnaround, keeping v7's five moves and their Y totals.
     *
     * <pre>
     *   G1 Y&lt;index/2&gt;                      index rotation, first half
     *   G1 Y360                            full wrap at the end of the tube
     *   G1 X&lt;lead&gt; Y&lt;takeup&gt; Z&lt;-d*theta&gt;  head flips to neutral, X recenters over the contact point
     *   G1 X&lt;lead&gt; Y&lt;takeup&gt; Z&lt;-d*theta&gt;  head flips to the opposite angle, X leads for the return
     *   G1 Y&lt;index/2&gt;                      index rotation, second half
     * </pre>
     *
     * <p>v7 put its (zero) X moves on the index lines and left the Z lines pure. They are on the Z
     * lines here because that is where the motion belongs: the recenter happens <em>while</em> the
     * head swings through neutral, which is how you described it. Same number of lines, same Y and
     * Z totals.
     *
     * <p>After the final pass only the first half runs. There is no return pass to lead into, so
     * the head stops at neutral and the carriage stops over the contact point - which also means
     * the program ends with X back at its starting position and Z at zero, ready for the next job.
     */
    private void emitTurnaround(List<String> out, int direction, boolean lastPass) {
        double endPerimeter = params.profile().endPerimeterForDirection(direction);

        double indexHalf = params.indexAngleDeg(endPerimeter) / 2.0;
        double takeupHalf = takeupHalfDegrees(endPerimeter);
        double lead = leadAt(endPerimeter);

        // Both halves move X the same way: the first cancels the outbound lead, the second
        // establishes the return lead. xSign flips with direction, so both are +direction * lead.
        double x = direction * lead;
        double z = -direction * params.wrapAngleDeg();

        move(out, null, indexHalf, null);
        move(out, null, 360.0, null);
        move(out, x, takeupHalf, z);
        if (!lastPass) {
            move(out, x, takeupHalf, z);
        }
        move(out, null, indexHalf, null);
    }

    // --------------------------------------------------------------- helpers

    /** Sign of carriage travel for a pass direction. v7: {@code direction == 1} travels -X. */
    private static int xSign(int direction) {
        return -direction;
    }

    /** Head angle held during a pass in the given direction. */
    private double zForDirection(int direction) {
        return direction * params.wrapAngleDeg();
    }

    private double leadAt(double perimeterMm) {
        return params.towHead().axialLead(perimeterMm, params.wrapAngleDeg());
    }

    /**
     * Mandrel rotation per half-turnaround that keeps the tow taut while the head swings.
     * v7's hardcoded 90 unless {@code useComputedSlackTakeup} is set.
     */
    private double takeupHalfDegrees(double perimeterMm) {
        if (!params.useComputedSlackTakeup() || !params.towHead().isEnabled()) {
            return WindingParameters.FLIP_TAKEUP_DEG;
        }
        return params.towHead().slackTakeupDegrees(perimeterMm, params.wrapAngleDeg()) / 2.0;
    }

    /**
     * Emit one move, tracking how far the carriage strays from where the program started.
     *
     * <p>That envelope is what the startup procedure needs: park the head anywhere with
     * {@code -minXOffsetMm} of travel available in one direction and {@code maxXOffsetMm} in the
     * other, and the program cannot run out of rail.
     */
    private void move(List<String> out, Double x, Double y, Double z) {
        if (x != null) {
            xPosition += x;
            minX = Math.min(minX, xPosition);
            maxX = Math.max(maxX, xPosition);
        }
        out.add(g1(x, y, z));
    }

    /** Build a G1 line, dropping words that are null or round to zero. */
    private static String g1(Double x, Double y, Double z) {
        StringBuilder sb = new StringBuilder("G1");
        if (x != null && !Num.isZero(x)) {
            sb.append(" X").append(Num.fmt(x));
        }
        if (y != null && !Num.isZero(y)) {
            sb.append(" Y").append(Num.fmt(y));
        }
        if (z != null && !Num.isZero(z)) {
            sb.append(" Z").append(Num.fmt(z));
        }
        if (sb.length() == 2) {
            // Every word rounded away. Emit an explicit no-op rather than a bare "G1".
            return "G1 X0";
        }
        return sb.toString();
    }

    private GcodeProgram.Summary buildSummary(TubeProfile profile) {
        TowHeadGeometry head = params.towHead();
        boolean enabled = head.isEnabled();
        double theta = params.wrapAngleDeg();
        return new GcodeProgram.Summary(
                params.layers(),
                params.passes(),
                params.passCount(),
                profile.totalLengthMm(),
                enabled,
                enabled ? head.axialLead(profile.nearPerimeterMm(), theta) : 0.0,
                enabled ? head.axialLead(profile.farPerimeterMm(), theta) : 0.0,
                enabled ? head.freeSpan(profile.nearPerimeterMm(), theta) : 0.0,
                enabled ? head.slackTakeupDegrees(profile.nearPerimeterMm(), theta) : 0.0,
                takeupHalfDegrees(profile.nearPerimeterMm()),
                minX,
                maxX);
    }
}

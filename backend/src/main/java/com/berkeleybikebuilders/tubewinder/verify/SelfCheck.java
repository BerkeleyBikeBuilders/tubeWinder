package com.berkeleybikebuilders.tubewinder.verify;

import com.berkeleybikebuilders.tubewinder.WindingParameters;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeGenerator;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;
import com.berkeleybikebuilders.tubewinder.geometry.TowHeadGeometry;
import com.berkeleybikebuilders.tubewinder.geometry.TraverseSegment;
import com.berkeleybikebuilders.tubewinder.geometry.TubeProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free verification harness. Run it with:
 *
 * <pre>
 *   mvn -q compile
 *   java -cp target/classes com.berkeleybikebuilders.tubewinder.verify.SelfCheck
 * </pre>
 *
 * <p>Two groups of checks. The <b>parity</b> ones pin the arithmetic that came from Generator v7 -
 * traverse rotation, layer count, pass count, index angle - so those can't drift without someone
 * noticing. The <b>invariant</b> ones assert things that must be true of any correct program: the
 * carriage comes back where it started, the mandrel never reverses, the head only ever sits at plus
 * or minus the wrap angle or at neutral.
 *
 * <p>Each check maps one-to-one onto a JUnit test if you later add the dependency; it is written
 * this way so it runs with nothing but a JDK.
 */
public final class SelfCheck {

    private static final double EPS = 1e-6;

    // Spreadsheet CIRCULAR sheet: 700 mm long, 27 mm diameter, 45 degree wrap.
    private static final double PERIMETER = 2 * Math.PI * 13.5;
    private static final double LENGTH = 700.0;

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    private SelfCheck() {
    }

    public static void main(String[] args) {
        traverseRotationMatchesV7Formula();
        layerAndPassCountsMatchV7();
        indexAngleMatchesV7Formula();
        withOffsetDisabledTheTraverseIsExactlyV7();
        carriageReturnsToStartAfterAPairOfPasses();
        headAngleOnlySitsAtWrapAngleOrNeutral();
        mandrelOnlyTurnsOneWay();
        offsetCompensationChangesOnlyTheTurnaround();
        towHeadGeometryIsSelfConsistent();
        headInsideTheMandrelIsRejected();
        badWrapAnglesAreRejected();
        taperIsDiscretisedAtOneMillimetre();
        reverseTraverseWalksTheProfileBackwards();
        taperedTubeCarriageLandsWhereItStarted();

        System.out.printf("%n%d checks passed, %d failed%n", passed, failures.size());
        failures.forEach(f -> System.out.println("  FAIL " + f));
        if (!failures.isEmpty()) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------ v7 parity

    private static void traverseRotationMatchesV7Formula() {
        TraverseSegment segment = new TraverseSegment(LENGTH, PERIMETER);
        double expected = 360.0 * LENGTH * Math.tan(Math.toRadians(45)) / PERIMETER;
        near("traverse rotation matches v7 lin_sec", expected, segment.rotationDegrees(45), EPS);
        // Independent cross-check: the spreadsheet gets 2970.892 degrees for this tube.
        near("traverse rotation matches the spreadsheet", 2970.892, segment.rotationDegrees(45), 1e-3);
    }

    private static void layerAndPassCountsMatchV7() {
        WindingParameters p = base().build();
        near("layer count matches v7", 8, p.layers(), 0);
        double expected = 8 * (LENGTH * PERIMETER)
                / (6.5 * Math.sqrt(LENGTH * LENGTH
                        + Math.pow(LENGTH * Math.tan(Math.toRadians(45)), 2)));
        near("pass count matches v7", expected, p.passes(), EPS);
        near("emitted passes is the ceiling, as v7", Math.ceil(expected), p.passCount(), 0);
    }

    private static void indexAngleMatchesV7Formula() {
        WindingParameters p = base().build();
        double expected = 360.0 + 3.0 * 6.5 * Math.toDegrees(Math.atan(6.5 / PERIMETER));
        near("index angle matches v7 gant_rot", expected, p.indexAngleDeg(PERIMETER), EPS);
    }

    private static void withOffsetDisabledTheTraverseIsExactlyV7() {
        GcodeProgram program = GcodeGenerator.generate(base().towHead(TowHeadGeometry.none()).build());
        boolean found = program.lines().contains("G1 X-700 Y2970.892");
        check("offset off reproduces v7's traverse line verbatim", found);
    }

    // ------------------------------------------------------ invariants

    private static void carriageReturnsToStartAfterAPairOfPasses() {
        GcodeProgram program = GcodeGenerator.generate(base().build());
        if (program.summary().passCount() % 2 != 0) {
            check("carriage returns to start (skipped: odd pass count)", true);
            return;
        }
        double x = 0;
        for (double[] move : parse(program)) {
            x += move[0];
        }
        near("carriage returns to its starting position", 0.0, x, 1e-6);
    }

    private static void headAngleOnlySitsAtWrapAngleOrNeutral() {
        GcodeProgram program = GcodeGenerator.generate(base().build());
        double z = 0;
        boolean ok = true;
        for (double[] move : parse(program)) {
            z += move[2];
            double abs = Math.abs(z);
            if (abs > 1e-6 && Math.abs(abs - 45) > 1e-6) {
                ok = false;
                break;
            }
        }
        check("tow head only ever sits at +/- the wrap angle or neutral", ok);
    }

    private static void mandrelOnlyTurnsOneWay() {
        GcodeProgram program = GcodeGenerator.generate(base().build());
        boolean ok = parse(program).stream().allMatch(move -> move[1] >= -1e-9);
        check("mandrel never reverses", ok);
    }

    private static void offsetCompensationChangesOnlyTheTurnaround() {
        GcodeProgram with = GcodeGenerator.generate(base().build());
        GcodeProgram without = GcodeGenerator.generate(base().towHead(TowHeadGeometry.none()).build());
        if (with.lines().size() != without.lines().size()) {
            check("offset compensation leaves the line count alone", false);
            return;
        }
        int differing = 0;
        for (int i = 0; i < with.lines().size(); i++) {
            if (!with.lines().get(i).equals(without.lines().get(i))) {
                differing++;
            }
        }
        // One preamble lead-in, two head-flip lines per turnaround, one for the final turnaround.
        near("offset compensation touches only the lead-in and the head flips",
                1 + 2 * (with.summary().passCount() - 1) + 1, differing, 0);
    }

    // ------------------------------------------------------ geometry

    private static void towHeadGeometryIsSelfConsistent() {
        TowHeadGeometry head = TowHeadGeometry.current();
        double theta = 45;
        double lambda = head.tangentLength(PERIMETER);

        near("head distance to the axis", Math.hypot(50, 25), head.axisDistanceMm(), EPS);
        near("tangent length is sqrt(A^2 - R^2)",
                Math.sqrt(head.axisDistanceMm() * head.axisDistanceMm() - 13.5 * 13.5), lambda, 1e-9);
        near("lead * tan(theta) == tangent length",
                lambda, head.axialLead(PERIMETER, theta) * Math.tan(Math.toRadians(theta)), 1e-9);
        near("freeSpan * sin(theta) == tangent length",
                lambda, head.freeSpan(PERIMETER, theta) * Math.sin(Math.toRadians(theta)), 1e-9);
        near("hoop winding needs no lead", 0.0, head.axialLead(PERIMETER, 89.9999999), 1e-4);
    }

    private static void headInsideTheMandrelIsRejected() {
        check("a head inside the mandrel is rejected",
                throwsIllegalArgument(() -> new TowHeadGeometry(5, 5).tangentLength(PERIMETER)));
    }

    private static void badWrapAnglesAreRejected() {
        check("wrap angle 0 is rejected",
                throwsIllegalArgument(() -> base().wrapAngleDeg(0).build()));
        check("wrap angle 90 is rejected",
                throwsIllegalArgument(() -> base().wrapAngleDeg(90).build()));
    }

    // ------------------------------------------------------ taper

    private static void taperIsDiscretisedAtOneMillimetre() {
        TubeProfile profile = TubeProfile.tapered(100, 150, 20, 60, 30);
        near("taper produces 1 mm steps", 62, profile.forwardSegments().size(), 0);
        near("taper preserves total length", 110.0, profile.totalLengthMm(), EPS);
    }

    private static void reverseTraverseWalksTheProfileBackwards() {
        TubeProfile profile = TubeProfile.tapered(100, 150, 20, 60, 30);
        List<TraverseSegment> reverse = profile.reverseSegments();
        near("return pass starts with the far section's length", 30.0,
                reverse.get(0).lengthMm(), EPS);
        near("return pass starts at the far perimeter", 150.0,
                reverse.get(0).perimeterMm(), EPS);
        near("return pass ends with the near section's length", 20.0,
                reverse.get(reverse.size() - 1).lengthMm(), EPS);
        near("return pass ends at the near perimeter", 100.0,
                reverse.get(reverse.size() - 1).perimeterMm(), EPS);
    }

    private static void taperedTubeCarriageLandsWhereItStarted() {
        WindingParameters p = base()
                .profile(TubeProfile.tapered(104.922, 144, 16, 465.162, 16))
                .wrapAngleDeg(60)
                .wallThicknessMm(0.2)
                .build();
        GcodeProgram program = GcodeGenerator.generate(p);
        if (program.summary().passCount() % 2 != 0) {
            check("tapered carriage returns to start (skipped: odd pass count)", true);
            return;
        }
        double x = 0;
        for (double[] move : parse(program)) {
            x += move[0];
        }
        near("tapered tube carriage returns to its starting position", 0.0, x, 1e-6);
    }

    // ------------------------------------------------------ plumbing

    private static WindingParameters.Builder base() {
        return WindingParameters.builder()
                .profile(TubeProfile.straight(PERIMETER, LENGTH))
                .wrapAngleDeg(45)
                .towWidthMm(6.5)
                .wallThicknessMm(1.6)
                .feedrate(12500)
                .includeComments(false);
    }

    private static List<double[]> parse(GcodeProgram program) {
        List<double[]> moves = new ArrayList<>();
        for (String line : program.lines()) {
            if (!line.startsWith("G1")) {
                continue;
            }
            double[] move = new double[3];
            for (String word : line.split("\\s+")) {
                if (word.length() < 2) {
                    continue;
                }
                int index = switch (word.charAt(0)) {
                    case 'X' -> 0;
                    case 'Y' -> 1;
                    case 'Z' -> 2;
                    default -> -1;
                };
                if (index >= 0) {
                    move[index] = Double.parseDouble(word.substring(1));
                }
            }
            moves.add(move);
        }
        return moves;
    }

    private static boolean throwsIllegalArgument(Runnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static void near(String what, double expected, double actual, double tolerance) {
        check(String.format("%s (expected %s, got %s)", what, expected, actual),
                Math.abs(expected - actual) <= tolerance);
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  ok   " + what);
        } else {
            failures.add(what);
            System.out.println("  FAIL " + what);
        }
    }
}

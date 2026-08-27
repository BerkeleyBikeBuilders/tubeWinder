package com.berkeleybikebuilders.tubewinder.geometry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shape being wound, expressed as perimeter along the mandrel axis.
 *
 * <p>Every tube is treated as round, so perimeter and radius are interchangeable. A taper is
 * handled the way you described it: as a run of short constant-perimeter tubes butted end to end,
 * each wound at the same wrap angle. Generator v7 did this with 5 mm chunks; the default here is
 * 1 mm.
 *
 * <p>{@link #forwardSegments()} is the traverse from the near end to the far end.
 * {@link #reverseSegments()} is the return, which is the forward list reversed - <b>not</b> the
 * forward list with the end perimeters swapped, which is what v7 did. See {@link #tapered} for why
 * that mattered.
 */
public final class TubeProfile {

    /** Default taper discretisation. v7 used 5.0; you asked for 1 mm. */
    public static final double DEFAULT_TAPER_STEP_MM = 1.0;

    private final List<TraverseSegment> forward;
    private final String description;

    private TubeProfile(List<TraverseSegment> forward, String description) {
        if (forward.isEmpty()) {
            throw new IllegalArgumentException("A tube profile needs at least one segment");
        }
        this.forward = List.copyOf(forward);
        this.description = description;
    }

    /** A constant-perimeter tube - v7's "linear" case. */
    public static TubeProfile straight(double perimeterMm, double lengthMm) {
        return new TubeProfile(
                List.of(new TraverseSegment(lengthMm, perimeterMm)),
                String.format("straight: %.1f mm long, %.3f mm perimeter", lengthMm, perimeterMm));
    }

    /**
     * v7's "non_linear" case: a constant section, a tapered section, then a second constant section.
     *
     * <p>Note on the reverse traverse. v7 emitted the return pass as
     * {@code [length1 @ p1, taper, length3 @ p0]} - it swapped the perimeters but kept the section
     * lengths in their original order. Coming back from the {@code p1} end, the first section you
     * cross is {@code length3} (which really is at {@code p1}), not {@code length1}. When
     * {@code length1 == length3} the two are identical, which is presumably why it never showed up.
     * When they differ, the mandrel rotation for those sections is wrong. Reversing the discretised
     * segment list fixes it and leaves the equal-length case byte-for-byte unchanged.
     *
     * @param nearPerimeterMm   perimeter at the near end (v7's p0)
     * @param farPerimeterMm    perimeter at the far end (v7's p1)
     * @param nearLengthMm      length of the near constant section (v7's length_1)
     * @param taperLengthMm     length of the tapered section (v7's length_2)
     * @param farLengthMm       length of the far constant section (v7's length_3)
     * @param taperStepMm       taper discretisation step
     */
    public static TubeProfile tapered(double nearPerimeterMm,
                                      double farPerimeterMm,
                                      double nearLengthMm,
                                      double taperLengthMm,
                                      double farLengthMm,
                                      double taperStepMm) {
        if (taperStepMm <= 0) {
            throw new IllegalArgumentException("Taper step must be positive, got " + taperStepMm);
        }
        List<TraverseSegment> segments = new ArrayList<>();
        if (nearLengthMm > 0) {
            segments.add(new TraverseSegment(nearLengthMm, nearPerimeterMm));
        }
        if (taperLengthMm > 0) {
            int steps = Math.max(1, (int) Math.round(taperLengthMm / taperStepMm));
            double step = taperLengthMm / steps;
            for (int i = 0; i < steps; i++) {
                // Midpoint sampling. v7 sampled the leading edge of each chunk, which biases the
                // whole taper by half a step. At 1 mm steps the difference is small, but midpoint
                // costs nothing.
                double fraction = (i + 0.5) / steps;
                double perimeter = nearPerimeterMm + (farPerimeterMm - nearPerimeterMm) * fraction;
                segments.add(new TraverseSegment(step, perimeter));
            }
        }
        if (farLengthMm > 0) {
            segments.add(new TraverseSegment(farLengthMm, farPerimeterMm));
        }
        String description = String.format(
                "tapered: %.1f mm @ %.3f | %.1f mm taper %.3f->%.3f (%.2f mm steps) | %.1f mm @ %.3f",
                nearLengthMm, nearPerimeterMm, taperLengthMm, nearPerimeterMm, farPerimeterMm,
                taperStepMm, farLengthMm, farPerimeterMm);
        return new TubeProfile(segments, description);
    }

    /** Same as the six-argument form, using {@link #DEFAULT_TAPER_STEP_MM}. */
    public static TubeProfile tapered(double nearPerimeterMm,
                                      double farPerimeterMm,
                                      double nearLengthMm,
                                      double taperLengthMm,
                                      double farLengthMm) {
        return tapered(nearPerimeterMm, farPerimeterMm, nearLengthMm, taperLengthMm, farLengthMm,
                DEFAULT_TAPER_STEP_MM);
    }

    /** Arbitrary profile - the hook CAD import will use. */
    public static TubeProfile of(List<TraverseSegment> segments, String description) {
        return new TubeProfile(segments, description);
    }

    public List<TraverseSegment> forwardSegments() {
        return forward;
    }

    public List<TraverseSegment> reverseSegments() {
        List<TraverseSegment> reversed = new ArrayList<>(forward);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    /** Segments in traverse order for the given pass direction (see {@code GcodeGenerator}). */
    public List<TraverseSegment> segmentsForDirection(int direction) {
        return direction == 1 ? forwardSegments() : reverseSegments();
    }

    public double totalLengthMm() {
        return forward.stream().mapToDouble(TraverseSegment::lengthMm).sum();
    }

    public double nearPerimeterMm() {
        return forward.get(0).perimeterMm();
    }

    public double farPerimeterMm() {
        return forward.get(forward.size() - 1).perimeterMm();
    }

    /**
     * Perimeter used for the pass-count calculation. v7 used {@code p} for straight tubes and
     * {@code p0} for tapered ones; both are the near-end perimeter, so this preserves that.
     */
    public double referencePerimeterMm() {
        return nearPerimeterMm();
    }

    /** Perimeter at the end a pass in this direction finishes at - used for the turnaround. */
    public double endPerimeterForDirection(int direction) {
        return direction == 1 ? farPerimeterMm() : nearPerimeterMm();
    }

    /** Largest perimeter anywhere on the tube, for the tow-head clearance check. */
    public double maxPerimeterMm() {
        return forward.stream().mapToDouble(TraverseSegment::perimeterMm).max().orElseThrow();
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}

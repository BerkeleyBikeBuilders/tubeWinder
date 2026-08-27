package com.berkeleybikebuilders.tubewinder;

import com.berkeleybikebuilders.tubewinder.geometry.TowHeadGeometry;
import com.berkeleybikebuilders.tubewinder.geometry.TubeProfile;

/**
 * Everything the generator needs for one winding job.
 *
 * <p>Layer thickness and the index-angle formula are carried over from Generator v7 unchanged -
 * they are the numbers that have actually produced parts.
 */
public final class WindingParameters {

    /**
     * Cured thickness of one layer, in mm.
     *
     * <p>v7 wrote this as {@code layers = thickness * (8 / 1.6)}, i.e. 0.2 mm per layer. The
     * spreadsheet says 1.0 mm. You said follow the code, so 0.2 it is.
     */
    public static final double LAYER_THICKNESS_MM = 1.6 / 8.0;

    /**
     * Mandrel rotation accompanying each half of the head flip at a turnaround, in degrees.
     *
     * <p>Hardcoded at 90 in v7 (so 180 over a full turnaround). Its job is to keep the tow taut
     * while the head swings through neutral. The geometry says the exact figure is
     * {@link TowHeadGeometry#slackTakeupDegrees}; for a 27 mm mandrel at 45 degrees with the current
     * head offsets that comes out around 146, so v7's 180 is in the right neighbourhood but not
     * derived. Set {@link #useComputedSlackTakeup()} to switch.
     */
    public static final double FLIP_TAKEUP_DEG = 90.0;

    private final TubeProfile profile;
    private final double wrapAngleDeg;
    private final double towWidthMm;
    private final double wallThicknessMm;
    private final double feedrate;
    private final TowHeadGeometry towHead;
    private final boolean useComputedSlackTakeup;
    private final boolean compensateTaperLead;
    private final boolean includeComments;
    private final double layerThicknessMm;

    private WindingParameters(Builder b) {
        this.profile = require(b.profile, "profile");
        this.wrapAngleDeg = positive(b.wrapAngleDeg, "wrap angle");
        this.towWidthMm = positive(b.towWidthMm, "tow width");
        this.wallThicknessMm = positive(b.wallThicknessMm, "wall thickness");
        this.feedrate = positive(b.feedrate, "feedrate");
        this.towHead = b.towHead == null ? TowHeadGeometry.current() : b.towHead;
        this.useComputedSlackTakeup = b.useComputedSlackTakeup;
        this.compensateTaperLead = b.compensateTaperLead;
        this.includeComments = b.includeComments;
        this.layerThicknessMm = Double.isNaN(b.layerThicknessMm)
                ? LAYER_THICKNESS_MM
                : positive(b.layerThicknessMm, "layer thickness");

        if (wrapAngleDeg <= 0 || wrapAngleDeg >= 90) {
            throw new IllegalArgumentException(
                    "Wrap angle must be strictly between 0 and 90 degrees (from the mandrel axis); "
                            + "got " + wrapAngleDeg);
        }
        // Fail here rather than mid-program: the head has to clear the fattest part of the mandrel.
        towHead.tangentLength(profile.maxPerimeterMm());
    }

    public static Builder builder() {
        return new Builder();
    }

    public TubeProfile profile() {
        return profile;
    }

    public double wrapAngleDeg() {
        return wrapAngleDeg;
    }

    public double towWidthMm() {
        return towWidthMm;
    }

    public double wallThicknessMm() {
        return wallThicknessMm;
    }

    public double feedrate() {
        return feedrate;
    }

    public TowHeadGeometry towHead() {
        return towHead;
    }

    public boolean useComputedSlackTakeup() {
        return useComputedSlackTakeup;
    }

    /**
     * Whether to adjust X across a taper as the lead changes with mandrel radius.
     *
     * <p>Only has any effect on tapered tubes: on a straight tube the lead is constant and every
     * adjustment is zero.
     */
    public boolean compensateTaperLead() {
        return compensateTaperLead;
    }

    /**
     * Whether to emit {@code ;} header comments. On by default - they record the parameters that
     * produced the file, which is worth having when a part comes out wrong. GRBL ignores them.
     * Turn off if your controller is fussy.
     */
    public boolean includeComments() {
        return includeComments;
    }

    /**
     * Cured thickness of one layer for this job. Defaults to {@link #LAYER_THICKNESS_MM}, v7's
     * value; overridable because this is the number most likely to be wrong (assumption A3).
     */
    public double layerThicknessMm() {
        return layerThicknessMm;
    }

    /** Number of layers needed for the requested wall thickness. v7's formula, unchanged. */
    public long layers() {
        return Math.round(wallThicknessMm / layerThicknessMm);
    }

    /**
     * Total passes. v7's formula, unchanged:
     * {@code layers * (L * P) / (towWidth * sqrt(L^2 + (L tan theta)^2))}, using the overall tube
     * length and the near-end perimeter.
     */
    public double passes() {
        double length = profile.totalLengthMm();
        double perimeter = profile.referencePerimeterMm();
        double towPerPass = Math.sqrt(
                length * length
                        + Math.pow(length * Math.tan(Math.toRadians(wrapAngleDeg)), 2));
        return layers() * (length * perimeter) / (towWidthMm * towPerPass);
    }

    /**
     * Passes actually emitted. v7 ran {@code while current < passes} with a floating-point
     * {@code passes}, which is a ceiling - preserved here.
     *
     * <p>Worth knowing: this is not forced even, so a job can finish with the carriage at the
     * opposite end from where it started. The spreadsheet rounded up to an even number
     * ({@code ceiling(N, 2)}). Left as v7 has it; say the word and it becomes a one-line change.
     */
    public int passCount() {
        return (int) Math.ceil(passes());
    }

    /** Index rotation per turnaround, in degrees. v7's {@code gant_rot} formula, unchanged. */
    public double indexAngleDeg(double perimeterMm) {
        return 360.0 + 3.0 * towWidthMm * Math.toDegrees(Math.atan(towWidthMm / perimeterMm));
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static double positive(double value, String name) {
        if (!(value > 0)) {
            throw new IllegalArgumentException(name + " must be positive, got " + value);
        }
        return value;
    }

    public static final class Builder {
        private TubeProfile profile;
        private double wrapAngleDeg = Double.NaN;
        private double towWidthMm = Double.NaN;
        private double wallThicknessMm = Double.NaN;
        private double feedrate = Double.NaN;
        private TowHeadGeometry towHead;
        private boolean useComputedSlackTakeup = false;
        private boolean compensateTaperLead = true;
        private boolean includeComments = true;
        private double layerThicknessMm = Double.NaN;

        public Builder profile(TubeProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder wrapAngleDeg(double wrapAngleDeg) {
            this.wrapAngleDeg = wrapAngleDeg;
            return this;
        }

        public Builder towWidthMm(double towWidthMm) {
            this.towWidthMm = towWidthMm;
            return this;
        }

        public Builder wallThicknessMm(double wallThicknessMm) {
            this.wallThicknessMm = wallThicknessMm;
            return this;
        }

        public Builder feedrate(double feedrate) {
            this.feedrate = feedrate;
            return this;
        }

        public Builder towHead(TowHeadGeometry towHead) {
            this.towHead = towHead;
            return this;
        }

        public Builder useComputedSlackTakeup(boolean value) {
            this.useComputedSlackTakeup = value;
            return this;
        }

        public Builder compensateTaperLead(boolean value) {
            this.compensateTaperLead = value;
            return this;
        }

        public Builder includeComments(boolean value) {
            this.includeComments = value;
            return this;
        }

        /** Override the cured per-layer thickness. Leave unset to use v7's 0.2 mm. */
        public Builder layerThicknessMm(double value) {
            this.layerThicknessMm = value;
            return this;
        }

        public WindingParameters build() {
            return new WindingParameters(this);
        }
    }
}

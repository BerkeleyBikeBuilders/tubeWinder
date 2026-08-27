package com.berkeleybikebuilders.tubewinder.geometry;

/**
 * Geometry of the offset tow head.
 *
 * <p>Generator v7 assumed the tow was laid at exactly the point the tow head was over. It isn't:
 * the head sits off the mandrel, so the tow leaves the roller, crosses a free span, and only then
 * touches the mandrel. This class computes how far ahead of the contact point the head has to sit.
 *
 * <h2>Coordinate model</h2>
 *
 * Look down the mandrel axis, at the cross-section plane containing the payout point:
 *
 * <pre>
 *                        H  = payout point (roller exit)
 *                       /|
 *                      / |  offsetVertical
 *                     /  |
 *          ----------+---+----   offsetHorizontal
 *                  (axis)
 *
 *   A = hypot(offsetHorizontal, offsetVertical)   distance from payout point to MANDREL AXIS
 * </pre>
 *
 * <p><b>Both offsets are measured from the mandrel AXIS, not the mandrel surface.</b> If the numbers
 * you have are surface-referenced, add the mandrel radius to the appropriate component before
 * constructing this. See {@code README.md} - this is assumption A1 and needs confirming on the
 * new winder.
 *
 * <h2>Derivation</h2>
 *
 * A tow under tension leaves a cylinder along the tangent. Let the contact point be {@code C} and
 * the lay direction in the surface tangent plane be
 * {@code t = (cos theta) axial + (sin theta) circumferential}, with {@code theta} the wrap angle
 * measured from the mandrel axis (0 = axial, 90 = hoop).
 *
 * <p>Travelling a distance {@code s} from {@code C} along {@code t}:
 * <ul>
 *   <li>the axial coordinate advances by {@code s cos(theta)};</li>
 *   <li>in the cross-section plane you move {@code s sin(theta)} along the tangent line to the
 *       circle of radius {@code R}, which puts you at radius {@code sqrt(R^2 + (s sin theta)^2)}.</li>
 * </ul>
 *
 * <p>The head sits at radius {@code A}, so {@code s sin(theta) = sqrt(A^2 - R^2)}. Call that
 * quantity the <b>tangent length</b>:
 *
 * <pre>
 *   lambda(R) = sqrt(A^2 - R^2)          (planar tangent length, independent of wrap angle)
 *   freeSpan  = lambda / sin(theta)      (length of unsupported tow between roller and mandrel)
 *   axialLead = lambda / tan(theta)      (how far the head leads the contact point, along X)
 * </pre>
 *
 * <p>Sanity checks: at {@code theta = 90} (hoop winding) the lead is zero, which is right - the tow
 * goes straight around and the head does not need to run ahead. As {@code theta} approaches 0 the
 * lead grows without bound, which is also right: a nearly axial tow paid out from a standoff has a
 * very long shadow. That is a real physical limit of the machine, not a bug in the math.
 *
 * <h2>Slack takeup</h2>
 *
 * At neutral (head square to the mandrel, Z = 0) the free span is its shortest, {@code A - R}.
 * At the wrap angle it is {@code lambda / sin(theta)}, which is longer. Going into a pass that extra
 * length is paid out from the spool; coming out of a pass at the turnaround it has to be taken up,
 * which is what the mandrel rotation during the head flip is for.
 *
 * <p>{@link #slackTakeupDegrees} computes the rotation that exactly absorbs it. Generator v7 uses a
 * hardcoded 90 degrees per half-turnaround (180 total) instead. The generator keeps v7's constant by
 * default - see {@code WindingParameters#useComputedSlackTakeup()} to switch to the computed value.
 */
public final class TowHeadGeometry {

    private final double offsetHorizontalMm;
    private final double offsetVerticalMm;

    /** Distance from the payout point to the mandrel axis, in the cross-section plane. */
    private final double axisDistanceMm;

    /** False for {@link #none()}, which reproduces Generator v7's zero-offset behaviour. */
    private final boolean enabled;

    public TowHeadGeometry(double offsetHorizontalMm, double offsetVerticalMm) {
        this(offsetHorizontalMm, offsetVerticalMm, true);
    }

    private TowHeadGeometry(double offsetHorizontalMm, double offsetVerticalMm, boolean enabled) {
        if (offsetHorizontalMm < 0 || offsetVerticalMm < 0) {
            throw new IllegalArgumentException("Tow head offsets must be non-negative");
        }
        if (enabled && offsetHorizontalMm == 0 && offsetVerticalMm == 0) {
            throw new IllegalArgumentException(
                    "Tow head offset cannot be zero in both axes; use TowHeadGeometry.none() to "
                            + "disable offset compensation entirely");
        }
        this.offsetHorizontalMm = offsetHorizontalMm;
        this.offsetVerticalMm = offsetVerticalMm;
        this.axisDistanceMm = Math.hypot(offsetHorizontalMm, offsetVerticalMm);
        this.enabled = enabled;
    }

    /** The winder as currently specced: 50 mm horizontal, 25 mm vertical. Provisional. */
    public static TowHeadGeometry current() {
        return new TowHeadGeometry(50.0, 25.0);
    }

    /** No offset compensation - reproduces Generator v7's original behaviour exactly. */
    public static TowHeadGeometry none() {
        return new TowHeadGeometry(0.0, 0.0, false);
    }

    /** Whether offset compensation is active. */
    public boolean isEnabled() {
        return enabled;
    }

    public double offsetHorizontalMm() {
        return offsetHorizontalMm;
    }

    public double offsetVerticalMm() {
        return offsetVerticalMm;
    }

    public double axisDistanceMm() {
        return axisDistanceMm;
    }

    /** Mandrel radius for a given cross-section perimeter. All tubes are treated as round. */
    public static double radiusFromPerimeter(double perimeterMm) {
        return perimeterMm / (2.0 * Math.PI);
    }

    /**
     * Planar tangent length from the payout point to the mandrel of the given perimeter.
     * Independent of wrap angle.
     */
    public double tangentLength(double perimeterMm) {
        if (!enabled) {
            return 0.0;
        }
        double r = radiusFromPerimeter(perimeterMm);
        if (axisDistanceMm <= r) {
            throw new IllegalArgumentException(String.format(
                    "Tow head is inside the mandrel: head is %.2f mm from the axis but the mandrel "
                            + "radius is %.2f mm (perimeter %.2f mm). Check the tow head offsets.",
                    axisDistanceMm, r, perimeterMm));
        }
        return Math.sqrt(axisDistanceMm * axisDistanceMm - r * r);
    }

    /**
     * How far ahead of the contact point the tow head must sit, along the mandrel axis.
     *
     * @param perimeterMm    local cross-section perimeter
     * @param wrapAngleDeg   wrap angle from the mandrel axis, exclusive of 0 and 90
     */
    public double axialLead(double perimeterMm, double wrapAngleDeg) {
        requireUsableWrapAngle(wrapAngleDeg);
        return tangentLength(perimeterMm) / Math.tan(Math.toRadians(wrapAngleDeg));
    }

    /** Length of unsupported tow between the roller and the mandrel during a pass. */
    public double freeSpan(double perimeterMm, double wrapAngleDeg) {
        requireUsableWrapAngle(wrapAngleDeg);
        return tangentLength(perimeterMm) / Math.sin(Math.toRadians(wrapAngleDeg));
    }

    /** Free span with the head neutral (Z = 0), i.e. the shortest it ever gets. */
    public double neutralSpan(double perimeterMm) {
        return enabled ? axisDistanceMm - radiusFromPerimeter(perimeterMm) : 0.0;
    }

    /** Extra tow length held in the free span at the wrap angle, versus neutral. */
    public double slackMm(double perimeterMm, double wrapAngleDeg) {
        return freeSpan(perimeterMm, wrapAngleDeg) - neutralSpan(perimeterMm);
    }

    /**
     * Mandrel rotation, in degrees, that exactly takes up {@link #slackMm} over a full turnaround.
     * Generator v7 hardcodes 180 degrees (2 x 90) for this; this is what the geometry actually asks
     * for.
     */
    public double slackTakeupDegrees(double perimeterMm, double wrapAngleDeg) {
        return 360.0 * slackMm(perimeterMm, wrapAngleDeg) / perimeterMm;
    }

    private static void requireUsableWrapAngle(double wrapAngleDeg) {
        if (wrapAngleDeg <= 0 || wrapAngleDeg >= 90) {
            throw new IllegalArgumentException(
                    "Wrap angle must be strictly between 0 and 90 degrees (measured from the "
                            + "mandrel axis); got " + wrapAngleDeg);
        }
    }
}

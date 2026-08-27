package com.berkeleybikebuilders.tubewinder.geometry;

/**
 * One straight chunk of a single traverse: travel {@code lengthMm} along the mandrel axis while the
 * local cross-section perimeter is treated as constant at {@code perimeterMm}.
 *
 * <p>This is the unit the G-code generator consumes. A constant-diameter tube is one segment; a
 * tapered tube is a run of short segments approximating the taper. Reversing a list of these gives
 * the return traverse, correctly, with each sub-perimeter in the right place.
 *
 * <p>When CAD import lands (roadmap item 4) it produces one of these lists and nothing downstream
 * has to change.
 */
public record TraverseSegment(double lengthMm, double perimeterMm) {

    public TraverseSegment {
        if (lengthMm <= 0) {
            throw new IllegalArgumentException("Segment length must be positive, got " + lengthMm);
        }
        if (perimeterMm <= 0) {
            throw new IllegalArgumentException("Segment perimeter must be positive, got " + perimeterMm);
        }
    }

    /** Mandrel radius over this segment. */
    public double radiusMm() {
        return TowHeadGeometry.radiusFromPerimeter(perimeterMm);
    }

    /**
     * Mandrel rotation, in degrees, for this segment at the given wrap angle.
     *
     * <p>Unchanged from Generator v7's {@code lin_sec}:
     * {@code Y = 360 * length * tan(theta) / perimeter}. This is the one piece of v7 that agrees
     * exactly with the spreadsheet model, and it is correct - the circumferential surface distance
     * travelled is {@code length * tan(theta)}, and dividing by the perimeter converts it to turns.
     */
    public double rotationDegrees(double wrapAngleDeg) {
        return 360.0 * lengthMm * Math.tan(Math.toRadians(wrapAngleDeg)) / perimeterMm;
    }
}

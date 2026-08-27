# Tube Winder Backend

G-code generation for the Berkeley Bike Builders filament winder. Java 17, Maven, no UI
dependencies - it is a library plus a CLI, so it drops into a UGS-based application later without
being untangled first.

This is Generator v7 with the tow-head offset added. v7's pass structure, index scheme, layer
constant and pass arithmetic are carried over unchanged, because those are the numbers that have
actually produced parts.

## Where this lives

`tubeWinder/backend/` - one module of the winder repo, alongside `ui`. The parent `pom.xml` at the
repo root builds both; `mvn package` from here builds this module alone.

## Build and run

```bash
mvn package
java -jar target/tubewinder-backend-0.1.0-SNAPSHOT.jar     # interactive, like v7

# or scripted:
java -jar target/tubewinder-backend-0.1.0-SNAPSHOT.jar \
    type=straight perimeter=84.823 length=700 wrap=45 tow=6.5 wall=1.6 feed=12500 out=tube.gcode
```

Optional keys: `headH` (default 50), `headV` (default 25), `taperStep` (default 1),
`offset=off` to disable tow-head compensation, `comments=off`, `takeup=computed`.

Verification, no dependencies needed:

```bash
mvn -q compile
java -cp target/classes com.berkeleybikebuilders.tubewinder.verify.SelfCheck
```

## Layout

| File | What it holds |
| --- | --- |
| `geometry/TowHeadGeometry` | The offset math. All of it. |
| `geometry/TraverseSegment` | One chunk of travel at a constant perimeter, and its mandrel rotation. |
| `geometry/TubeProfile` | The mandrel shape as a list of segments. Where CAD import will plug in. |
| `WindingParameters` | Job inputs, plus v7's layer/pass/index formulas. |
| `gcode/GcodeGenerator` | Emits the program. |
| `gcode/GcodeProgram` | Lines plus a summary, and file writing. |
| `cli/GeneratorCli` | Interactive and scripted front ends. |
| `verify/SelfCheck` | 26 checks: v7 parity and machine invariants. |

## Axes

| Axis | Meaning |
| --- | --- |
| X | Carriage travel along the mandrel, mm |
| Y | Mandrel rotation, degrees (geared 1 unit = 1 degree) |
| Z | Tow head angle, degrees. Zero is neutral; during a pass it holds at the wrap angle so the roller stays square to the tow |

Everything is `G21 G91` - millimetres, incremental - as v7 was.

Wrap angle is measured **from the mandrel axis**: 0 would be axial, 90 would be hoop. Both are
rejected; the geometry degenerates at each end.

## The tow-head offset

The thing v7 assumed away. The head sits off the mandrel, so tow leaves the roller, crosses a free
span, and only then touches the mandrel - the contact point trails the head.

A tow under tension leaves a cylinder along the tangent. Put the contact point at `C` and travel a
distance `s` along the lay direction `t = (cos θ) axial + (sin θ) circumferential`:

- the axial coordinate advances by `s·cos θ`
- in the cross-section plane you move `s·sin θ` along the tangent line to the circle of radius `R`,
  putting you at radius `sqrt(R² + (s sin θ)²)`

The head sits at radius `A` from the axis, so `s·sin θ = sqrt(A² − R²)`. Call that the tangent
length:

```
lambda(R) = sqrt(A² − R²)        planar tangent length, independent of wrap angle
freeSpan  = lambda / sin(θ)      unsupported tow between roller and mandrel
axialLead = lambda / tan(θ)      how far the head leads the contact point along X
```

Sanity: at θ = 90 (hoop) the lead is zero, which is right. As θ approaches 0 the lead grows without
bound, which is also right - a nearly axial tow paid out from a standoff has a very long shadow.
That is a real limit of the machine, not a bug.

For a 27 mm mandrel at 45 degrees with the current offsets: `lambda` = 54.25 mm, lead = 54.25 mm,
free span = 76.72 mm.

### Where it gets applied

```
preamble           head runs ahead by the lead, Z swings to the wrap angle
traverse           X and Y move together; Z holds at angle
turnaround         index, full wrap, then:
                     line 1  X recenters over the contact point while Z returns to neutral
                     line 2  X leads for the return while Z swings to the opposite angle
                   index
```

Both turnaround X moves are `+direction × lead`: the first cancels the outbound lead, the second
establishes the return lead.

After the last pass only line 1 runs - there is no return pass to lead into - so the program ends
with the head at neutral and the carriage back at its starting position.

## What changed from v7

1. **Z follows the wrap angle.** v7 hardcoded `Dest_z = 45`; v6 had `Dest_z = wrap_angle`, which is
   what the roller actually needs. At a 45 degree wrap the output is unchanged, which is presumably
   why the hardcode survived.
2. **X carries the tow-head lead.** `Dest_x` was 0 in v7 and a hardcoded 20 in v6. It is now
   derived, and it sits on the head-flip lines rather than the index lines, because the recenter
   happens *while* the head swings through neutral.
3. **Taper steps are 1 mm**, sampled at the midpoint rather than the leading edge.
4. **The return traverse walks the profile properly reversed.** v7 emitted the return as
   `[length_1 @ p1, taper, length_3 @ p0]` - perimeters swapped, section lengths left in outbound
   order. Coming back from the `p1` end the first section you cross is `length_3`, not `length_1`.
   Identical when the end sections are equal length; wrong when they are not.
5. **The file is written once.** v7 opened the output in append mode per line, so re-running with
   the same filename concatenated the new program onto the old one.

The index formula, layer constant, pass count and traverse rotation are v7's, untouched.

## Verification

`SelfCheck` runs 26 checks - v7 parity on all four inherited formulas, plus invariants (the carriage
returns to its starting position, the mandrel never reverses, the head only ever sits at ±θ or
neutral).

Beyond that, the generator was diffed against v7 itself. Running v7's
`linear_terminal(84.823, 700, 45, 1.6, 6.5, 12500)` and this backend with `offset=off comments=off`
gives **444 of 445 moves identical**. The one difference is the last move: v7 stops mid-flip with
the head still cocked at 45 degrees; this parks it at neutral.

## Carriage envelope

The summary reports how far the carriage strays from where the program starts - for the 700 mm tube
at 45 degrees, `-754.25 mm to 54.25 mm`. That is the tube length plus the tow head lead in the
first-pass direction, and the lead alone the other way. The UI's startup procedure uses it to decide
where the head has to be parked.

`GcodeGenerator.LEFT_X_SIGN` records which way is which: v7's first pass emits negative X and runs to
the right, so negative X is right and positive X is left.

## Assumptions that need confirming

- **A1 - the offsets are measured from the mandrel AXIS**, not the surface. 50 mm horizontal and
  25 mm vertical put the payout point 55.90 mm from the axis. If your numbers are
  surface-referenced, add the mandrel radius before constructing `TowHeadGeometry`. This is the one
  that most changes the output.
- **A2 - turnaround takeup is v7's hardcoded 90 degrees per half.** The geometry says the rotation
  that exactly absorbs the slack is 145.6 degrees total for the 27 mm tube at 45 degrees, against
  v7's 180. Right neighbourhood, not derived. `takeup=computed` switches to the derived value; left
  off by default because v7's number has made parts.
- **A3 - layer thickness is 0.2 mm**, from v7's `thickness * (8 / 1.6)`. The spreadsheet says
  1.0 mm. Following the code, as agreed. A measurement off a scrap part settles it.
- **A4 - the index formula is v7's**, kept verbatim on your word that the spacing looks right. It
  multiplies millimetres by degrees, so it will not survive a change of units or tow width
  gracefully. Worth re-deriving once there is a part to measure against.
- **A5 - pass count is not forced even.** v7 took the ceiling of a fractional pass count, so a job
  can finish with the carriage at the opposite end from where it started. The spreadsheet rounded up
  to an even number. Left as v7 has it; one line to change.

## Next

`TubeProfile.of(List<TraverseSegment>, String)` is the seam for CAD import. Anything that can turn a
file into a perimeter-versus-position list feeds the existing generator with nothing downstream
changing.

# Tube Winder UI

The desktop application. Swing, Java 17, no third-party dependencies - its only dependency is the
`backend` module.

## Running it

```bash
mvn package                        # from the repo root, builds backend then ui

java -cp "ui/target/classes:backend/target/classes" \
     com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp
```

On Windows the classpath separator is `;`:

```
java -cp "ui\target\classes;backend\target\classes" com.berkeleybikebuilders.tubewinder.ui.TubeWinderApp
```

## Layout

```
+--------------------------------------------------------------+
| toolbar: port baud connect | startup run pause stop | home zero |
+---------------------------+----------------------------------+
| Part specification  (gear)|                                  |
| [ Parameters | Part file ]|            visualizer            |
|   tube type, dimensions   |            (ours, later)         |
|   wrap angle, wall        |                                  |
|   [Slice]                 |                                  |
+---------------------------+                                  |
| [ Jog controller | Terminal ]                                |
+---------------------------+----------------------------------+
| status: message                        X ..  Y ..  Z ..      |
+--------------------------------------------------------------+
```

Two deliberate substitutions against UGS Classic:

- **UGS's file-open panel becomes the part specification.** UGS loads a G-code file someone else
  made; here the program is generated on the spot from the tube you are about to wind. Two tabs:
  **Parameters**, the form that has specified every wind so far, and **Part file**, where a CAD file
  will go once roadmap item 4 lands. The gear sits above both, because machine settings are neither
  per-tube nor per-method.
- **UGS's OpenGL toolpath canvas becomes our visualizer slot.** Empty for now, drawing a grid so the
  space reads as a viewport, and printing the last program's summary so it is not wasted.

Everything else is theirs because it works: the toolbar arrangement, the jog controls, the coloured
console, the position readout along the bottom.

A note on words: producing G-code from a tube is **slicing**, and the result is a **sliced program**.
The button says Slice.

Under it, **Download** writes the sliced program to `~/Downloads/gcode.txt` with no dialog:
two clicks from parameters to a file a sender can open. It overwrites the previous `gcode.txt`
rather than piling up numbered copies, and says so in the terminal when it does, so a stale file
cannot be mistaken for the current one. If there is no Downloads folder it falls back to the home
folder. `File > Save G-code...` is still there when a specific path is wanted.

## Startup and alignment

The sun in the toolbar. Same idea as a 3D printer finding itself on power-up, and **Run stays
disabled until it has been done** - the machine should not be asked to wind while it is guessing
where it is.

Five steps, roughly a second apart, narrated in the terminal:

1. **Exercise X** - sweep the carriage both ways and return, proving it travels freely.
2. **Calibrate Y** - index the mandrel axis and zero it.
3. **Calibrate Z** - sweep the tow head both ways and return it to neutral.
4. **Park** - put the head at the left-hand start and set work zero there.
5. **Hand over to the operator** - mount the mandrel, attach the tow 10 mm in from its left-hand end.

Parking is not a fixed spot, which is why the procedure needs a sliced program first. It works out
the rail the program actually needs on each side of the parked position - for the 700 mm tube at 45
degrees, 754.2 mm to the right and 54.2 mm to the left. The 754.2 is the tube plus the tow head lead;
the 54.2 is the lead alone, which is how far past the start the head swings on the return.

Against the rail length from settings, that gives the parking position: the spare rail is split
evenly either side, so on a 1000 mm rail the head parks 150 mm from the left end with 191.5 mm to
spare. A program that cannot fit at all is refused before the procedure starts, naming the numbers.

Slicing a bigger tube afterwards does not silently invalidate this: Run compares the new program's
envelope against what the machine was parked for and refuses if it needs more room.

Which way is left: Generator v7's first pass emits negative X and runs to the right, so **negative X
is right and positive X is left**. That lives in `GcodeGenerator.LEFT_X_SIGN` so nothing has to guess
- if it turns out to be backwards on the machine, flipping that constant is the whole fix.

Alignment is cleared by disconnecting and by a soft reset, since after either the controller's idea
of position is not to be trusted.

## Swing, not JavaFX

UGS ships both (`ugs-classic` and `ugs-platform` are Swing; `ugs-fx` is newer and less complete).
Swing is where UGS's mature UI lives, so that is what there is most to borrow from - and it needs
nothing beyond the JDK.

One departure: UGS lays its jog buttons out as a cartesian cross, which is right for an XYZ mill. On
this machine only X is linear; Y and Z are rotations in degrees. A compass rose would imply a
spatial relationship that isn't there, so the jog panel is one labelled row per axis with its units
shown, and separate step sizes for linear and rotary.

## The part file tab

Nothing reads a part yet, and the panel says so rather than implying otherwise - you can pick or
drop a file, it is remembered and reported to the terminal, and Generate stays disabled.

What it will do: pull the mandrel's axis and radius profile r(z) out of the file, turn that into a
`TubeProfile` - the same list of short constant-perimeter segments the taper already produces - and
hand it to the same generator. Everything downstream of the profile is written, which is why this is
a smaller job than it looks. `TubeProfile.of(List<TraverseSegment>, String)` is the seam.

## Settings, and what stays put

The gear above the tab bar opens the machine settings: tow width, cured layer thickness, feedrate,
rail length, tow head offsets, taper step, generator options, serial port. These are the things that do
not change between winds, so the parameters form only ever asks for what is actually different about
this tube.

Stored as plain properties at `~/.tubewinder/settings.properties` - readable, diffable, and easy to
copy between machines in the shop.

Layer thickness is settable here rather than baked in, because it is assumption A3 in the backend
README and the number most likely to be wrong.

## The machine connection

Everything that touches the winder goes through the `WinderConnection` interface - toolbar, jog
panel and terminal are written against it and nothing else.

The only implementation today is `SimulatedConnection`. It does **not** open a serial port. What it
does do is behave like the machine will: walk a generated program line by line, integrate the moves
into the position readout, echo each line to the terminal, and flag anything it cannot parse as an
error. Every message it emits says it is simulated, so nobody mistakes it for a connected winder.

That makes the whole UI exercisable now - toolbar states, jog enablement, DRO, progress, the error
path - and it makes the shape of the real thing obvious. When the sender is real, whether that is a
serial GRBL client written here or UGS's `BackendAPI` borrowed from `ugs-core`, it implements
`WinderConnection` and the UI does not change.

`SimulatedConnection` is where roadmap items 1 and 5 surface. The startup procedure lives there, and
its steps are the ones the real machine will run - the difference is that today they narrate rather
than move anything. The position it integrates is exactly the value an encoder would be compared
against, which is where item 5 starts.

## Files

| File | What it does |
| --- | --- |
| `TubeWinderApp` | Entry point, look and feel. |
| `MainWindow` | Assembles the layout, routes connection events, owns the current program. |
| `WinderToolBar` | The machine toolbar. |
| `SpecificationPanel` | Title, gear, and the Parameters / Part file tabs. |
| `ParametersPanel` | Per-wind parameters, generate, summary. |
| `PartFilePanel` | Part file drop target. Records a file; reads nothing yet. |
| `SettingsDialog` | The gear: everything that stays put. |
| `MachineSettings` | Those values, and their properties file. |
| `JogControlPanel` | Manual axis control. |
| `TerminalPanel` | Coloured console. |
| `VisualizerPanel` | The slot the wrap preview goes in. |
| `StatusBar` | Position readout. |
| `WinderConnection` | The interface everything machine-facing is written against. |
| `SimulatedConnection` | The stand-in. |
| `Icons` | Toolbar icons drawn with Java2D, no image assets. |
| `ThinScrollBar` | Flat grey scroll bars, no arrow buttons. |

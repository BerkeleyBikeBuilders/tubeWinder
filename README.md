[![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)

# tubeWinder

A full G-code slicer for the Berkeley Bike Builders CFRP filament winder, with a desktop app to
drive it. Enter a tube's dimensions, slice, run the startup procedure, wind.

Two modules: [`backend`](backend/README.md) does the slicing, [`ui`](ui/README.md) is the app.

## Opening it

You need a JDK 17 or newer. If you have IntelliJ you already do; otherwise:

```
winget install --id EclipseAdoptium.Temurin.21.JDK -e
```

Then **double-click `TubeWinder.bat`**, at the top of this folder. That is the whole thing.

It compiles itself when it needs to: the first run after a clone, and any run where a source file is
newer than the jar. An up-to-date launch is instant, a rebuild takes a couple of seconds. You never
have to remember to build after a pull.

**Optional, once:** put an icon on the desktop, so you never touch a terminal again.

```
powershell -ExecutionPolicy Bypass -File .\tools\create-desktop-shortcut.ps1
```

The shortcut does the same build-if-needed check, with no console window at all.

If a compile fails, run `./tools/build-jar.sh` from Git Bash to see the errors.

### If it says you need to update Java

Windows usually has an old Java 8 launcher in `System32`, at the front of `PATH`, left behind by
some installer years ago. The launcher version-checks every Java it finds and skips anything below
17, so this should not happen; if it does, ask it what it can see:

```
powershell -ExecutionPolicy Bypass -File .\tools\launch.ps1 -Diagnose
```

It lists every Java installation, its version, and which one it picked.

### Giving it to someone else

Hand them [`release/TubeWinder-win64.zip`](release/README.md). They extract the folder and
double-click `TubeWinder.bat`. Nothing to install, no Java needed: a complete Java 21 runtime is
inside the zip, the same way UGS ships its own. About 50 MB.

Refresh it after changes with:

```
powershell -ExecutionPolicy Bypass -File .\tools\make-bundle.ps1
```

It does not update itself, so rebuild it whenever the club should get changes.

### Building a native .exe instead

```
powershell -ExecutionPolicy Bypass -File .\tools\package-app.ps1
```

Builds `dist\TubeWinder\TubeWinder.exe`, a real Windows application rather than a folder with a
`.bat` in it. Same idea as the zip above, slightly more polished, but it needs a full JDK with
`jpackage` on the machine doing the building. The runtime inside IntelliJ usually has no `jpackage`;
the script says so if that is what you have. The zip is the one to use until that matters.

### Or from IntelliJ

`File > Open` this folder, set the project SDK to 17+, then run
`ui/src/main/java/com/berkeleybikebuilders/tubewinder/ui/TubeWinderApp.java`.

## Using it

1. **Gear icon**: set the things that never change - rail length, tow width, layer thickness,
   feedrate, tow head offsets.
2. **Parameters**: this tube's dimensions and wrap angle.
3. **Slice**, then **Download** to drop `gcode.txt` in your downloads folder.
4. **Connect**, then the **sun** to run startup and alignment. Mount the mandrel when it asks.
5. **Run**.

The winder has three axes, all incremental, all `G21 G91`:

| Axis | Meaning |
| --- | --- |
| X | Carriage along the mandrel, mm. Negative is right, positive is left |
| Y | Mandrel rotation, degrees |
| Z | Tow head angle, degrees. Holds at the wrap angle so the roller stays square to the tow |

The sender is not real yet: connecting runs a simulator that narrates what the machine would do.

## Roadmap

1. Sensors and encoders: slip correction, absolute positioning, mandrel detection.
   *(Startup procedure in place and gating Run; simulated, no sensors yet.)*
2. One program, sender included, with a wrap visualizer. *(UI done; sender simulated.)*
3. Tow head offset in the toolpath. *(Done.)*
4. Read part files instead of typing dimensions. *(UI slot in place, no geometry extraction yet.)*
5. Data loopback: the machine corrects itself mid-wind.

Generator v7, the Python in `prev versions/`, is the source of truth the backend was ported from.
What changed and what still needs measuring is in [`backend/README.md`](backend/README.md).

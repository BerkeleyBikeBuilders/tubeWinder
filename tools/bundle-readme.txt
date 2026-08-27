Tube Winder
Berkeley Bike Builders CFRP filament winder: G-code slicer and machine control.

TO RUN
    1. Extract this whole folder somewhere you can find it again.
       Do not run it from inside the zip.
    2. Double-click TubeWinder.bat.

That is all. Nothing needs to be installed, and no Java is required on your machine:
the runtime folder next to this file is a complete Java 21 runtime, the same way UGS
ships its own.

Keep the folder together. TubeWinder.bat, TubeWinder.jar and runtime\ all have to stay
side by side.

USING IT
    Gear icon    Values that never change: rail length, tow width, layer thickness,
                 feedrate, tow head offsets. Set these once.
    Parameters   This tube's dimensions and wrap angle.
    Slice        Produces the G-code. Download writes it to your downloads folder
                 as gcode.txt.
    Sun icon     Startup and alignment. Run this before winding. It parks the tow head
                 and tells you where to mount the mandrel.
    Run          Winds the part.

The sender is not connected to real hardware yet. Connecting runs a simulator that
narrates what the machine would do, so everything is safe to click.

SOURCE
    https://github.com/BerkeleyBikeBuilders/tubeWinder

The bundled runtime is Eclipse Temurin 21.0.5+11, redistributed under GPLv2 with the
Classpath Exception. Its licence files are in runtime\legal.

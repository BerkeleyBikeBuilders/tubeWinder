package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.gcode.GcodeGenerator;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stand-in for the real machine.
 *
 * <p>It does not open a serial port. What it does do is behave like the machine will: it walks a
 * generated program line by line, integrates the moves into a position readout, echoes each line to
 * the terminal, and flags anything it cannot parse as an error. That makes the whole UI - toolbar
 * states, jog enablement, DRO, progress, terminal - exercisable now, and it makes the shape of the
 * real implementation obvious.
 *
 * <p>Every message it emits says so, so nobody mistakes it for a connected winder.
 */
final class SimulatedConnection implements WinderConnection {

    /** G-code words we understand. Anything else in a G1 line is reported as an error. */
    private static final Pattern WORD = Pattern.compile("([A-Za-z])(-?\\d*\\.?\\d+)");

    /** Lines pushed per timer tick. Fast enough to watch, slow enough to read. */
    private static final int LINES_PER_TICK = 6;
    private static final int TICK_MS = 40;

    /** One alignment step per tick, slow enough that the operator can follow along. */
    private static final int ALIGN_TICK_MS = 900;

    /** How far the carriage sweeps each way while proving the X axis moves. */
    private static final double X_SWEEP_MM = 25;

    /** The tow is attached this far in from the end of the mandrel. */
    static final double TOW_ATTACH_INSET_MM = 10;

    private final List<Listener> listeners = new ArrayList<>();

    private State state = State.DISCONNECTED;
    private double x;
    private double y;
    private double z;

    private List<String> queue = List.of();
    private int cursor;
    private Timer timer;

    private boolean aligned;
    private double alignedClearanceRightMm;
    private double alignedClearanceLeftMm;
    private List<Runnable> alignSteps = List.of();
    private int alignStep;
    private Timer alignTimer;

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public State state() {
        return state;
    }

    @Override
    public double[] position() {
        return new double[]{x, y, z};
    }

    @Override
    public void connect(String port, int baudRate) {
        if (state.isConnected()) {
            return;
        }
        message("Simulated connection to " + port + " at " + baudRate
                + " baud. Nothing is being sent to a machine.", MessageKind.INFO);
        message("Grbl 1.1 [simulated]", MessageKind.RECEIVED);
        setState(State.CONNECTED);
    }

    @Override
    public boolean isAligned() {
        return aligned;
    }

    @Override
    public void disconnect() {
        stopTimer();
        stopAlignTimer();
        clearAlignment("disconnected");
        message("Disconnected.", MessageKind.INFO);
        setState(State.DISCONNECTED);
        progress(-1);
    }

    @Override
    public void runStartupProcedure(GcodeProgram program, double railLengthMm) {
        if (!state.isConnected()) {
            message("Not connected.", MessageKind.ERROR);
            return;
        }
        if (!state.isIdle()) {
            message("Machine is busy - finish or stop what it is doing first.", MessageKind.ERROR);
            return;
        }
        if (program == null) {
            message("Slice a tube first - parking depends on how far that program travels.",
                    MessageKind.ERROR);
            return;
        }

        GcodeProgram.Summary summary = program.summary();
        double right = summary.clearanceTowardFirstPassMm();
        double left = summary.clearanceBehindMm();
        double needed = right + left;
        double spare = railLengthMm - needed;

        if (spare < 0) {
            message(String.format(Locale.ROOT,
                    "This program sweeps %.1f mm of X (%.1f mm right of the start, %.1f mm left) "
                            + "but the rail is only %.1f mm. Shorten the tube, lower the wrap "
                            + "angle, or correct the rail length in settings.",
                    needed, right, left, railLengthMm), MessageKind.ERROR);
            return;
        }

        // Park with the spare rail split evenly, so a small mistake either way still has room.
        double parkFromLeftEnd = left + spare / 2;

        aligned = false;
        alignStep = 0;
        alignSteps = List.of(
                () -> {
                    message("Alignment 1/5 - exercising X.", MessageKind.INFO);
                    sweepX();
                },
                () -> {
                    message("Alignment 2/5 - calibrating Y, the mandrel axis.", MessageKind.INFO);
                    message("G91 G21 Y360", MessageKind.SENT);
                    y = 0;
                    firePosition();
                    message("Y indexed and zeroed.", MessageKind.RECEIVED);
                },
                () -> {
                    message("Alignment 3/5 - calibrating Z, the tow head.", MessageKind.INFO);
                    message("G91 G21 Z90", MessageKind.SENT);
                    message("G91 G21 Z-180", MessageKind.SENT);
                    message("G91 G21 Z90", MessageKind.SENT);
                    z = 0;
                    firePosition();
                    message("Z swept both ways and returned to neutral.", MessageKind.RECEIVED);
                },
                () -> {
                    message("Alignment 4/5 - parking the tow head at the left-hand start.",
                            MessageKind.INFO);
                    message(String.format(Locale.ROOT,
                            "G91 G21 X%s", trim(GcodeGenerator.LEFT_X_SIGN * X_SWEEP_MM)),
                            MessageKind.SENT);
                    message("G10 L20 P1 X0 Y0 Z0", MessageKind.SENT);
                    x = 0;
                    y = 0;
                    z = 0;
                    firePosition();
                    message(String.format(Locale.ROOT,
                            "Parked %.1f mm from the left end of the %.0f mm rail. This program "
                                    + "needs %.1f mm to the right of here and %.1f mm to the left "
                                    + "- %.1f mm of rail in total, %.1f mm spare.",
                            parkFromLeftEnd, railLengthMm, right, left, needed, spare),
                            MessageKind.INFO);
                },
                () -> {
                    message("Alignment 5/5 - waiting on the operator.", MessageKind.INFO);
                    prompt(String.format(Locale.ROOT,
                            "Mount the mandrel, then attach the tow %.0f mm in from its left-hand "
                                    + "end.%n%nThe head is parked %.1f mm from the left end of the "
                                    + "rail. This program travels %.1f mm to the right of there and "
                                    + "%.1f mm to the left, leaving %.1f mm spare.",
                            TOW_ATTACH_INSET_MM, parkFromLeftEnd, right, left, spare));
                });

        alignedClearanceRightMm = right;
        alignedClearanceLeftMm = left;

        message("Startup and alignment procedure.", MessageKind.INFO);
        setState(State.ALIGNING);
        startAlignTimer();
    }

    @Override
    public void confirmOperatorAction() {
        if (state != State.ALIGNING) {
            return;
        }
        aligned = true;
        message(String.format(Locale.ROOT,
                "Aligned. Work zero is the tow attachment point, %.0f mm in from the left-hand end "
                        + "of the mandrel.", TOW_ATTACH_INSET_MM), MessageKind.INFO);
        setState(State.CONNECTED);
    }

    @Override
    public void run(GcodeProgram program) {
        if (!state.isIdle()) {
            message("Not idle - cannot start a program.", MessageKind.ERROR);
            return;
        }
        if (!aligned) {
            message("Run the startup procedure first - the machine does not know where it is.",
                    MessageKind.ERROR);
            return;
        }
        GcodeProgram.Summary summary = program.summary();
        if (summary.clearanceTowardFirstPassMm() > alignedClearanceRightMm + 1e-6
                || summary.clearanceBehindMm() > alignedClearanceLeftMm + 1e-6) {
            message(String.format(Locale.ROOT,
                    "This program needs more room than the machine was parked for (%.1f/%.1f mm "
                            + "against %.1f/%.1f mm). Run the startup procedure again.",
                    summary.clearanceTowardFirstPassMm(), summary.clearanceBehindMm(),
                    alignedClearanceRightMm, alignedClearanceLeftMm), MessageKind.ERROR);
            return;
        }
        queue = program.lines();
        cursor = 0;
        message("Starting program: " + queue.size() + " lines, "
                + program.summary().passCount() + " passes.", MessageKind.INFO);
        setState(State.RUNNING);
        startTimer();
    }

    @Override
    public void pause() {
        if (state != State.RUNNING) {
            return;
        }
        stopTimer();
        message("Feed hold.", MessageKind.SENT);
        setState(State.PAUSED);
    }

    @Override
    public void resume() {
        if (state != State.PAUSED) {
            return;
        }
        message("Cycle start.", MessageKind.SENT);
        setState(State.RUNNING);
        startTimer();
    }

    @Override
    public void stop() {
        stopTimer();
        if (cursor > 0 && cursor < queue.size()) {
            message("Program stopped at line " + cursor + " of " + queue.size() + ".",
                    MessageKind.INFO);
        }
        queue = List.of();
        cursor = 0;
        progress(-1);
        if (state.isConnected()) {
            setState(State.CONNECTED);
        }
    }

    @Override
    public void home() {
        requireIdle(() -> {
            message("$H", MessageKind.SENT);
            message("Homing is not implemented on the winder yet.", MessageKind.INFO);
            x = 0;
            y = 0;
            z = 0;
            firePosition();
        });
    }

    @Override
    public void zeroWork() {
        requireIdle(() -> {
            message("G10 L20 P1 X0 Y0 Z0", MessageKind.SENT);
            x = 0;
            y = 0;
            z = 0;
            firePosition();
            message("Work zero set at the current position.", MessageKind.RECEIVED);
        });
    }

    @Override
    public void softReset() {
        stopTimer();
        stopAlignTimer();
        clearAlignment("soft reset");
        message("[soft reset]", MessageKind.SENT);
        queue = List.of();
        cursor = 0;
        progress(-1);
        if (state.isConnected()) {
            setState(State.CONNECTED);
        }
        message("Grbl 1.1 [simulated]", MessageKind.RECEIVED);
    }

    @Override
    public void unlock() {
        message("$X", MessageKind.SENT);
        if (state == State.ALARM) {
            setState(State.CONNECTED);
        }
        message("[Caution: Unlocked]", MessageKind.RECEIVED);
    }

    @Override
    public void jog(Axis axis, double distance, double feedrate) {
        requireIdle(() -> {
            String command = String.format(Locale.ROOT, "$J=G91 G21 %s%s F%s",
                    axis.letter(), trim(distance), trim(feedrate));
            message(command, MessageKind.SENT);
            switch (axis) {
                case X -> x += distance;
                case Y -> y += distance;
                case Z -> z += distance;
            }
            firePosition();
        });
    }

    // ------------------------------------------------------------------

    private void requireIdle(Runnable action) {
        if (!state.isConnected()) {
            message("Not connected.", MessageKind.ERROR);
            return;
        }
        if (!state.isIdle()) {
            message("Machine is " + state.label().toLowerCase(Locale.ROOT)
                    + " - finish or stop the program first.", MessageKind.ERROR);
            return;
        }
        action.run();
    }

    /** Prove the carriage moves, and end up back where it started. */
    private void sweepX() {
        double left = GcodeGenerator.LEFT_X_SIGN * X_SWEEP_MM;
        message(String.format(Locale.ROOT, "G91 G21 X%s", trim(left)), MessageKind.SENT);
        message(String.format(Locale.ROOT, "G91 G21 X%s", trim(-2 * left)), MessageKind.SENT);
        message(String.format(Locale.ROOT, "G91 G21 X%s", trim(left)), MessageKind.SENT);
        message("X travels freely both ways.", MessageKind.RECEIVED);
    }

    private void clearAlignment(String why) {
        if (aligned) {
            message("Alignment cleared by " + why + " - the machine no longer knows where it is.",
                    MessageKind.INFO);
        }
        aligned = false;
        alignSteps = List.of();
        alignStep = 0;
    }

    private void startAlignTimer() {
        stopAlignTimer();
        alignTimer = new Timer(ALIGN_TICK_MS, event -> alignTick());
        alignTimer.setInitialDelay(0);
        alignTimer.start();
    }

    private void stopAlignTimer() {
        if (alignTimer != null) {
            alignTimer.stop();
            alignTimer = null;
        }
    }

    private void alignTick() {
        if (alignStep >= alignSteps.size()) {
            stopAlignTimer();
            return;
        }
        alignSteps.get(alignStep++).run();
        progress((double) alignStep / alignSteps.size());
        if (alignStep >= alignSteps.size()) {
            stopAlignTimer();
            progress(-1);
        }
    }

    private void prompt(String text) {
        listeners.forEach(listener -> listener.onOperatorAction(text));
    }

    private void startTimer() {
        stopTimer();
        timer = new Timer(TICK_MS, event -> tick());
        timer.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    private void tick() {
        for (int i = 0; i < LINES_PER_TICK && cursor < queue.size(); i++) {
            step(queue.get(cursor++));
        }
        progress(queue.isEmpty() ? -1 : (double) cursor / queue.size());
        firePosition();
        if (cursor >= queue.size()) {
            stopTimer();
            message("Program complete.", MessageKind.INFO);
            queue = List.of();
            cursor = 0;
            progress(-1);
            setState(State.CONNECTED);
        }
    }

    private void step(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.startsWith(";")) {
            message(trimmed, MessageKind.INFO);
            return;
        }

        String body = trimmed;
        int comment = body.indexOf(';');
        if (comment >= 0) {
            body = body.substring(0, comment).trim();
        }

        double dx = 0;
        double dy = 0;
        double dz = 0;
        boolean moved = false;
        String remainder = body;
        Matcher matcher = WORD.matcher(body);
        while (matcher.find()) {
            remainder = remainder.replaceFirst(Pattern.quote(matcher.group()), "");
            switch (Character.toUpperCase(matcher.group(1).charAt(0))) {
                case 'X' -> {
                    dx = Double.parseDouble(matcher.group(2));
                    moved = true;
                }
                case 'Y' -> {
                    dy = Double.parseDouble(matcher.group(2));
                    moved = true;
                }
                case 'Z' -> {
                    dz = Double.parseDouble(matcher.group(2));
                    moved = true;
                }
                case 'G', 'M', 'F', 'S' -> {
                    // Understood, nothing to integrate.
                }
                default -> {
                    message("error: unsupported word '" + matcher.group() + "' in: " + trimmed,
                            MessageKind.ERROR);
                    setState(State.ALARM);
                    stopTimer();
                    return;
                }
            }
        }
        if (!remainder.trim().isEmpty()) {
            message("error: could not parse '" + remainder.trim() + "' in: " + trimmed,
                    MessageKind.ERROR);
            setState(State.ALARM);
            stopTimer();
            return;
        }

        message(trimmed, MessageKind.SENT);
        if (moved) {
            // The generator emits G91, so every move is incremental.
            x += dx;
            y += dy;
            z += dz;
        }
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private void setState(State next) {
        if (state != next) {
            state = next;
            listeners.forEach(listener -> listener.onStateChanged(next));
        }
    }

    private void firePosition() {
        listeners.forEach(listener -> listener.onPositionChanged(x, y, z));
    }

    private void progress(double fraction) {
        listeners.forEach(listener -> listener.onProgress(fraction));
    }

    private void message(String text, MessageKind kind) {
        listeners.forEach(listener -> listener.onMessage(text, kind));
    }
}

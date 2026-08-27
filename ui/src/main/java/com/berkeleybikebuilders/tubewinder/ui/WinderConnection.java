package com.berkeleybikebuilders.tubewinder.ui;

import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;

/**
 * Everything the UI needs from the machine.
 *
 * <p>The point of this interface is that the toolbar, the jog panel and the terminal are written
 * against it and nothing else. Today the only implementation is {@link SimulatedConnection}, which
 * pretends. When the sender is real - whether that is a serial GRBL client written here or UGS's
 * {@code BackendAPI} borrowed from {@code ugs-core} - it implements this and the UI does not change.
 */
interface WinderConnection {

    enum State {
        DISCONNECTED("Disconnected"),
        CONNECTED("Idle"),
        ALIGNING("Aligning"),
        RUNNING("Running"),
        PAUSED("Paused"),
        ALARM("Alarm");

        private final String label;

        State(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }

        boolean isConnected() {
            return this != DISCONNECTED;
        }

        /** Whether it is safe to jog or start a program. */
        boolean isIdle() {
            return this == CONNECTED;
        }
    }

    /** How a terminal line should be presented. */
    enum MessageKind {
        SENT, RECEIVED, INFO, ERROR
    }

    enum Axis {
        X("X", "mm"), Y("Y", "deg"), Z("Z", "deg");

        private final String letter;
        private final String unit;

        Axis(String letter, String unit) {
            this.letter = letter;
            this.unit = unit;
        }

        String letter() {
            return letter;
        }

        String unit() {
            return unit;
        }

        boolean isRotary() {
            return this != X;
        }
    }

    interface Listener {
        void onStateChanged(State state);

        /**
         * The machine is waiting on a person: mount the mandrel, attach the tow, that sort of thing.
         * The UI should put the prompt in front of the operator and call
         * {@link #confirmOperatorAction()} once they say they have done it.
         */
        void onOperatorAction(String prompt);

        /** X in mm, Y and Z in degrees. */
        void onPositionChanged(double x, double y, double z);

        void onMessage(String text, MessageKind kind);

        /** 0..1, or -1 when no program is running. */
        void onProgress(double fraction);
    }

    void addListener(Listener listener);

    State state();

    double[] position();

    void connect(String port, int baudRate);

    void disconnect();

    /**
     * Whether the machine has been through the startup procedure and knows where it is.
     *
     * <p>Cleared by disconnecting and by a soft reset - after either, the controller's idea of
     * position is not to be trusted.
     */
    boolean isAligned();

    /**
     * Startup and alignment, the way a 3D printer finds itself when it wakes up.
     *
     * <p>Takes the sliced program because parking is not a fixed spot: the head has to end up
     * somewhere with enough rail on both sides for that particular program's carriage envelope,
     * which {@code program.summary()} reports. Takes the rail length so it can refuse a program
     * that would not fit at all.
     */
    void runStartupProcedure(GcodeProgram program, double railLengthMm);

    /** Called by the UI when the operator confirms whatever {@code onOperatorAction} asked for. */
    void confirmOperatorAction();

    void run(GcodeProgram program);

    void pause();

    void resume();

    void stop();

    void home();

    void zeroWork();

    void softReset();

    void unlock();

    void jog(Axis axis, double distance, double feedrate);
}

package com.berkeleybikebuilders.tubewinder.cli;

import com.berkeleybikebuilders.tubewinder.WindingParameters;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeGenerator;
import com.berkeleybikebuilders.tubewinder.gcode.GcodeProgram;
import com.berkeleybikebuilders.tubewinder.geometry.TowHeadGeometry;
import com.berkeleybikebuilders.tubewinder.geometry.TubeProfile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Command-line front end, so the backend is usable today without waiting for the GUI.
 *
 * <p>Run with no arguments for the same interactive prompts Generator v7 had. Run with
 * {@code key=value} arguments for a scripted run:
 *
 * <pre>
 *   java -jar tubewinder-backend.jar \
 *       type=straight perimeter=84.823 length=700 wrap=45 tow=6.5 wall=1.6 feed=12500 \
 *       out=tube.gcode
 *
 *   java -jar tubewinder-backend.jar \
 *       type=tapered nearPerimeter=104.922 farPerimeter=144 nearLength=16 taperLength=465.162 \
 *       farLength=16 wrap=60 tow=6.5 wall=0.2 feed=12500 out=taper.gcode
 * </pre>
 *
 * <p>Optional keys: {@code headH} (default 50), {@code headV} (default 25), {@code taperStep}
 * (default 1), {@code offset=off} to disable tow-head compensation, {@code comments=off},
 * {@code takeup=computed}.
 */
public final class GeneratorCli {

    private GeneratorCli() {
    }

    public static void main(String[] args) throws IOException {
        try {
            WindingParameters params;
            Path out;
            if (args.length == 0) {
                Interactive interactive = promptForEverything();
                params = interactive.params();
                out = interactive.out();
            } else {
                Map<String, String> kv = parseArgs(args);
                params = fromMap(kv);
                out = kv.containsKey("out") ? Path.of(kv.get("out")) : null;
            }

            GcodeProgram program = GcodeGenerator.generate(params);

            if (out == null) {
                System.out.print(program.text());
            } else {
                program.writeTo(out);
                System.out.printf("Wrote %d lines to %s%n", program.lines().size(), out.toAbsolutePath());
            }
            System.err.print(program.summary().report());
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        }
    }

    // ------------------------------------------------------------ scripted

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> kv = new HashMap<>();
        for (String arg : args) {
            int eq = arg.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Expected key=value, got: " + arg);
            }
            kv.put(arg.substring(0, eq).trim().toLowerCase(Locale.ROOT), arg.substring(eq + 1).trim());
        }
        return kv;
    }

    private static WindingParameters fromMap(Map<String, String> kv) {
        String type = kv.getOrDefault("type", "straight").toLowerCase(Locale.ROOT);
        double taperStep = optDouble(kv, "taperstep", TubeProfile.DEFAULT_TAPER_STEP_MM);

        TubeProfile profile;
        if (type.startsWith("straight") || type.startsWith("linear")) {
            profile = TubeProfile.straight(reqDouble(kv, "perimeter"), reqDouble(kv, "length"));
        } else if (type.startsWith("taper") || type.startsWith("non")) {
            profile = TubeProfile.tapered(
                    reqDouble(kv, "nearperimeter"),
                    reqDouble(kv, "farperimeter"),
                    reqDouble(kv, "nearlength"),
                    reqDouble(kv, "taperlength"),
                    reqDouble(kv, "farlength"),
                    taperStep);
        } else {
            throw new IllegalArgumentException("Unknown type: " + type + " (straight or tapered)");
        }

        boolean offsetOn = !"off".equalsIgnoreCase(kv.getOrDefault("offset", "on"));
        TowHeadGeometry head = offsetOn
                ? new TowHeadGeometry(optDouble(kv, "headh", 50.0), optDouble(kv, "headv", 25.0))
                : TowHeadGeometry.none();

        return WindingParameters.builder()
                .profile(profile)
                .wrapAngleDeg(reqDouble(kv, "wrap"))
                .towWidthMm(reqDouble(kv, "tow"))
                .wallThicknessMm(reqDouble(kv, "wall"))
                .feedrate(reqDouble(kv, "feed"))
                .towHead(head)
                .useComputedSlackTakeup("computed".equalsIgnoreCase(kv.getOrDefault("takeup", "v7")))
                .includeComments(!"off".equalsIgnoreCase(kv.getOrDefault("comments", "on")))
                .build();
    }

    private static double reqDouble(Map<String, String> kv, String key) {
        String v = kv.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required argument: " + key);
        }
        try {
            return Double.parseDouble(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number, got: " + v);
        }
    }

    private static double optDouble(Map<String, String> kv, String key, double fallback) {
        return kv.containsKey(key) ? reqDouble(kv, key) : fallback;
    }

    // --------------------------------------------------------- interactive

    private record Interactive(WindingParameters params, Path out) {
    }

    private static Interactive promptForEverything() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Good morning BBB! Tube winder G-code generator.");
        String type = choice(in, "Tube type", "straight", "tapered");

        TubeProfile profile;
        if (type.equals("straight")) {
            double perimeter = number(in, "Cross-section perimeter (mm)");
            double length = number(in, "Tube length (mm)");
            profile = TubeProfile.straight(perimeter, length);
        } else {
            double p0 = number(in, "Perimeter at the near end (mm)");
            double p1 = number(in, "Perimeter at the far end (mm)");
            double l1 = number(in, "Length of the near constant section (mm)");
            double l2 = number(in, "Length of the tapered section (mm)");
            double l3 = number(in, "Length of the far constant section (mm)");
            profile = TubeProfile.tapered(p0, p1, l1, l2, l3);
        }

        double wrap = number(in, "Wrap angle from the mandrel axis (deg)");
        double tow = number(in, "Tow width (mm)");
        double wall = number(in, "Wall thickness (mm)");
        double feed = number(in, "Feedrate");

        System.out.println("Tow head offset, measured from the MANDREL AXIS to the payout point.");
        double headH = numberOrDefault(in, "  horizontal (mm)", 50.0);
        double headV = numberOrDefault(in, "  vertical (mm)", 25.0);

        WindingParameters params = WindingParameters.builder()
                .profile(profile)
                .wrapAngleDeg(wrap)
                .towWidthMm(tow)
                .wallThicknessMm(wall)
                .feedrate(feed)
                .towHead(new TowHeadGeometry(headH, headV))
                .build();

        System.out.print("Output file (blank to print to this terminal): ");
        String file = in.readLine();
        Path out = (file == null || file.isBlank()) ? null : Path.of(file.trim());
        return new Interactive(params, out);
    }

    private static String choice(BufferedReader in, String prompt, String... options) throws IOException {
        while (true) {
            System.out.printf("%s (%s): ", prompt, String.join(" or ", options));
            String line = in.readLine();
            if (line != null) {
                String v = line.trim().toLowerCase(Locale.ROOT);
                for (String option : options) {
                    if (option.equals(v)) {
                        return option;
                    }
                }
            }
            System.out.println("Whoops! Please try again :)");
        }
    }

    private static double number(BufferedReader in, String prompt) throws IOException {
        while (true) {
            System.out.printf("%s: ", prompt);
            String line = in.readLine();
            try {
                double v = Double.parseDouble(line.trim());
                if (v > 0) {
                    return v;
                }
            } catch (RuntimeException ignored) {
                // fall through to the retry message
            }
            System.out.println("Whoops! Please enter a positive number :)");
        }
    }

    private static double numberOrDefault(BufferedReader in, String prompt, double fallback)
            throws IOException {
        System.out.printf("%s [%s]: ", prompt, fallback);
        String line = in.readLine();
        if (line == null || line.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(line.trim());
        } catch (NumberFormatException e) {
            System.out.println("Not a number; using " + fallback);
            return fallback;
        }
    }
}

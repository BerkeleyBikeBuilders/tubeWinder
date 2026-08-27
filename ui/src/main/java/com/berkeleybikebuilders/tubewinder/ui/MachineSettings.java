package com.berkeleybikebuilders.tubewinder.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The things that do not change between winds.
 *
 * <p>Tow width, feedrate, layer thickness, the tow head offsets, the serial port - set them once in
 * the settings dialog and they persist across runs, so the parameters panel only ever asks for what
 * is actually different about this tube.
 *
 * <p>Stored as a properties file at {@code ~/.tubewinder/settings.properties}. Plain text on
 * purpose: it is readable, diffable, and easy to copy between the machines in the shop.
 */
final class MachineSettings {

    private static final Path FILE =
            Path.of(System.getProperty("user.home"), ".tubewinder", "settings.properties");

    // --- material and process -------------------------------------------
    private double feedrate = 12500;
    private double towWidthMm = 6.5;
    private double layerThicknessMm = 0.2;

    // --- machine geometry -------------------------------------------------
    /** Usable X travel, end to end. The startup procedure parks inside it. */
    private double railLengthMm = 1000;

    private double towHeadHorizontalMm = 50;
    private double towHeadVerticalMm = 25;
    private boolean offsetCompensation = true;

    // --- generator behaviour --------------------------------------------
    private double taperStepMm = 1.0;
    private boolean computedTakeup = false;
    private boolean includeComments = true;

    // --- connection ------------------------------------------------------
    private String port = "COM3";
    private int baudRate = 115200;

    // --- jogging ---------------------------------------------------------
    private double jogStepLinearMm = 1.0;
    private double jogStepRotaryDeg = 5.0;
    private double jogFeedrate = 1000;

    static MachineSettings load() {
        MachineSettings settings = new MachineSettings();
        if (!Files.exists(FILE)) {
            return settings;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            properties.load(in);
        } catch (IOException e) {
            return settings;
        }
        settings.feedrate = d(properties, "feedrate", settings.feedrate);
        settings.towWidthMm = d(properties, "towWidthMm", settings.towWidthMm);
        settings.layerThicknessMm = d(properties, "layerThicknessMm", settings.layerThicknessMm);
        settings.railLengthMm = d(properties, "railLengthMm", settings.railLengthMm);
        settings.towHeadHorizontalMm = d(properties, "towHeadHorizontalMm", settings.towHeadHorizontalMm);
        settings.towHeadVerticalMm = d(properties, "towHeadVerticalMm", settings.towHeadVerticalMm);
        settings.offsetCompensation = b(properties, "offsetCompensation", settings.offsetCompensation);
        settings.taperStepMm = d(properties, "taperStepMm", settings.taperStepMm);
        settings.computedTakeup = b(properties, "computedTakeup", settings.computedTakeup);
        settings.includeComments = b(properties, "includeComments", settings.includeComments);
        settings.port = properties.getProperty("port", settings.port);
        settings.baudRate = (int) d(properties, "baudRate", settings.baudRate);
        settings.jogStepLinearMm = d(properties, "jogStepLinearMm", settings.jogStepLinearMm);
        settings.jogStepRotaryDeg = d(properties, "jogStepRotaryDeg", settings.jogStepRotaryDeg);
        settings.jogFeedrate = d(properties, "jogFeedrate", settings.jogFeedrate);
        return settings;
    }

    /** @return null on success, or a message describing why the save failed */
    String save() {
        Properties properties = new Properties();
        properties.setProperty("feedrate", String.valueOf(feedrate));
        properties.setProperty("towWidthMm", String.valueOf(towWidthMm));
        properties.setProperty("layerThicknessMm", String.valueOf(layerThicknessMm));
        properties.setProperty("railLengthMm", String.valueOf(railLengthMm));
        properties.setProperty("towHeadHorizontalMm", String.valueOf(towHeadHorizontalMm));
        properties.setProperty("towHeadVerticalMm", String.valueOf(towHeadVerticalMm));
        properties.setProperty("offsetCompensation", String.valueOf(offsetCompensation));
        properties.setProperty("taperStepMm", String.valueOf(taperStepMm));
        properties.setProperty("computedTakeup", String.valueOf(computedTakeup));
        properties.setProperty("includeComments", String.valueOf(includeComments));
        properties.setProperty("port", port);
        properties.setProperty("baudRate", String.valueOf(baudRate));
        properties.setProperty("jogStepLinearMm", String.valueOf(jogStepLinearMm));
        properties.setProperty("jogStepRotaryDeg", String.valueOf(jogStepRotaryDeg));
        properties.setProperty("jogFeedrate", String.valueOf(jogFeedrate));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                properties.store(out, "Tube winder machine settings");
            }
            return null;
        } catch (IOException e) {
            return "Could not save settings to " + FILE + ": " + e.getMessage();
        }
    }

    static Path settingsFile() {
        return FILE;
    }

    private static double d(Properties p, String key, double fallback) {
        try {
            return Double.parseDouble(p.getProperty(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean b(Properties p, String key, boolean fallback) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(fallback)));
    }

    // --- accessors -------------------------------------------------------

    double feedrate() {
        return feedrate;
    }

    void setFeedrate(double v) {
        feedrate = v;
    }

    double towWidthMm() {
        return towWidthMm;
    }

    void setTowWidthMm(double v) {
        towWidthMm = v;
    }

    double layerThicknessMm() {
        return layerThicknessMm;
    }

    void setLayerThicknessMm(double v) {
        layerThicknessMm = v;
    }

    double railLengthMm() {
        return railLengthMm;
    }

    void setRailLengthMm(double v) {
        railLengthMm = v;
    }

    double towHeadHorizontalMm() {
        return towHeadHorizontalMm;
    }

    void setTowHeadHorizontalMm(double v) {
        towHeadHorizontalMm = v;
    }

    double towHeadVerticalMm() {
        return towHeadVerticalMm;
    }

    void setTowHeadVerticalMm(double v) {
        towHeadVerticalMm = v;
    }

    boolean offsetCompensation() {
        return offsetCompensation;
    }

    void setOffsetCompensation(boolean v) {
        offsetCompensation = v;
    }

    double taperStepMm() {
        return taperStepMm;
    }

    void setTaperStepMm(double v) {
        taperStepMm = v;
    }

    boolean computedTakeup() {
        return computedTakeup;
    }

    void setComputedTakeup(boolean v) {
        computedTakeup = v;
    }

    boolean includeComments() {
        return includeComments;
    }

    void setIncludeComments(boolean v) {
        includeComments = v;
    }

    String port() {
        return port;
    }

    void setPort(String v) {
        port = v;
    }

    int baudRate() {
        return baudRate;
    }

    void setBaudRate(int v) {
        baudRate = v;
    }

    double jogStepLinearMm() {
        return jogStepLinearMm;
    }

    void setJogStepLinearMm(double v) {
        jogStepLinearMm = v;
    }

    double jogStepRotaryDeg() {
        return jogStepRotaryDeg;
    }

    void setJogStepRotaryDeg(double v) {
        jogStepRotaryDeg = v;
    }

    double jogFeedrate() {
        return jogFeedrate;
    }

    void setJogFeedrate(double v) {
        jogFeedrate = v;
    }
}

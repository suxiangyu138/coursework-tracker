package com.coursework.tracker.ui;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AppIcon {

    private static final int[] WINDOW_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};
    private static final int[] RESOURCE_SIZES = {16, 20, 24, 32, 40, 48, 64, 96, 128, 256};
    private static final int TRAY_SIZE = 24;
    private static final int MAX_SIZE = 256;
    private static final String RESOURCE_PATTERN = "/icons/app-icon-%d.png";
    private static final Map<Integer, BufferedImage> CACHE = new ConcurrentHashMap<>();

    private AppIcon() {
    }

    public static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>(WINDOW_SIZES.length);
        for (int size : WINDOW_SIZES) {
            icons.add(image(size));
        }
        return icons;
    }

    public static void applyTo(Window window) {
        if (window != null) {
            window.setIconImages(windowIcons());
        }
    }

    public static Image trayIcon() {
        return image(TRAY_SIZE);
    }

    public static BufferedImage image(int size) {
        int target = Math.max(1, size);
        return CACHE.computeIfAbsent(target, AppIcon::render);
    }

    private static BufferedImage render(int size) {
        BufferedImage exact = read(size);
        if (exact != null) {
            return exact;
        }
        BufferedImage source = read(nearest(size));
        if (source == null) {
            return fallback(size);
        }
        return scale(source, size);
    }

    private static int nearest(int size) {
        for (int candidate : RESOURCE_SIZES) {
            if (candidate >= size) {
                return candidate;
            }
        }
        return MAX_SIZE;
    }

    private static BufferedImage read(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream(String.format(RESOURCE_PATTERN, size))) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static BufferedImage scale(BufferedImage source, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, size, size, null);
        g.dispose();
        return image;
    }

    private static BufferedImage fallback(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4F46E5), size, size, new Color(0x0EA5E9)));
        g.fillRoundRect(0, 0, size, size, Math.round(size * 0.30f), Math.round(size * 0.30f));
        g.dispose();
        return image;
    }
}

package com.coursework.tracker.ui;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用图标：渐变圆角底 + 白色时钟环 + 完成勾 + 琥珀色提醒点。
 * 纯 Java2D 绘制，任意尺寸都清晰，不依赖外部图片资源。
 */
public final class AppIcon {

    private static final Color GRADIENT_START = new Color(0x4F46E5);
    private static final Color GRADIENT_END = new Color(0x0EA5E9);
    private static final Color ACCENT = new Color(0xFBBF24);
    private static final Color SHADOW = new Color(0x0B1220);
    private static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private AppIcon() {
    }

    /** 窗口/任务栏使用的一组尺寸，Windows 会挑选最合适的一张。 */
    public static List<Image> windowIcons() {
        List<Image> icons = new ArrayList<>(SIZES.length);
        for (int size : SIZES) {
            icons.add(image(size));
        }
        return icons;
    }

    public static void applyTo(Window window) {
        if (window != null) {
            window.setIconImages(windowIcons());
        }
    }

    /** 托盘使用 24px 的紧凑版本（16/24 会省略圆环，只保留粗勾和提醒点）。 */
    public static Image trayIcon() {
        return image(24);
    }

    public static BufferedImage image(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float pad = Math.max(1f, size * 0.045f);
        float side = size - pad * 2;
        float arc = size * 0.30f;
        float smallSize = size;

        // 柔和投影
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Float(pad, pad + size * 0.02f, side, side, arc, arc));

        // 主体渐变
        RoundRectangle2D body = new RoundRectangle2D.Float(pad, pad, side, side, arc, arc);
        g.setPaint(new GradientPaint(0, 0, GRADIENT_START, size, size, GRADIENT_END));
        g.fill(body);

        // 顶部高光
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f));
        g.setPaint(new GradientPaint(0, pad, Color.WHITE, 0, pad + side * 0.55f,
                new Color(255, 255, 255, 0)));
        g.fill(new RoundRectangle2D.Float(pad, pad, side, side * 0.62f, arc, arc));
        g.setComposite(AlphaComposite.SrcOver);

        // 内描边
        g.setColor(new Color(255, 255, 255, 46));
        g.setStroke(new BasicStroke(Math.max(1f, size * 0.012f)));
        g.draw(body);

        // 时钟环 + 完成勾：小尺寸（16/20/24）省略圆环，只保留粗勾，避免糊成一团
        float cx = size * 0.50f;
        float cy = size * 0.535f;
        float radius = size * 0.255f;
        boolean compact = size <= 24;
        if (!compact) {
            g.setColor(new Color(255, 255, 255, 235));
            g.setStroke(new BasicStroke(size * 0.082f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
        }

        Path2D check = new Path2D.Float();
        if (compact) {
            check.moveTo(size * 0.27f, size * 0.54f);
            check.lineTo(size * 0.44f, size * 0.71f);
            check.lineTo(size * 0.75f, size * 0.34f);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(size * 0.135f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        } else {
            check.moveTo(cx - radius * 0.52f, cy + radius * 0.02f);
            check.lineTo(cx - radius * 0.10f, cy + radius * 0.44f);
            check.lineTo(cx + radius * 0.58f, cy - radius * 0.46f);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(size * 0.078f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        }
        g.draw(check);

        // 琥珀色提醒点（带深色描边，小尺寸也醒目）
        float dotX = size * (compact ? 0.78f : 0.755f);
        float dotY = size * 0.245f;
        float dotR = size * (compact ? 0.085f : 0.062f);
        g.setColor(new Color(SHADOW.getRed(), SHADOW.getGreen(), SHADOW.getBlue(), 190));
        g.fill(new Ellipse2D.Float(dotX - dotR * 1.45f, dotY - dotR * 1.45f,
                dotR * 2.9f, dotR * 2.9f));
        g.setColor(ACCENT);
        g.fill(new Ellipse2D.Float(dotX - dotR, dotY - dotR, dotR * 2, dotR * 2));

        g.dispose();
        return image;
    }
}

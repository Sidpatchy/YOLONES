package com.sidpatchy.yolones;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class FrameBufferRenderer extends JPanel {
    private BufferedImage image;
    private int scale;

    public FrameBufferRenderer(int scale) {
        this.scale = scale;
        this.image = new BufferedImage(256, 240, BufferedImage.TYPE_INT_RGB);

        setPreferredSize(new Dimension(256 * scale, 240 * scale));
        setBackground(Color.BLACK);
    }

    public void updateFrame(int[] framebuffer) {
        // Copy framebuffer to image
        image.setRGB(0, 0, 256, 240, framebuffer, 0, 256);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw scaled up
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.drawImage(image, 0, 0, 256 * scale, 240 * scale, null);
    }

    public static JFrame createWindow(FrameBufferRenderer renderer) {
        JFrame frame = new JFrame("YOLONES");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(renderer);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }
}


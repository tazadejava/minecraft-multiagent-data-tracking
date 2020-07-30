package me.tazadejava.map;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MapTest {

    static class MapPanel extends JPanel {

        public MapPanel() {
            addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {

                }

                @Override
                public void keyPressed(KeyEvent e) {
                    switch(e.getKeyCode()) {
                        case KeyEvent.VK_RIGHT:
                            xScale += 0.1;
                            break;
                        case KeyEvent.VK_LEFT:
                            xScale -= 0.1;
                            break;
                        case KeyEvent.VK_UP:
                            zScale -= 0.1;
                            break;
                        case KeyEvent.VK_DOWN:
                            zScale += 0.1;
                            break;
                        case KeyEvent.VK_SPACE:
                            angle += 90;
                            angle %= 360;
                            break;
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {

                }
            });

            setFocusable(true);
            requestFocusInWindow();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D graphics = (Graphics2D) g;

            try {
                BufferedImage image = ImageIO.read(new File("/home/yoshi/Documents/GenesisUROP/ActionTrackerPlugin/src/main/resources/sparky_map.png"));

                graphics.translate(width, height);

                graphics.drawImage(getImage(image), null, 0, 0);

                graphics.drawString(((Math.round(xScale * 100d) / 100d)) + " " + ((Math.round(zScale * 100d) / 100d)) + " - " + angle, 10, 246);

                graphics.setColor(Color.RED);
                graphics.drawOval(60, 60 + 32, 8, 8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(width * 3, height * 3);
        }

        double xScale = 0.3265;
        double zScale = 0.4894;
        int angle = 0;
        int width = 128;
        int height = 128;

        private BufferedImage getImage(BufferedImage image) {
            int pointerZTranslation = 64;

            BufferedImage translated = new BufferedImage(width * 3, height * 3, image.getType());

            Graphics2D graphics = translated.createGraphics();

            graphics.translate(width, height);

            graphics.setColor(Color.RED);
            graphics.drawRect(0, 0, width - 1, height - 1);

            graphics.setColor(Color.BLUE);
            graphics.drawRect(0, 0, translated.getWidth() - 1, translated.getHeight() - 1);

            int xDelta = (int) (-xScale * width) +  (width / 2);
            int zDelta = (int) (-zScale * height) + (height / 2) + (int) (((pointerZTranslation) / 255d) * height);
            graphics.drawImage(image, null, xDelta, zDelta);

            graphics.dispose();

            //next, rotate

            BufferedImage rotated = new BufferedImage(width, height, image.getType());

            graphics = rotated.createGraphics();

//            graphics.rotate(Math.toRadians(angle), width / 2, height / 2 + ((int) (((pointerZTranslation) / 255d) * height)));
            graphics.rotate(Math.toRadians(angle), rotated.getWidth() / 2, rotated.getHeight() / 2 + ((int) (((pointerZTranslation) / 255d) * rotated.getHeight())));
            graphics.drawImage(translated, null, -width, -height);

            graphics.dispose();

            return rotated;
//            return translated;
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        MapPanel panel = new MapPanel();
        frame.add(panel);

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                panel.repaint();
            }
        }, 0, 100);

        frame.setLocationRelativeTo(null);
        frame.pack();
        frame.setVisible(true);
    }
}

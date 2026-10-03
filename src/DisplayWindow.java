import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class DisplayWindow extends JFrame {
    private static final int WIDTH = 160;
    private static final int HEIGHT = 144;
    private static final int SCALE = 3;

    private final BufferedImage image;
    private final JPanel canvasPanel;
    private volatile int[] latestFrame;

    public DisplayWindow(String title, Joypad joypad) {
        super(title);

        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);

        canvasPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                int[] frame = latestFrame;
                if (frame != null) {
                    image.setRGB(
                        0, 0,
                        DisplayWindow.WIDTH, DisplayWindow.HEIGHT,
                        frame, 0, DisplayWindow.WIDTH
                    );
                }
                ((Graphics2D) g).setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                );
                g.drawImage(
                    image, 0, 0,
                    DisplayWindow.WIDTH * DisplayWindow.SCALE,
                    DisplayWindow.HEIGHT * DisplayWindow.SCALE,
                    null
                );
            }
        };

        canvasPanel.setBackground(new Color(0xE0F8D0));
        canvasPanel.setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));
        this.add(canvasPanel);
        this.pack();
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);
        this.setResizable(false);

        this.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                joypad.handleKeyPressed(e.getKeyCode());
            }

            @Override
            public void keyReleased(KeyEvent e) {
                joypad.handleKeyReleased(e.getKeyCode());
            }
        });
    }

    public void renderFrame(int[] screenBuffer) {
        latestFrame = screenBuffer.clone();
        canvasPanel.repaint();
    }
}
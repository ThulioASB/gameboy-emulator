import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

public class Display {
    public static final int WIDTH = 160;
    public static final int HEIGHT = 144;
    private static final int SCALE = 3;

    private final JFrame frame;
    private final BufferedImage image;
    private final int[] pixels;

    public Display() {
        image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        pixels = new int[WIDTH * HEIGHT];

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(image, 0, 0, WIDTH * SCALE, HEIGHT * SCALE, null);
            }
        };

        panel.setPreferredSize(new Dimension(WIDTH * SCALE, HEIGHT * SCALE));

        frame = new JFrame("Java Game Boy Emulator");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void attachKeyListener(Joypad joypad) {
        frame.addKeyListener(new KeyAdapter() {
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

    public void render(int[] frameBuffer) {
        System.arraycopy(frameBuffer, 0, pixels, 0, pixels.length);
        image.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
        SwingUtilities.invokeLater(frame::repaint);
    }
}
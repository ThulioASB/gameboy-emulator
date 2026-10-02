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

    public void attachKeyListener(MMU mmu) {
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                updateKey(e.getKeyCode(), true, mmu);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                updateKey(e.getKeyCode(), false, mmu);
            }
        });
    }

    private void updateKey(int keyCode, boolean pressed, MMU mmu) {
        int bit;
        boolean isDirection = false;

        switch (keyCode) {
            case KeyEvent.VK_RIGHT -> { bit = 0; isDirection = true; }
            case KeyEvent.VK_LEFT  -> { bit = 1; isDirection = true; }
            case KeyEvent.VK_UP    -> { bit = 2; isDirection = true; }
            case KeyEvent.VK_DOWN  -> { bit = 3; isDirection = true; }
            case KeyEvent.VK_Z     -> bit = 0; // A
            case KeyEvent.VK_X     -> bit = 1; // B
            case KeyEvent.VK_SHIFT -> bit = 2; // Select
            case KeyEvent.VK_ENTER -> bit = 3; // Start
            default -> { return; }
        }

        if (isDirection) {
            if (pressed) mmu.btnDirections &= ~(1 << bit);
            else        mmu.btnDirections |= (1 << bit);
        } else {
            if (pressed) mmu.btnButtons &= ~(1 << bit);
            else        mmu.btnButtons |= (1 << bit);
        }

        if (pressed) {
            int ifReg = mmu.readByte(0xFF0F);
            mmu.writeByte(0xFF0F, ifReg | 0x10);
        }
    }

    public void render(int[] frameBuffer) {
        System.arraycopy(frameBuffer, 0, pixels, 0, pixels.length);
        image.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
        
        SwingUtilities.invokeLater(() -> frame.repaint());
    }
}
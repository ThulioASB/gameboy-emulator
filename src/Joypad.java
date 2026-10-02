import java.awt.event.KeyEvent;

public class Joypad {
    private final MMU mmu;

    public boolean right, left, up, down;
    public boolean a, b, select, start;

    public Joypad(MMU mmu) {
        this.mmu = mmu;
    }

    public void handleKeyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_RIGHT -> right = true;
            case KeyEvent.VK_LEFT -> left = true;
            case KeyEvent.VK_UP -> up = true;
            case KeyEvent.VK_DOWN -> down = true;
            case KeyEvent.VK_Z -> a = true;
            case KeyEvent.VK_X -> b = true;
            case KeyEvent.VK_ENTER -> start = true;
            case KeyEvent.VK_BACK_SPACE -> select = true;
        }
        triggerJoypadInterrupt();
    }

    public void handleKeyReleased(int keyCode) {
        switch (keyCode) {
            case KeyEvent.VK_RIGHT -> right = false;
            case KeyEvent.VK_LEFT -> left = false;
            case KeyEvent.VK_UP -> up = false;
            case KeyEvent.VK_DOWN -> down = false;
            case KeyEvent.VK_Z -> a = false;
            case KeyEvent.VK_X -> b = false;
            case KeyEvent.VK_ENTER -> start = false;
            case KeyEvent.VK_BACK_SPACE -> select = false;
        }
    }

    private void triggerJoypadInterrupt() {
        int ifReg = mmu.readByte(0xFF0F);
        mmu.writeByte(0xFF0F, ifReg | 0x10);
    }

    public int getState() {
        int joyp = mmu.readByteDirectly(0xFF00);
        int result = joyp | 0x0F;

        if ((joyp & 0x10) == 0) {
            if (right) result &= ~0x01;
            if (left)  result &= ~0x02;
            if (up)    result &= ~0x04;
            if (down)  result &= ~0x08;
        }

        if ((joyp & 0x20) == 0) {
            if (a)      result &= ~0x01;
            if (b)      result &= ~0x02;
            if (select) result &= ~0x04;
            if (start)  result &= ~0x08;
        }

        return result;
    }
}
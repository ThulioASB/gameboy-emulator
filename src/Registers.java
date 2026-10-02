public class Registers {
    public int a, f, b, c, d, e, h, l;
    
    public int sp;
    public int pc;

    public int getBC() {
        return ((b & 0xFF) << 8) | (c & 0xFF);
    }

    public void setBC(int value) {
        this.b = (value >> 8) & 0xFF;
        this.c = value & 0xFF;
    }

    public int getDE() {
        return ((d & 0xFF) << 8) | (e & 0xFF);
    }

    public void setDE(int value) {
        this.d = (value >> 8) & 0xFF;
        this.e = value & 0xFF;
    }

    public int getHL() {
        return ((h & 0xFF) << 8) | (l & 0xFF);
    }

    public void setHL(int value) {
        this.h = (value >> 8) & 0xFF;
        this.l = value & 0xFF;
    }

    public int getAF() {
        return ((a & 0xFF) << 8) | (f & 0xF0);
    }

    public void setAF(int value) {
        this.a = (value >> 8) & 0xFF;
        this.f = value & 0xF0;
    }

    public boolean isZero() { return (f & 0x80) != 0; }
    public void setZero(boolean value) { f = value ? (f | 0x80) : (f & ~0x80); }

    public boolean isSubtract() { return (f & 0x40) != 0; }
    public void setSubtract(boolean value) { f = value ? (f | 0x40) : (f & ~0x40); }

    public boolean isHalfCarry() { return (f & 0x20) != 0; }
    public void setHalfCarry(boolean value) { f = value ? (f | 0x20) : (f & ~0x20); }

    public boolean isCarry() { return (f & 0x10) != 0; }
    public void setCarry(boolean value) { f = value ? (f | 0x10) : (f & ~0x10); }
}
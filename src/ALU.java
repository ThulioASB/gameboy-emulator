public class ALU {
    private final Registers reg;

    public ALU(Registers reg) {
        this.reg = reg;
    }

    public int add8(int a, int b, boolean includeCarry) {
        int cVal = (includeCarry && reg.isCarry()) ? 1 : 0;
        int result = a + b + cVal;

        reg.setZero((result & 0xFF) == 0);
        reg.setSubtract(false);
        reg.setHalfCarry(((a & 0x0F) + (b & 0x0F) + cVal) > 0x0F);
        reg.setCarry(result > 0xFF);

        return result & 0xFF;
    }

    public int sub8(int a, int b, boolean includeCarry) {
        int cVal = (includeCarry && reg.isCarry()) ? 1 : 0;
        int result = a - b - cVal;

        reg.setZero((result & 0xFF) == 0);
        reg.setSubtract(true);
        reg.setHalfCarry(((a & 0x0F) - (b & 0x0F) - cVal) < 0);
        reg.setCarry(result < 0);

        return result & 0xFF;
    }

    public int add16(int a, int b) {
        int result = a + b;

        reg.setSubtract(false);
        reg.setHalfCarry(((a & 0x0FFF) + (b & 0x0FFF)) > 0x0FFF);
        reg.setCarry(result > 0xFFFF);

        return result & 0xFFFF;
    }

    public int and8(int a, int b) {
        int result = (a & b) & 0xFF;
        reg.setZero(result == 0);
        reg.setSubtract(false);
        reg.setHalfCarry(true);
        reg.setCarry(false);
        return result;
    }

    public int or8(int a, int b) {
        int result = (a | b) & 0xFF;
        reg.setZero(result == 0);
        reg.setSubtract(false);
        reg.setHalfCarry(false);
        reg.setCarry(false);
        return result;
    }

    public int xor8(int a, int b) {
        int result = (a ^ b) & 0xFF;
        reg.setZero(result == 0);
        reg.setSubtract(false);
        reg.setHalfCarry(false);
        reg.setCarry(false);
        return result;
    }

    public int inc8(int val) {
        int result = (val + 1) & 0xFF;
        reg.setZero(result == 0);
        reg.setSubtract(false);
        reg.setHalfCarry((val & 0x0F) == 0x0F);
        return result;
    }

    public int dec8(int val) {
        int result = (val - 1) & 0xFF;
        reg.setZero(result == 0);
        reg.setSubtract(true);
        reg.setHalfCarry((val & 0x0F) == 0);
        return result;
    }

    public void daa() {
        int a = reg.a;
        int correction = 0;

        if (reg.isHalfCarry() || (!reg.isSubtract() && (a & 0x0F) > 9)) {
            correction |= 0x06;
        }

        if (reg.isCarry() || (!reg.isSubtract() && a > 0x99)) {
            correction |= 0x60;
            reg.setCarry(true);
        }

        a += reg.isSubtract() ? -correction : correction;
        a &= 0xFF;

        reg.setZero(a == 0);
        reg.setHalfCarry(false);
        reg.a = a;
    }
}
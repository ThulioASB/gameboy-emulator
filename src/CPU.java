public class CPU {
    public int a, f, b, c, d, e, h, l;
    public int sp;
    public int pc;
    private final MMU mmu;

    private static final int FLAG_Z = 0x80;
    private static final int FLAG_N = 0x40;
    private static final int FLAG_H = 0x20;
    private static final int FLAG_C = 0x10;

    public boolean ime = false;
    public boolean isHalted = false;

    public CPU(MMU mmu) {
        this.mmu = mmu;
        reset();
    }

    public boolean isFlagZ() { return (f & FLAG_Z) != 0; }
    public boolean isFlagN() { return (f & FLAG_N) != 0; }
    public boolean isFlagH() { return (f & FLAG_H) != 0; }
    public boolean isFlagC() { return (f & FLAG_C) != 0; }

    public void setFlags(boolean z, boolean n, boolean h, boolean c) {
        int newF = 0;
        if (z) newF |= FLAG_Z;
        if (n) newF |= FLAG_N;
        if (h) newF |= FLAG_H;
        if (c) newF |= FLAG_C;
        this.f = newF;
    }

    public void setF(int value) {
        this.f = value & 0xF0;
    }

    public void reset() {
        a = 0x01;
        f = 0xB0;
        b = 0x00;
        c = 0x13;
        d = 0x00;
        e = 0xD8;
        h = 0x01;
        l = 0x4D;
        sp = 0xFFFE;
        pc = 0x0100;

        mmu.writeByte(0xFF40, 0x91);
        mmu.writeByte(0xFF47, 0xFC);
    }

    public int getBC() { return BitUtils.combineBytes(b, c); }
    public void setBC(int value) {
        b = (value >> 8) & 0xFF;
        c = value & 0xFF;
    }

    public int getDE() { return BitUtils.combineBytes(d, e); }
    public void setDE(int value) {
        d = (value >> 8) & 0xFF;
        e = value & 0xFF;
    }

    public int getHL() { return BitUtils.combineBytes(h, l); }
    public void setHL(int value) {
        h = (value >> 8) & 0xFF;
        l = value & 0xFF;
    }

    public int step() {
        checkInterrupts();
        if (isHalted) {
            return 4;
        }

        int opcode = mmu.readByte(pc);
        pc = BitUtils.toUnsigned16(pc + 1);
        return executeOpcode(opcode);
    }

    public void checkInterrupts() {
        int ie = mmu.readByte(0xFFFF);
        int ifReg = mmu.readByte(0xFF0F);
        int pending = ie & ifReg;

        if (pending != 0) {
            isHalted = false;
            if (ime) {
                if ((pending & 0x01) != 0) {
                    ime = false;
                    mmu.writeByte(0xFF0F, ifReg & ~0x01);
                    pushStack(pc);
                    pc = 0x0040;
                    return;
                }
                if ((pending & 0x02) != 0) {
                    ime = false;
                    mmu.writeByte(0xFF0F, ifReg & ~0x02);
                    pushStack(pc);
                    pc = 0x0048;
                    return;
                }
                if ((pending & 0x04) != 0) {
                    ime = false;
                    mmu.writeByte(0xFF0F, ifReg & ~0x04);
                    pushStack(pc);
                    pc = 0x0050;
                    return;
                }
                if ((pending & 0x10) != 0) {
                    ime = false;
                    mmu.writeByte(0xFF0F, ifReg & ~0x10);
                    pushStack(pc);
                    pc = 0x0060;
                    return;
                }
            }
        }
    }

    private int executeOpcode(int opcode) {
        switch (opcode) {
            case 0x00: return 4;
            case 0x01: setBC(readImmediateWord()); return 12;
            case 0x11: setDE(readImmediateWord()); return 12;
            case 0x21: setHL(readImmediateWord()); return 12;
            case 0x31: sp = readImmediateWord(); return 12;
            case 0x3E: a = readImmediateByte(); return 8;
            case 0x06: b = readImmediateByte(); return 8;
            case 0x0E: c = readImmediateByte(); return 8;
            case 0x16: d = readImmediateByte(); return 8;
            case 0x1E: e = readImmediateByte(); return 8;
            case 0x26: h = readImmediateByte(); return 8;
            case 0x2E: l = readImmediateByte(); return 8;

            case 0x08: {
                int addr = readImmediateWord();
                mmu.writeByte(addr, sp & 0xFF);
                mmu.writeByte(addr + 1, (sp >> 8) & 0xFF);
                return 20;
            }

            case 0x10: readImmediateByte(); return 4; // STOP

            case 0x80: addA(b); return 4;
            case 0x81: addA(c); return 4;
            case 0x82: addA(d); return 4;
            case 0x83: addA(e); return 4;
            case 0x84: addA(h); return 4;
            case 0x85: addA(l); return 4;
            case 0x86: addA(mmu.readByte(getHL())); return 8;
            case 0x87: addA(a); return 4;

            case 0x88: adcA(b); return 4;
            case 0x89: adcA(c); return 4;
            case 0x8A: adcA(d); return 4;
            case 0x8B: adcA(e); return 4;
            case 0x8C: adcA(h); return 4;
            case 0x8D: adcA(l); return 4;
            case 0x8E: adcA(mmu.readByte(getHL())); return 8;
            case 0x8F: adcA(a); return 4;

            case 0x90: subA(b); return 4;
            case 0x91: subA(c); return 4;
            case 0x92: subA(d); return 4;
            case 0x93: subA(e); return 4;
            case 0x94: subA(h); return 4;
            case 0x95: subA(l); return 4;
            case 0x96: subA(mmu.readByte(getHL())); return 8;
            case 0x97: subA(a); return 4;

            case 0x98: sbcA(b); return 4;
            case 0x99: sbcA(c); return 4;
            case 0x9A: sbcA(d); return 4;
            case 0x9B: sbcA(e); return 4;
            case 0x9C: sbcA(h); return 4;
            case 0x9D: sbcA(l); return 4;
            case 0x9E: sbcA(mmu.readByte(getHL())); return 8;
            case 0x9F: sbcA(a); return 4;

            case 0xA0: andA(b); return 4;
            case 0xA1: andA(c); return 4;
            case 0xA2: andA(d); return 4;
            case 0xA3: andA(e); return 4;
            case 0xA4: andA(h); return 4;
            case 0xA5: andA(l); return 4;
            case 0xA6: andA(mmu.readByte(getHL())); return 8;
            case 0xA7: andA(a); return 4;

            case 0xA8: xorA(b); return 4;
            case 0xA9: xorA(c); return 4;
            case 0xAA: xorA(d); return 4;
            case 0xAB: xorA(e); return 4;
            case 0xAC: xorA(h); return 4;
            case 0xAD: xorA(l); return 4;
            case 0xAE: xorA(mmu.readByte(getHL())); return 8;
            case 0xAF: xorA(a); return 4;

            case 0xB0: orA(b); return 4;
            case 0xB1: orA(c); return 4;
            case 0xB2: orA(d); return 4;
            case 0xB3: orA(e); return 4;
            case 0xB4: orA(h); return 4;
            case 0xB5: orA(l); return 4;
            case 0xB6: orA(mmu.readByte(getHL())); return 8;
            case 0xB7: orA(a); return 4;

            case 0xB8: cpA(b); return 4;
            case 0xB9: cpA(c); return 4;
            case 0xBA: cpA(d); return 4;
            case 0xBB: cpA(e); return 4;
            case 0xBC: cpA(h); return 4;
            case 0xBD: cpA(l); return 4;
            case 0xBE: cpA(mmu.readByte(getHL())); return 8;
            case 0xBF: cpA(a); return 4;

            case 0xC6: addA(readImmediateByte()); return 8;
            case 0xCE: adcA(readImmediateByte()); return 8;
            case 0xD6: subA(readImmediateByte()); return 8;
            case 0xDE: sbcA(readImmediateByte()); return 8;
            case 0xE6: andA(readImmediateByte()); return 8;
            case 0xEE: xorA(readImmediateByte()); return 8;
            case 0xF6: orA(readImmediateByte()); return 8;
            case 0xFE: cpA(readImmediateByte()); return 8;

            case 0xC5: pushStack(getBC()); return 16;
            case 0xD5: pushStack(getDE()); return 16;
            case 0xE5: pushStack(getHL()); return 16;
            case 0xF5: pushStack(BitUtils.combineBytes(a, f)); return 16;
            case 0xC1: setBC(popStack()); return 12;
            case 0xD1: setDE(popStack()); return 12;
            case 0xE1: setHL(popStack()); return 12;
            case 0xF1: {
                int af = popStack();
                this.a = (af >> 8) & 0xFF;
                setF(af & 0xFF);
                return 12;
            }
            case 0xC3: pc = readImmediateWord(); return 16;
            case 0x18: {
                byte offset = (byte) readImmediateByte();
                pc = BitUtils.toUnsigned16(pc + offset);
                return 12;
            }
            case 0x20: {
                byte offset = (byte) readImmediateByte();
                if (!isFlagZ()) { pc = BitUtils.toUnsigned16(pc + offset); return 12; }
                return 8;
            }
            case 0x28: {
                byte offset = (byte) readImmediateByte();
                if (isFlagZ()) { pc = BitUtils.toUnsigned16(pc + offset); return 12; }
                return 8;
            }
            case 0x30: {
                byte offset = (byte) readImmediateByte();
                if (!isFlagC()) { pc = BitUtils.toUnsigned16(pc + offset); return 12; }
                return 8;
            }
            case 0x38: {
                byte offset = (byte) readImmediateByte();
                if (isFlagC()) { pc = BitUtils.toUnsigned16(pc + offset); return 12; }
                return 8;
            }
            case 0xCD: {
                int targetAddress = readImmediateWord();
                pushStack(pc);
                pc = targetAddress;
                return 24;
            }
            case 0xC9: pc = popStack(); return 16;
            case 0xD9: pc = popStack(); ime = true; return 16;
            case 0xC0: if (!isFlagZ()) { pc = popStack(); return 20; } return 8;
            case 0xC8: if (isFlagZ())  { pc = popStack(); return 20; } return 8;
            case 0xD0: if (!isFlagC()) { pc = popStack(); return 20; } return 8;
            case 0xD8: if (isFlagC())  { pc = popStack(); return 20; } return 8;
            case 0xC2: { int addr = readImmediateWord(); if (!isFlagZ()) { pc = addr; return 16; } return 12; }
            case 0xCA: { int addr = readImmediateWord(); if (isFlagZ())  { pc = addr; return 16; } return 12; }
            case 0xD2: { int addr = readImmediateWord(); if (!isFlagC()) { pc = addr; return 16; } return 12; }
            case 0xDA: { int addr = readImmediateWord(); if (isFlagC())  { pc = addr; return 16; } return 12; }
            case 0xC4: { int addr = readImmediateWord(); if (!isFlagZ()) { pushStack(pc); pc = addr; return 24; } return 12; }
            case 0xCC: { int addr = readImmediateWord(); if (isFlagZ())  { pushStack(pc); pc = addr; return 24; } return 12; }
            case 0xDC: { int addr = readImmediateWord(); if (isFlagC())  { pushStack(pc); pc = addr; return 24; } return 12; }
            case 0xD4: { int addr = readImmediateWord(); if (!isFlagC()) { pushStack(pc); pc = addr; return 24; } return 12; }

            case 0xCB: return executeCBOpcode(readImmediateByte());
            case 0xE0: mmu.writeByte(0xFF00 + readImmediateByte(), a); return 12;
            case 0xF0: a = mmu.readByte(0xFF00 + readImmediateByte()); return 12;
            case 0xE2: mmu.writeByte(0xFF00 + c, a); return 8;
            case 0xF2: a = mmu.readByte(0xFF00 + c); return 8;
            case 0xEA: mmu.writeByte(readImmediateWord(), a); return 16;
            case 0xFA: a = mmu.readByte(readImmediateWord()); return 16;

            case 0x40: return 4;
            case 0x41: b = c; return 4;
            case 0x42: b = d; return 4;
            case 0x43: b = e; return 4;
            case 0x44: b = h; return 4;
            case 0x45: b = l; return 4;
            case 0x46: b = mmu.readByte(getHL()); return 8;
            case 0x47: b = a; return 4;
            case 0x48: c = b; return 4;
            case 0x49: return 4;
            case 0x4A: c = d; return 4;
            case 0x4B: c = e; return 4;
            case 0x4C: c = h; return 4;
            case 0x4D: c = l; return 4;
            case 0x4E: c = mmu.readByte(getHL()); return 8;
            case 0x4F: c = a; return 4;
            case 0x50: d = b; return 4;
            case 0x51: d = c; return 4;
            case 0x52: return 4;
            case 0x53: d = e; return 4;
            case 0x54: d = h; return 4;
            case 0x55: d = l; return 4;
            case 0x56: d = mmu.readByte(getHL()); return 8;
            case 0x57: d = a; return 4;
            case 0x58: e = b; return 4;
            case 0x59: e = c; return 4;
            case 0x5A: e = d; return 4;
            case 0x5B: return 4;
            case 0x5C: e = h; return 4;
            case 0x5D: e = l; return 4;
            case 0x5E: e = mmu.readByte(getHL()); return 8;
            case 0x5F: e = a; return 4;
            case 0x60: h = b; return 4;
            case 0x61: h = c; return 4;
            case 0x62: h = d; return 4;
            case 0x63: h = e; return 4;
            case 0x64: return 4;
            case 0x65: h = l; return 4;
            case 0x66: h = mmu.readByte(getHL()); return 8;
            case 0x67: h = a; return 4;
            case 0x68: l = b; return 4;
            case 0x69: l = c; return 4;
            case 0x6A: l = d; return 4;
            case 0x6B: l = e; return 4;
            case 0x6C: l = h; return 4;
            case 0x6D: return 4;
            case 0x6E: l = mmu.readByte(getHL()); return 8;
            case 0x6F: l = a; return 4;
            case 0x70: mmu.writeByte(getHL(), b); return 8;
            case 0x71: mmu.writeByte(getHL(), c); return 8;
            case 0x72: mmu.writeByte(getHL(), d); return 8;
            case 0x73: mmu.writeByte(getHL(), e); return 8;
            case 0x74: mmu.writeByte(getHL(), h); return 8;
            case 0x75: mmu.writeByte(getHL(), l); return 8;
            case 0x76: isHalted = true; return 4;
            case 0x77: mmu.writeByte(getHL(), a); return 8;
            case 0x78: a = b; return 4;
            case 0x79: a = c; return 4;
            case 0x7A: a = d; return 4;
            case 0x7B: a = e; return 4;
            case 0x7C: a = h; return 4;
            case 0x7D: a = l; return 4;
            case 0x7E: a = mmu.readByte(getHL()); return 8;
            case 0x7F: return 4;

            case 0x02: mmu.writeByte(getBC(), a); return 8;
            case 0x12: mmu.writeByte(getDE(), a); return 8;
            case 0x0A: a = mmu.readByte(getBC()); return 8;
            case 0x1A: a = mmu.readByte(getDE()); return 8;
            case 0x22: mmu.writeByte(getHL(), a); setHL(BitUtils.toUnsigned16(getHL() + 1)); return 8;
            case 0x32: mmu.writeByte(getHL(), a); setHL(BitUtils.toUnsigned16(getHL() - 1)); return 8;
            case 0x2A: a = mmu.readByte(getHL()); setHL(BitUtils.toUnsigned16(getHL() + 1)); return 8;
            case 0x3A: a = mmu.readByte(getHL()); setHL(BitUtils.toUnsigned16(getHL() - 1)); return 8;
            case 0x36: mmu.writeByte(getHL(), readImmediateByte()); return 12;

            case 0x04: b = inc8(b); return 4;
            case 0x05: b = dec8(b); return 4;
            case 0x0C: c = inc8(c); return 4;
            case 0x0D: c = dec8(c); return 4;
            case 0x14: d = inc8(d); return 4;
            case 0x15: d = dec8(d); return 4;
            case 0x1C: e = inc8(e); return 4;
            case 0x1D: e = dec8(e); return 4;
            case 0x24: h = inc8(h); return 4;
            case 0x25: h = dec8(h); return 4;
            case 0x2C: l = inc8(l); return 4;
            case 0x2D: l = dec8(l); return 4;
            case 0x3C: a = inc8(a); return 4;
            case 0x3D: a = dec8(a); return 4;
            case 0x34: mmu.writeByte(getHL(), inc8(mmu.readByte(getHL()))); return 12;
            case 0x35: mmu.writeByte(getHL(), dec8(mmu.readByte(getHL()))); return 12;

            case 0x03: setBC(BitUtils.toUnsigned16(getBC() + 1)); return 8;
            case 0x0B: setBC(BitUtils.toUnsigned16(getBC() - 1)); return 8;
            case 0x13: setDE(BitUtils.toUnsigned16(getDE() + 1)); return 8;
            case 0x1B: setDE(BitUtils.toUnsigned16(getDE() - 1)); return 8;
            case 0x23: setHL(BitUtils.toUnsigned16(getHL() + 1)); return 8;
            case 0x2B: setHL(BitUtils.toUnsigned16(getHL() - 1)); return 8;
            case 0x33: sp = BitUtils.toUnsigned16(sp + 1); return 8;
            case 0x3B: sp = BitUtils.toUnsigned16(sp - 1); return 8;

            case 0x09: addHL(getBC()); return 8;
            case 0x19: addHL(getDE()); return 8;
            case 0x29: addHL(getHL()); return 8;
            case 0x39: addHL(sp); return 8;

            case 0x37: setFlags(isFlagZ(), false, false, true); return 4; // SCF
            case 0x3F: setFlags(isFlagZ(), false, false, !isFlagC()); return 4; // CCF

            case 0xE8: { // ADD SP, e
                byte off = (byte) readImmediateByte();
                int result = sp + off;
                boolean flagH = ((sp & 0x0F) + (off & 0x0F)) > 0x0F;
                boolean flagC = ((sp & 0xFF) + (off & 0xFF)) > 0xFF;
                sp = BitUtils.toUnsigned16(result);
                setFlags(false, false, flagH, flagC);
                return 16;
            }
            case 0xF8: { // LD HL, SP+e
                byte off = (byte) readImmediateByte();
                int result = sp + off;
                boolean flagH = ((sp & 0x0F) + (off & 0x0F)) > 0x0F;
                boolean flagC = ((sp & 0xFF) + (off & 0xFF)) > 0xFF;
                setHL(BitUtils.toUnsigned16(result));
                setFlags(false, false, flagH, flagC);
                return 12;
            }

            case 0xF3: ime = false; return 4;
            case 0xFB: ime = true; return 4;
            case 0xE9: pc = getHL(); return 4;
            case 0xF9: sp = getHL(); return 8;

            case 0x07: {
                int rlcaCarry = (a & 0x80) != 0 ? 1 : 0;
                a = ((a << 1) & 0xFF) | rlcaCarry;
                setFlags(false, false, false, rlcaCarry == 1);
                return 4;
            }
            case 0x0F: {
                int rrcaCarry = (a & 0x01) != 0 ? 1 : 0;
                a = (a >> 1) | (rrcaCarry << 7);
                setFlags(false, false, false, rrcaCarry == 1);
                return 4;
            }
            case 0x17: {
                int rlaOldCarry = isFlagC() ? 1 : 0;
                int rlaNewCarry = (a & 0x80) != 0 ? 1 : 0;
                a = ((a << 1) & 0xFF) | rlaOldCarry;
                setFlags(false, false, false, rlaNewCarry == 1);
                return 4;
            }
            case 0x1F: {
                int rraOldCarry = isFlagC() ? 1 : 0;
                int rraNewCarry = (a & 0x01) != 0 ? 1 : 0;
                a = (a >> 1) | (rraOldCarry << 7);
                setFlags(false, false, false, rraNewCarry == 1);
                return 4;
            }
            case 0x27: daa(); return 4;
            case 0x2F: a = (~a) & 0xFF; setFlags(isFlagZ(), true, true, isFlagC()); return 4;

            case 0xC7: pushStack(pc); pc = 0x0000; return 16;
            case 0xCF: pushStack(pc); pc = 0x0008; return 16;
            case 0xD7: pushStack(pc); pc = 0x0010; return 16;
            case 0xDF: pushStack(pc); pc = 0x0018; return 16;
            case 0xE7: pushStack(pc); pc = 0x0020; return 16;
            case 0xEF: pushStack(pc); pc = 0x0028; return 16;
            case 0xF7: pushStack(pc); pc = 0x0030; return 16;
            case 0xFF: pushStack(pc); pc = 0x0038; return 16;

            default:
                System.out.printf("Opcode não implementado: 0x%02X no PC: 0x%04X%n", opcode, pc - 1);
                return 4;
        }
    }

    private int readImmediateByte() {
        int value = mmu.readByte(pc);
        pc = BitUtils.toUnsigned16(pc + 1);
        return value;
    }

    private int readImmediateWord() {
        int low = readImmediateByte();
        int high = readImmediateByte();
        return BitUtils.combineBytes(high, low);
    }

    private void addA(int value) {
        int result = a + value;
        boolean flagZ = (result & 0xFF) == 0;
        boolean flagN = false;
        boolean flagH = ((a & 0x0F) + (value & 0x0F)) > 0x0F;
        boolean flagC = result > 0xFF;
        this.a = result & 0xFF;
        setFlags(flagZ, flagN, flagH, flagC);
    }

    private void adcA(int value) {
        int carry = isFlagC() ? 1 : 0;
        int result = a + value + carry;
        boolean flagZ = (result & 0xFF) == 0;
        boolean flagN = false;
        boolean flagH = ((a & 0x0F) + (value & 0x0F) + carry) > 0x0F;
        boolean flagC = result > 0xFF;
        this.a = result & 0xFF;
        setFlags(flagZ, flagN, flagH, flagC);
    }

    private void subA(int value) {
        int result = a - value;
        boolean flagZ = (result & 0xFF) == 0;
        boolean flagN = true;
        boolean flagH = ((a & 0x0F) - (value & 0x0F)) < 0;
        boolean flagC = result < 0;
        this.a = result & 0xFF;
        setFlags(flagZ, flagN, flagH, flagC);
    }

    private void sbcA(int value) {
        int carry = isFlagC() ? 1 : 0;
        int result = a - value - carry;
        boolean flagZ = (result & 0xFF) == 0;
        boolean flagN = true;
        boolean flagH = ((a & 0x0F) - (value & 0x0F) - carry) < 0;
        boolean flagC = result < 0;
        this.a = result & 0xFF;
        setFlags(flagZ, flagN, flagH, flagC);
    }

    private void andA(int value) {
        this.a = (this.a & value) & 0xFF;
        setFlags(this.a == 0, false, true, false);
    }

    private void xorA(int value) {
        this.a = (this.a ^ value) & 0xFF;
        setFlags(this.a == 0, false, false, false);
    }

    private void orA(int value) {
        this.a = (this.a | value) & 0xFF;
        setFlags(this.a == 0, false, false, false);
    }

    private void cpA(int value) {
        int result = a - value;
        boolean flagZ = (result & 0xFF) == 0;
        boolean flagN = true;
        boolean flagH = ((a & 0x0F) - (value & 0x0F)) < 0;
        boolean flagC = result < 0;
        setFlags(flagZ, flagN, flagH, flagC);
    }

    public void pushStack(int value) {
        sp = BitUtils.toUnsigned16(sp - 1);
        mmu.writeByte(sp, (value >> 8) & 0xFF);
        sp = BitUtils.toUnsigned16(sp - 1);
        mmu.writeByte(sp, value & 0xFF);
    }

    public int popStack() {
        int low = mmu.readByte(sp);
        sp = BitUtils.toUnsigned16(sp + 1);
        int high = mmu.readByte(sp);
        sp = BitUtils.toUnsigned16(sp + 1);
        return BitUtils.combineBytes(high, low);
    }

    private int executeCBOpcode(int cbOpcode) {
        int regIndex = cbOpcode & 0x07;
        int bit = (cbOpcode >> 3) & 0x07;
        int val = getRegisterByCBIndex(regIndex);
        int cycles = (regIndex == 6) ? 16 : 8;

        int group = cbOpcode & 0xC0;
        switch (group) {
            case 0x00: {
                int subOp = (cbOpcode >> 3) & 0x07;
                boolean carryOut = false;
                switch (subOp) {
                    case 0:
                        carryOut = (val & 0x80) != 0;
                        val = ((val << 1) & 0xFF) | (carryOut ? 1 : 0);
                        setFlags(val == 0, false, false, carryOut);
                        break;
                    case 1:
                        carryOut = (val & 0x01) != 0;
                        val = (val >> 1) | (carryOut ? 0x80 : 0);
                        setFlags(val == 0, false, false, carryOut);
                        break;
                    case 2:
                        boolean oldCarry = isFlagC();
                        carryOut = (val & 0x80) != 0;
                        val = ((val << 1) & 0xFF) | (oldCarry ? 1 : 0);
                        setFlags(val == 0, false, false, carryOut);
                        break;
                    case 3:
                        boolean oldCarryR = isFlagC();
                        carryOut = (val & 0x01) != 0;
                        val = (val >> 1) | (oldCarryR ? 0x80 : 0);
                        setFlags(val == 0, false, false, carryOut);
                        break;
                    case 4:
                        carryOut = (val & 0x80) != 0;
                        val = (val << 1) & 0xFF;
                        setFlags(val == 0, false, false, carryOut);
                        break;
                    case 5:
                        carryOut = (val & 0x01) != 0;
                        val = (val >> 1) | (val & 0x80);
                        setFlags(val == 0, false, false, carryOut);
                        break;
                    case 6:
                        val = ((val & 0x0F) << 4) | ((val & 0xF0) >> 4);
                        setFlags(val == 0, false, false, false);
                        break;
                    case 7:
                        carryOut = (val & 0x01) != 0;
                        val = val >> 1;
                        setFlags(val == 0, false, false, carryOut);
                        break;
                }
                setRegisterByCBIndex(regIndex, val);
                return cycles;
            }
            case 0x40: {
                boolean isZero = (val & (1 << bit)) == 0;
                setFlags(isZero, false, true, isFlagC());
                return (regIndex == 6) ? 12 : 8;
            }
            case 0x80: {
                val &= ~(1 << bit);
                setRegisterByCBIndex(regIndex, val);
                return cycles;
            }
            case 0xC0: {
                val |= (1 << bit);
                setRegisterByCBIndex(regIndex, val);
                return cycles;
            }
        }
        return 8;
    }

    private int inc8(int value) {
        int result = (value + 1) & 0xFF;
        boolean flagZ = result == 0;
        boolean flagH = (value & 0x0F) == 0x0F;
        setFlags(flagZ, false, flagH, isFlagC());
        return result;
    }

    private int dec8(int value) {
        int result = (value - 1) & 0xFF;
        boolean flagZ = result == 0;
        boolean flagH = (value & 0x0F) == 0x00;
        setFlags(flagZ, true, flagH, isFlagC());
        return result;
    }

    private void addHL(int value) {
        int currentHL = getHL();
        int result = currentHL + value;
        boolean flagH = ((currentHL & 0x0FFF) + (value & 0x0FFF)) > 0x0FFF;
        boolean flagC = result > 0xFFFF;
        setHL(result & 0xFFFF);
        setFlags(isFlagZ(), false, flagH, flagC);
    }

    private void daa() {
        int correction = 0;
        boolean setCarry = isFlagC();

        if (isFlagH() || (!isFlagN() && (a & 0x0F) > 0x09)) {
            correction |= 0x06;
        }
        if (isFlagC() || (!isFlagN() && a > 0x99)) {
            correction |= 0x60;
            setCarry = true;
        }

        if (isFlagN()) {
            a = (a - correction) & 0xFF;
        } else {
            a = (a + correction) & 0xFF;
        }

        setFlags(a == 0, isFlagN(), false, setCarry);
    }

    private int getRegisterByCBIndex(int index) {
        switch (index) {
            case 0: return b;
            case 1: return c;
            case 2: return d;
            case 3: return e;
            case 4: return h;
            case 5: return l;
            case 6: return mmu.readByte(getHL());
            case 7: return a;
            default: return 0;
        }
    }

    private void setRegisterByCBIndex(int index, int value) {
        value &= 0xFF;
        switch (index) {
            case 0: b = value; break;
            case 1: c = value; break;
            case 2: d = value; break;
            case 3: e = value; break;
            case 4: h = value; break;
            case 5: l = value; break;
            case 6: mmu.writeByte(getHL(), value); break;
            case 7: a = value; break;
        }
    }
}
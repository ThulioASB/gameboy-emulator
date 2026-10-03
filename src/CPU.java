public class CPU {
    private final Registers reg;
    private final MMU mmu;
    private final ALU alu;
    private boolean ime = false;
    private boolean pendingIme = false;
    private boolean halted = false;

    public CPU(Registers reg, MMU mmu) {
        this.reg = reg;
        this.mmu = mmu;
        this.alu = new ALU(reg);

        this.reg.a = 0x01;
        this.reg.f = 0xB0;
        this.reg.b = 0x00;
        this.reg.c = 0x13;
        this.reg.d = 0x00;
        this.reg.e = 0xD8;
        this.reg.h = 0x01;
        this.reg.l = 0x4D;
        this.reg.sp = 0xFFFE;
        this.reg.pc = 0x0100;
    }

    private int fetchByte() {
        int b = mmu.readByte(reg.pc);
        reg.pc = (reg.pc + 1) & 0xFFFF;
        return b;
    }

    private int fetchWord() {
        int low = fetchByte();
        int high = fetchByte();
        return (high << 8) | low;
    }

    private void pushWord(int value) {
        reg.sp = (reg.sp - 1) & 0xFFFF;
        mmu.writeByte(reg.sp, (value >> 8) & 0xFF);
        reg.sp = (reg.sp - 1) & 0xFFFF;
        mmu.writeByte(reg.sp, value & 0xFF);
    }

    private int popWord() {
        int low = mmu.readByte(reg.sp);
        reg.sp = (reg.sp + 1) & 0xFFFF;
        int high = mmu.readByte(reg.sp);
        reg.sp = (reg.sp + 1) & 0xFFFF;
        return (high << 8) | low;
    }

    public int handleInterrupts() {
        int ifReg = mmu.readByte(0xFF0F);
        int ieReg = mmu.readByte(0xFFFF);
        int pending = ifReg & ieReg & 0x1F;

        if (pending == 0) return 0;

        if (halted) {
            halted = false;
        }

        if (!ime) {
            return 0;
        }

        ime = false;
        for (int bit = 0; bit < 5; bit++) {
            if ((pending & (1 << bit)) != 0) {
                mmu.writeByte(0xFF0F, ifReg & ~(1 << bit));
                pushWord(reg.pc);
                reg.pc = 0x0040 + (bit * 8);
                return 5;
            }
        }
        return 0;
    }

    public int step() {
        if (pendingIme) {
            ime = true;
            pendingIme = false;
        }

        int interruptCycles = handleInterrupts();
        if (interruptCycles > 0) return interruptCycles;

        if (halted) return 1;

        int opcode = fetchByte();

        switch (opcode) {
            case 0x00 -> { return 1; }
            case 0x01 -> { reg.setBC(fetchWord()); return 3; }
            case 0x02 -> { mmu.writeByte(reg.getBC(), reg.a); return 2; }
            case 0x03 -> { reg.setBC((reg.getBC() + 1) & 0xFFFF); return 2; }
            case 0x04 -> { reg.b = alu.inc8(reg.b); return 1; }
            case 0x05 -> { reg.b = alu.dec8(reg.b); return 1; }
            case 0x06 -> { reg.b = fetchByte(); return 2; }
            case 0x07 -> {
                int c = (reg.a & 0x80) >> 7;
                reg.a = ((reg.a << 1) | c) & 0xFF;
                reg.setZero(false); reg.setSubtract(false); reg.setHalfCarry(false); reg.setCarry(c == 1);
                return 1;
            }
            case 0x08 -> { mmu.writeWord(fetchWord(), reg.sp); return 5; }
            case 0x09 -> { reg.setHL(alu.add16(reg.getHL(), reg.getBC())); return 2; }
            case 0x0A -> { reg.a = mmu.readByte(reg.getBC()); return 2; }
            case 0x0B -> { reg.setBC((reg.getBC() - 1) & 0xFFFF); return 2; }
            case 0x0C -> { reg.c = alu.inc8(reg.c); return 1; }
            case 0x0D -> { reg.c = alu.dec8(reg.c); return 1; }
            case 0x0E -> { reg.c = fetchByte(); return 2; }
            case 0x0F -> {
                int c = reg.a & 0x01;
                reg.a = ((reg.a >> 1) | (c << 7)) & 0xFF;
                reg.setZero(false); reg.setSubtract(false); reg.setHalfCarry(false); reg.setCarry(c == 1);
                return 1;
            }
            case 0x11 -> { reg.setDE(fetchWord()); return 3; }
            case 0x12 -> { mmu.writeByte(reg.getDE(), reg.a); return 2; }
            case 0x13 -> { reg.setDE((reg.getDE() + 1) & 0xFFFF); return 2; }
            case 0x14 -> { reg.d = alu.inc8(reg.d); return 1; }
            case 0x15 -> { reg.d = alu.dec8(reg.d); return 1; }
            case 0x16 -> { reg.d = fetchByte(); return 2; }
            case 0x18 -> { byte offset = (byte) fetchByte(); reg.pc = (reg.pc + offset) & 0xFFFF; return 3; }
            case 0x19 -> { reg.setHL(alu.add16(reg.getHL(), reg.getDE())); return 2; }
            case 0x1A -> { reg.a = mmu.readByte(reg.getDE()); return 2; }
            case 0x1B -> { reg.setDE((reg.getDE() - 1) & 0xFFFF); return 2; }
            case 0x1C -> { reg.e = alu.inc8(reg.e); return 1; }
            case 0x1D -> { reg.e = alu.dec8(reg.e); return 1; }
            case 0x1E -> { reg.e = fetchByte(); return 2; }
            case 0x20 -> {
                byte offset = (byte) fetchByte();
                if (!reg.isZero()) { reg.pc = (reg.pc + offset) & 0xFFFF; return 3; }
                return 2;
            }
            case 0x21 -> { reg.setHL(fetchWord()); return 3; }
            case 0x22 -> { mmu.writeByte(reg.getHL(), reg.a); reg.setHL((reg.getHL() + 1) & 0xFFFF); return 2; }
            case 0x23 -> { reg.setHL((reg.getHL() + 1) & 0xFFFF); return 2; }
            case 0x24 -> { reg.h = alu.inc8(reg.h); return 1; }
            case 0x25 -> { reg.h = alu.dec8(reg.h); return 1; }
            case 0x26 -> { reg.h = fetchByte(); return 2; }
            case 0x27 -> { alu.daa(); return 1; }
            case 0x28 -> {
                byte offset = (byte) fetchByte();
                if (reg.isZero()) { reg.pc = (reg.pc + offset) & 0xFFFF; return 3; }
                return 2;
            }
            case 0x29 -> { reg.setHL(alu.add16(reg.getHL(), reg.getHL())); return 2; }
            case 0x2A -> { reg.a = mmu.readByte(reg.getHL()); reg.setHL((reg.getHL() + 1) & 0xFFFF); return 2; }
            case 0x2B -> { reg.setHL((reg.getHL() - 1) & 0xFFFF); return 2; }
            case 0x2C -> { reg.l = alu.inc8(reg.l); return 1; }
            case 0x2D -> { reg.l = alu.dec8(reg.l); return 1; }
            case 0x2E -> { reg.l = fetchByte(); return 2; }
            case 0x2F -> { reg.a = (~reg.a) & 0xFF; reg.setSubtract(true); reg.setHalfCarry(true); return 1; }
            case 0x30 -> {
                byte offset = (byte) fetchByte();
                if (!reg.isCarry()) { reg.pc = (reg.pc + offset) & 0xFFFF; return 3; }
                return 2;
            }
            case 0x31 -> { reg.sp = fetchWord(); return 3; }
            case 0x32 -> { mmu.writeByte(reg.getHL(), reg.a); reg.setHL((reg.getHL() - 1) & 0xFFFF); return 2; }
            case 0x33 -> { reg.sp = (reg.sp + 1) & 0xFFFF; return 2; }
            case 0x34 -> { mmu.writeByte(reg.getHL(), alu.inc8(mmu.readByte(reg.getHL()))); return 3; }
            case 0x35 -> { mmu.writeByte(reg.getHL(), alu.dec8(mmu.readByte(reg.getHL()))); return 3; }
            case 0x36 -> { mmu.writeByte(reg.getHL(), fetchByte()); return 3; }
            case 0x37 -> { reg.setSubtract(false); reg.setHalfCarry(false); reg.setCarry(true); return 1; }
            case 0x38 -> {
                byte offset = (byte) fetchByte();
                if (reg.isCarry()) { reg.pc = (reg.pc + offset) & 0xFFFF; return 3; }
                return 2;
            }
            case 0x39 -> { reg.setHL(alu.add16(reg.getHL(), reg.sp)); return 2; }
            case 0x3A -> { reg.a = mmu.readByte(reg.getHL()); reg.setHL((reg.getHL() - 1) & 0xFFFF); return 2; }
            case 0x3B -> { reg.sp = (reg.sp - 1) & 0xFFFF; return 2; }
            case 0x3C -> { reg.a = alu.inc8(reg.a); return 1; }
            case 0x3D -> { reg.a = alu.dec8(reg.a); return 1; }
            case 0x3E -> { reg.a = fetchByte(); return 2; }
            case 0x3F -> { reg.setSubtract(false); reg.setHalfCarry(false); reg.setCarry(!reg.isCarry()); return 1; }

            case 0x40 -> { reg.b = reg.b; return 1; }
            case 0x41 -> { reg.b = reg.c; return 1; }
            case 0x42 -> { reg.b = reg.d; return 1; }
            case 0x43 -> { reg.b = reg.e; return 1; }
            case 0x44 -> { reg.b = reg.h; return 1; }
            case 0x45 -> { reg.b = reg.l; return 1; }
            case 0x46 -> { reg.b = mmu.readByte(reg.getHL()); return 2; }
            case 0x47 -> { reg.b = reg.a; return 1; }
            case 0x48 -> { reg.c = reg.b; return 1; }
            case 0x49 -> { reg.c = reg.c; return 1; }
            case 0x4A -> { reg.c = reg.d; return 1; }
            case 0x4B -> { reg.c = reg.e; return 1; }
            case 0x4C -> { reg.c = reg.h; return 1; }
            case 0x4D -> { reg.c = reg.l; return 1; }
            case 0x4E -> { reg.c = mmu.readByte(reg.getHL()); return 2; }
            case 0x4F -> { reg.c = reg.a; return 1; }

            case 0x50 -> { reg.d = reg.b; return 1; }
            case 0x51 -> { reg.d = reg.c; return 1; }
            case 0x52 -> { reg.d = reg.d; return 1; }
            case 0x53 -> { reg.d = reg.e; return 1; }
            case 0x54 -> { reg.d = reg.h; return 1; }
            case 0x55 -> { reg.d = reg.l; return 1; }
            case 0x56 -> { reg.d = mmu.readByte(reg.getHL()); return 2; }
            case 0x57 -> { reg.d = reg.a; return 1; }
            case 0x58 -> { reg.e = reg.b; return 1; }
            case 0x59 -> { reg.e = reg.c; return 1; }
            case 0x5A -> { reg.e = reg.d; return 1; }
            case 0x5B -> { reg.e = reg.e; return 1; }
            case 0x5C -> { reg.e = reg.h; return 1; }
            case 0x5D -> { reg.e = reg.l; return 1; }
            case 0x5E -> { reg.e = mmu.readByte(reg.getHL()); return 2; }
            case 0x5F -> { reg.e = reg.a; return 1; }

            case 0x60 -> { reg.h = reg.b; return 1; }
            case 0x61 -> { reg.h = reg.c; return 1; }
            case 0x62 -> { reg.h = reg.d; return 1; }
            case 0x63 -> { reg.h = reg.e; return 1; }
            case 0x64 -> { reg.h = reg.h; return 1; }
            case 0x65 -> { reg.h = reg.l; return 1; }
            case 0x66 -> { reg.h = mmu.readByte(reg.getHL()); return 2; }
            case 0x67 -> { reg.h = reg.a; return 1; }
            case 0x68 -> { reg.l = reg.b; return 1; }
            case 0x69 -> { reg.l = reg.c; return 1; }
            case 0x6A -> { reg.l = reg.d; return 1; }
            case 0x6B -> { reg.l = reg.e; return 1; }
            case 0x6C -> { reg.l = reg.h; return 1; }
            case 0x6D -> { reg.l = reg.l; return 1; }
            case 0x6E -> { reg.l = mmu.readByte(reg.getHL()); return 2; }
            case 0x6F -> { reg.l = reg.a; return 1; }

            case 0x70 -> { mmu.writeByte(reg.getHL(), reg.b); return 2; }
            case 0x71 -> { mmu.writeByte(reg.getHL(), reg.c); return 2; }
            case 0x72 -> { mmu.writeByte(reg.getHL(), reg.d); return 2; }
            case 0x73 -> { mmu.writeByte(reg.getHL(), reg.e); return 2; }
            case 0x74 -> { mmu.writeByte(reg.getHL(), reg.h); return 2; }
            case 0x75 -> { mmu.writeByte(reg.getHL(), reg.l); return 2; }
            case 0x76 -> {
                halted = true;
                return 1;
            }
            case 0x77 -> { mmu.writeByte(reg.getHL(), reg.a); return 2; }
            case 0x78 -> { reg.a = reg.b; return 1; }
            case 0x79 -> { reg.a = reg.c; return 1; }
            case 0x7A -> { reg.a = reg.d; return 1; }
            case 0x7B -> { reg.a = reg.e; return 1; }
            case 0x7C -> { reg.a = reg.h; return 1; }
            case 0x7D -> { reg.a = reg.l; return 1; }
            case 0x7E -> { reg.a = mmu.readByte(reg.getHL()); return 2; }
            case 0x7F -> { reg.a = reg.a; return 1; }

            case 0x80 -> { reg.a = alu.add8(reg.a, reg.b, false); return 1; }
            case 0x81 -> { reg.a = alu.add8(reg.a, reg.c, false); return 1; }
            case 0x82 -> { reg.a = alu.add8(reg.a, reg.d, false); return 1; }
            case 0x83 -> { reg.a = alu.add8(reg.a, reg.e, false); return 1; }
            case 0x84 -> { reg.a = alu.add8(reg.a, reg.h, false); return 1; }
            case 0x85 -> { reg.a = alu.add8(reg.a, reg.l, false); return 1; }
            case 0x86 -> { reg.a = alu.add8(reg.a, mmu.readByte(reg.getHL()), false); return 2; }
            case 0x87 -> { reg.a = alu.add8(reg.a, reg.a, false); return 1; }
            case 0x88 -> { reg.a = alu.add8(reg.a, reg.b, true); return 1; }
            case 0x89 -> { reg.a = alu.add8(reg.a, reg.c, true); return 1; }
            case 0x8A -> { reg.a = alu.add8(reg.a, reg.d, true); return 1; }
            case 0x8B -> { reg.a = alu.add8(reg.a, reg.e, true); return 1; }
            case 0x8C -> { reg.a = alu.add8(reg.a, reg.h, true); return 1; }
            case 0x8D -> { reg.a = alu.add8(reg.a, reg.l, true); return 1; }
            case 0x8E -> { reg.a = alu.add8(reg.a, mmu.readByte(reg.getHL()), true); return 2; }
            case 0x8F -> { reg.a = alu.add8(reg.a, reg.a, true); return 1; }

            case 0x90 -> { reg.a = alu.sub8(reg.a, reg.b, false); return 1; }
            case 0x91 -> { reg.a = alu.sub8(reg.a, reg.c, false); return 1; }
            case 0x92 -> { reg.a = alu.sub8(reg.a, reg.d, false); return 1; }
            case 0x93 -> { reg.a = alu.sub8(reg.a, reg.e, false); return 1; }
            case 0x94 -> { reg.a = alu.sub8(reg.a, reg.h, false); return 1; }
            case 0x95 -> { reg.a = alu.sub8(reg.a, reg.l, false); return 1; }
            case 0x96 -> { reg.a = alu.sub8(reg.a, mmu.readByte(reg.getHL()), false); return 2; }
            case 0x97 -> { reg.a = alu.sub8(reg.a, reg.a, false); return 1; }
            case 0x98 -> { reg.a = alu.sub8(reg.a, reg.b, true); return 1; }
            case 0x99 -> { reg.a = alu.sub8(reg.a, reg.c, true); return 1; }
            case 0x9A -> { reg.a = alu.sub8(reg.a, reg.d, true); return 1; }
            case 0x9B -> { reg.a = alu.sub8(reg.a, reg.e, true); return 1; }
            case 0x9C -> { reg.a = alu.sub8(reg.a, reg.h, true); return 1; }
            case 0x9D -> { reg.a = alu.sub8(reg.a, reg.l, true); return 1; }
            case 0x9E -> { reg.a = alu.sub8(reg.a, mmu.readByte(reg.getHL()), true); return 2; }
            case 0x9F -> { reg.a = alu.sub8(reg.a, reg.a, true); return 1; }

            case 0xA0 -> { reg.a = alu.and8(reg.a, reg.b); return 1; }
            case 0xA1 -> { reg.a = alu.and8(reg.a, reg.c); return 1; }
            case 0xA2 -> { reg.a = alu.and8(reg.a, reg.d); return 1; }
            case 0xA3 -> { reg.a = alu.and8(reg.a, reg.e); return 1; }
            case 0xA4 -> { reg.a = alu.and8(reg.a, reg.h); return 1; }
            case 0xA5 -> { reg.a = alu.and8(reg.a, reg.l); return 1; }
            case 0xA6 -> { reg.a = alu.and8(reg.a, mmu.readByte(reg.getHL())); return 2; }
            case 0xA7 -> { reg.a = alu.and8(reg.a, reg.a); return 1; }
            case 0xA8 -> { reg.a = alu.xor8(reg.a, reg.b); return 1; }
            case 0xA9 -> { reg.a = alu.xor8(reg.a, reg.c); return 1; }
            case 0xAA -> { reg.a = alu.xor8(reg.a, reg.d); return 1; }
            case 0xAB -> { reg.a = alu.xor8(reg.a, reg.e); return 1; }
            case 0xAC -> { reg.a = alu.xor8(reg.a, reg.h); return 1; }
            case 0xAD -> { reg.a = alu.xor8(reg.a, reg.l); return 1; }
            case 0xAE -> { reg.a = alu.xor8(reg.a, mmu.readByte(reg.getHL())); return 2; }
            case 0xAF -> { reg.a = alu.xor8(reg.a, reg.a); return 1; }

            case 0xB0 -> { reg.a = alu.or8(reg.a, reg.b); return 1; }
            case 0xB1 -> { reg.a = alu.or8(reg.a, reg.c); return 1; }
            case 0xB2 -> { reg.a = alu.or8(reg.a, reg.d); return 1; }
            case 0xB3 -> { reg.a = alu.or8(reg.a, reg.e); return 1; }
            case 0xB4 -> { reg.a = alu.or8(reg.a, reg.h); return 1; }
            case 0xB5 -> { reg.a = alu.or8(reg.a, reg.l); return 1; }
            case 0xB6 -> { reg.a = alu.or8(reg.a, mmu.readByte(reg.getHL())); return 2; }
            case 0xB7 -> { reg.a = alu.or8(reg.a, reg.a); return 1; }
            case 0xB8 -> { alu.sub8(reg.a, reg.b, false); return 1; }
            case 0xB9 -> { alu.sub8(reg.a, reg.c, false); return 1; }
            case 0xBA -> { alu.sub8(reg.a, reg.d, false); return 1; }
            case 0xBB -> { alu.sub8(reg.a, reg.e, false); return 1; }
            case 0xBC -> { alu.sub8(reg.a, reg.h, false); return 1; }
            case 0xBD -> { alu.sub8(reg.a, reg.l, false); return 1; }
            case 0xBE -> { alu.sub8(reg.a, mmu.readByte(reg.getHL()), false); return 2; }
            case 0xBF -> { alu.sub8(reg.a, reg.a, false); return 1; }

            case 0xC0 -> { if (!reg.isZero()) { reg.pc = popWord(); return 5; } return 2; }
            case 0xC1 -> { reg.setBC(popWord()); return 3; }
            case 0xC2 -> { int addr = fetchWord(); if (!reg.isZero()) { reg.pc = addr; return 4; } return 3; }
            case 0xC3 -> { reg.pc = fetchWord(); return 4; }
            case 0xC4 -> { int addr = fetchWord(); if (!reg.isZero()) { pushWord(reg.pc); reg.pc = addr; return 6; } return 3; }
            case 0xC5 -> { pushWord(reg.getBC()); return 4; }
            case 0xC6 -> { reg.a = alu.add8(reg.a, fetchByte(), false); return 2; }
            case 0xC7 -> { pushWord(reg.pc); reg.pc = 0x0000; return 4; }
            case 0xC8 -> { if (reg.isZero()) { reg.pc = popWord(); return 5; } return 2; }
            case 0xC9 -> { reg.pc = popWord(); return 4; }
            case 0xCA -> { int addr = fetchWord(); if (reg.isZero()) { reg.pc = addr; return 4; } return 3; }
            case 0xCB -> { return executeCBOpcode(); }
            case 0xCC -> { int addr = fetchWord(); if (reg.isZero()) { pushWord(reg.pc); reg.pc = addr; return 6; } return 3; }
            case 0xCD -> { int addr = fetchWord(); pushWord(reg.pc); reg.pc = addr; return 6; }
            case 0xCE -> { reg.a = alu.add8(reg.a, fetchByte(), true); return 2; }
            case 0xCF -> { pushWord(reg.pc); reg.pc = 0x0008; return 4; }

            case 0xD0 -> { if (!reg.isCarry()) { reg.pc = popWord(); return 5; } return 2; }
            case 0xD1 -> { reg.setDE(popWord()); return 3; }
            case 0xD2 -> { int addr = fetchWord(); if (!reg.isCarry()) { reg.pc = addr; return 4; } return 3; }
            case 0xD4 -> { int addr = fetchWord(); if (!reg.isCarry()) { pushWord(reg.pc); reg.pc = addr; return 6; } return 3; }
            case 0xD5 -> { pushWord(reg.getDE()); return 4; }
            case 0xD6 -> { reg.a = alu.sub8(reg.a, fetchByte(), false); return 2; }
            case 0xD7 -> { pushWord(reg.pc); reg.pc = 0x0010; return 4; }
            case 0xD8 -> { if (reg.isCarry()) { reg.pc = popWord(); return 5; } return 2; }
            case 0xD9 -> { reg.pc = popWord(); ime = true; return 4; }
            case 0xDA -> { int addr = fetchWord(); if (reg.isCarry()) { reg.pc = addr; return 4; } return 3; }
            case 0xDC -> { int addr = fetchWord(); if (reg.isCarry()) { pushWord(reg.pc); reg.pc = addr; return 6; } return 3; }
            case 0xDE -> { reg.a = alu.sub8(reg.a, fetchByte(), true); return 2; }
            case 0xDF -> { pushWord(reg.pc); reg.pc = 0x0018; return 4; }

            case 0xE0 -> { mmu.writeByte(0xFF00 + fetchByte(), reg.a); return 3; }
            case 0xE1 -> { reg.setHL(popWord()); return 3; }
            case 0xE2 -> { mmu.writeByte(0xFF00 + reg.c, reg.a); return 2; }
            case 0xE5 -> { pushWord(reg.getHL()); return 4; }
            case 0xE6 -> { reg.a = alu.and8(reg.a, fetchByte()); return 2; }
            case 0xE7 -> { pushWord(reg.pc); reg.pc = 0x0020; return 4; }
            case 0xE9 -> { reg.pc = reg.getHL(); return 1; }
            case 0xEA -> { mmu.writeByte(fetchWord(), reg.a); return 4; }
            case 0xEE -> { reg.a = alu.xor8(reg.a, fetchByte()); return 2; }
            case 0xEF -> { pushWord(reg.pc); reg.pc = 0x0028; return 4; }

            case 0xF0 -> { reg.a = mmu.readByte(0xFF00 + fetchByte()); return 3; }
            case 0xF1 -> { reg.setAF(popWord()); return 3; }
            case 0xF2 -> { reg.a = mmu.readByte(0xFF00 + reg.c); return 2; }
            case 0xF3 -> { ime = false; return 1; }
            case 0xF5 -> { pushWord(reg.getAF()); return 4; }
            case 0xF6 -> { reg.a = alu.or8(reg.a, fetchByte()); return 2; }
            case 0xF7 -> { pushWord(reg.pc); reg.pc = 0x0030; return 4; }
            case 0xF8 -> {
                byte b = (byte) fetchByte();
                int result = reg.sp + b;
                reg.setZero(false); reg.setSubtract(false);
                reg.setHalfCarry(((reg.sp & 0x0F) + (b & 0x0F)) > 0x0F);
                reg.setCarry(((reg.sp & 0xFF) + (b & 0xFF)) > 0xFF);
                reg.setHL(result & 0xFFFF);
                return 3;
            }
            case 0xF9 -> { reg.sp = reg.getHL(); return 2; }
            case 0xFA -> { reg.a = mmu.readByte(fetchWord()); return 4; }
            case 0xFB -> { pendingIme = true; return 1; }
            case 0xFE -> { alu.sub8(reg.a, fetchByte(), false); return 2; }
            case 0xFF -> { pushWord(reg.pc); reg.pc = 0x0038; return 4; }

            default -> { halted = true; return 1; }
        }
    }

    private int executeCBOpcode() {
        int cbOpcode = fetchByte();
        int regIndex = cbOpcode & 0x07;
        int bitIndex = (cbOpcode >> 3) & 0x07;

        int val = getRegByIndex(regIndex);

        if (cbOpcode >= 0x40 && cbOpcode <= 0x7F) {
            boolean isBitSet = (val & (1 << bitIndex)) != 0;
            reg.setZero(!isBitSet);
            reg.setSubtract(false);
            reg.setHalfCarry(true);
            return (regIndex == 6) ? 3 : 2;
        } else if (cbOpcode >= 0x80 && cbOpcode <= 0xBF) {
            val &= ~(1 << bitIndex);
            setRegByIndex(regIndex, val);
            return (regIndex == 6) ? 4 : 2;
        } else if (cbOpcode >= 0xC0 && cbOpcode <= 0xFF) {
            val |= (1 << bitIndex);
            setRegByIndex(regIndex, val);
            return (regIndex == 6) ? 4 : 2;
        }

        switch (cbOpcode & 0xF8) {
            case 0x30 -> {
                int high = (val & 0xF0) >> 4;
                int low = (val & 0x0F) << 4;
                val = low | high;
                reg.setZero(val == 0); reg.setSubtract(false); reg.setHalfCarry(false); reg.setCarry(false);
            }
        }

        setRegByIndex(regIndex, val);
        return (regIndex == 6) ? 4 : 2;
    }

    private int getRegByIndex(int index) {
        return switch (index) {
            case 0 -> reg.b;
            case 1 -> reg.c;
            case 2 -> reg.d;
            case 3 -> reg.e;
            case 4 -> reg.h;
            case 5 -> reg.l;
            case 6 -> mmu.readByte(reg.getHL());
            case 7 -> reg.a;
            default -> 0;
        };
    }

    private void setRegByIndex(int index, int val) {
        val &= 0xFF;
        switch (index) {
            case 0 -> reg.b = val;
            case 1 -> reg.c = val;
            case 2 -> reg.d = val;
            case 3 -> reg.e = val;
            case 4 -> reg.h = val;
            case 5 -> reg.l = val;
            case 6 -> mmu.writeByte(reg.getHL(), val);
            case 7 -> reg.a = val;
        }
    }
}
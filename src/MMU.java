public class MMU {
    private final int[] memory = new int[0x10000];
    private MBC mbc;
    private Timer timer;
    private Joypad joypad;
    private APU apu;

    public MMU() {
        memory[0xFF00] = 0xCF;
        memory[0xFF05] = 0x00;
        memory[0xFF06] = 0x00;
        memory[0xFF07] = 0x00;
        memory[0xFF0F] = 0xE1;
        memory[0xFF10] = 0x80;
        memory[0xFF11] = 0xBF;
        memory[0xFF12] = 0xF3;
        memory[0xFF14] = 0xBF;
        memory[0xFF24] = 0x77;
        memory[0xFF25] = 0xF3;
        memory[0xFF26] = 0xF1;
        memory[0xFF40] = 0x91;
        memory[0xFF44] = 0x00;
        memory[0xFF47] = 0xFC;
        memory[0xFF48] = 0xFF;
        memory[0xFF49] = 0xFF;
        memory[0xFFFF] = 0x00;
    }

    public void setJoypad(Joypad joypad) {
        this.joypad = joypad;
    }

    public MBC getMbc() {
        return mbc;
    }

    public void loadRom(byte[] romData) {
        this.mbc = MBCFactory.createMBC(romData);
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public void setApu(APU apu) {
        this.apu = apu;
    }

    public int readByteDirectly(int address) {
        return memory[address & 0xFFFF] & 0xFF;
    }

    public int readByte(int address) {
        address &= 0xFFFF;

        if (address == 0xFF00 && joypad != null) {
            return joypad.getState();
        }

        if (address == 0xFF0F) {
            return memory[0xFF0F] | 0xE0;
        }

        if (address >= 0xFF00 && address <= 0xFF7F) {
            return memory[address] & 0xFF;
        }

        if (address <= 0x7FFF || (address >= 0xA000 && address <= 0xBFFF)) {
            return (mbc != null) ? mbc.readByte(address) : memory[address] & 0xFF;
        }

        return memory[address] & 0xFF;
    }

    public void writeByte(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;

        if (address <= 0x7FFF || (address >= 0xA000 && address <= 0xBFFF)) {
            if (mbc != null) {
                mbc.writeByte(address, value);
            }
            return;
        }

        if (address == 0xFF04) {
            if (timer != null) {
                timer.resetDiv();
            } else {
                memory[0xFF04] = 0;
            }
            return;
        }

        if (address == 0xFF44) {
            return;
        }

        if (address == 0xFF46) {
            memory[0xFF46] = value;
            performOAMDMA(value);
            return;
        }

        if (address == 0xFF0F) {
            memory[0xFF0F] = value & 0x1F;
            return;
        }

        if (address == 0xFF02) {
            memory[address] = value;
            if ((value & 0x81) == 0x81) {
                System.out.print((char) memory[0xFF01]);
                if (memory[0xFF01] == '\n') {
                    System.out.flush();
                }
                memory[address] = value & 0x7F;
                memory[0xFF0F] |= 0x08;
            }
            return;
        }

        memory[address] = value;
        if (apu != null) {
            apu.writeLengthRegister(address, value);
            apu.writeRegister(address, value);
        }
    }

    public void writeWord(int address, int value) {
        writeByte(address, value & 0xFF);
        writeByte(address + 1, (value >> 8) & 0xFF);
    }

    private void performOAMDMA(int sourceHighByte) {
        int sourceAddress = (sourceHighByte & 0xFF) << 8;
        for (int i = 0; i < 0xA0; i++) {
            memory[0xFE00 + i] = readByte(sourceAddress + i);
        }
    }

    public void setDivDirectly(int value) {
        memory[0xFF04] = value & 0xFF;
    }

    public void setLyDirectly(int value) {
        memory[0xFF44] = value & 0xFF;
    }

    public void setStatDirectly(int value) {
        memory[0xFF41] = value & 0xFF;
    }
}
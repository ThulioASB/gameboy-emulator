public class MMU {
    private final int[] memory = new int[0x10000];
    private MBC mbc;
    private Timer timer;
    private Joypad joypad;

    public MMU() {
        memory[0xFF00] = 0xCF;
        memory[0xFF05] = 0x00;
        memory[0xFF06] = 0x00;
        memory[0xFF07] = 0x00;
        memory[0xFF10] = 0x80;
        memory[0xFF11] = 0xBF;
        memory[0xFF12] = 0xF3;
        memory[0xFF14] = 0xBF;
        memory[0xFF24] = 0x77;
        memory[0xFF25] = 0xF3;
        memory[0xFF26] = 0xF1;
        memory[0xFF40] = 0x91;
        memory[0xFF47] = 0xFC;
        memory[0xFF48] = 0xFF;
        memory[0xFF49] = 0xFF;
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

    public int readByteDirectly(int address) {
        return memory[address & 0xFFFF] & 0xFF;
    }

    public int readByte(int address) {
        address &= 0xFFFF;

        if (address == 0xFF00 && joypad != null) {
            return joypad.getState();
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
            memory[0xFF44] = 0;
            return;
        }

        if (address == 0xFF46) {
            memory[0xFF46] = value;
            performOAMDMA(value);
            return;
        }

        memory[address] = value;
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
}
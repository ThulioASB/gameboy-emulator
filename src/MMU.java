public class MMU {
    private final int[] memory = new int[0x10000];
    private int timerCounter = 0;
    private int divCounter = 0;

    public int btnDirections = 0x0F;
    public int btnButtons    = 0x0F;

    public MMU() {
        memory[0xFF05] = 0x00;
        memory[0xFF06] = 0x00;
        memory[0xFF07] = 0x00;
        memory[0xFF10] = 0x80;
        memory[0xFF11] = 0xBF;
        memory[0xFF12] = 0xF3;
        memory[0xFF14] = 0xBF;
        memory[0xFF16] = 0x3F;
        memory[0xFF19] = 0xBF;
        memory[0xFF1A] = 0x7F;
        memory[0xFF1B] = 0xFF;
        memory[0xFF1C] = 0x9F;
        memory[0xFF20] = 0xFF;
        memory[0xFF23] = 0xBF;
        memory[0xFF24] = 0x77;
        memory[0xFF25] = 0xF3;
        memory[0xFF26] = 0xF1;
        memory[0xFF40] = 0x91;
        memory[0xFF41] = 0x85;
        memory[0xFF42] = 0x00;
        memory[0xFF43] = 0x00;
        memory[0xFF45] = 0x00;
        memory[0xFF47] = 0xFC;
        memory[0xFF48] = 0xFF;
        memory[0xFF49] = 0xFF;
        memory[0xFF4A] = 0x00;
        memory[0xFF4B] = 0x00;
        memory[0xFFFF] = 0x01;
    }

    public int readByte(int address) {
        address = BitUtils.toUnsigned16(address);
        if (address == 0xFF00) {
            int p1 = memory[0xFF00];
            int result = p1 | 0x0F;
            if ((p1 & 0x10) == 0) {
                result &= (btnDirections & 0x0F);
            }
            if ((p1 & 0x20) == 0) {
                result &= (btnButtons & 0x0F);
            }
            return result | 0xC0;
        }
        if (address == 0xFF0F) return memory[0xFF0F] | 0xE0;
        
        // Garante a leitura correta dos registradores da PPU
        if (address >= 0xFF40 && address <= 0xFF4B) {
            return memory[address];
        }

        return memory[address];
    }

    public void writeByte(int address, int value) {
        address = BitUtils.toUnsigned16(address);
        if (address < 0x8000) {
            return;
        }
        if (address == 0xFF00) {
            memory[0xFF00] = (value & 0x30);
            return;
        }
        if (address == 0xFF46) { // OAM DMA Transfer
            int sourceAddress = (value & 0xFF) << 8;
            for (int i = 0; i < 160; i++) {
                memory[0xFE00 + i] = memory[sourceAddress + i];
            }
            memory[address] = BitUtils.toUnsigned8(value);
            return;
        }
        if (address == 0xFF44) {
            memory[address] = 0;
            return;
        }
        if (address == 0xFF04) {
            memory[address] = 0;
            divCounter = 0;
            return;
        }
        memory[address] = BitUtils.toUnsigned8(value);
    }

    public void setLYDirect(int line) {
        memory[0xFF44] = BitUtils.toUnsigned8(line);
    }

    public void loadRom(byte[] romData) {
        for (int i = 0; i < romData.length && i < memory.length; i++) {
            memory[i] = BitUtils.toUnsigned8(romData[i]);
        }
    }

    public void step(int cycles) {
        divCounter += cycles;
        if (divCounter >= 256) {
            divCounter -= 256;
            memory[0xFF04] = (memory[0xFF04] + 1) & 0xFF;
        }

        int tac = memory[0xFF07];
        boolean timerEnabled = (tac & 0x04) != 0;
        if (timerEnabled) {
            timerCounter += cycles;
            int threshold = switch (tac & 0x03) {
                case 0 -> 1024;
                case 1 -> 16;
                case 2 -> 64;
                case 3 -> 256;
                default -> 1024;
            };
            while (timerCounter >= threshold) {
                timerCounter -= threshold;
                int tima = memory[0xFF05] + 1;
                if (tima > 0xFF) {
                    memory[0xFF05] = memory[0xFF06];
                    int ifReg = memory[0xFF0F];
                    memory[0xFF0F] = ifReg | 0x04;
                } else {
                    memory[0xFF05] = tima;
                }
            }
        }
    }
}
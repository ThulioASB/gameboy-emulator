public class MBC3 implements MBC, Saveable {
    private final byte[] rom;
    private final byte[] ram = new byte[0x8000];

    private boolean ramEnabled = false;
    private int romBank = 1;
    private int ramBank = 0;

    public MBC3(byte[] rom) {
        this.rom = rom;
    }

    @Override
    public int readByte(int address) {
        if (address >= 0x0000 && address <= 0x3FFF) {
            return (address < rom.length) ? (rom[address] & 0xFF) : 0xFF;
        }

        if (address >= 0x4000 && address <= 0x7FFF) {
            int targetAddress = (romBank * 0x4000) + (address - 0x4000);
            return targetAddress < rom.length ? (rom[targetAddress] & 0xFF) : 0xFF;
        }

        if (address >= 0xA000 && address <= 0xBFFF) {
            if (!ramEnabled) return 0xFF;
            if (ramBank <= 0x03) {
                int targetAddress = (ramBank * 0x2000) + (address - 0xA000);
                return ram[targetAddress & 0x7FFF] & 0xFF;
            }
        }

        return 0xFF;
    }

    @Override
    public void writeByte(int address, int value) {
        value &= 0xFF;

        if (address >= 0x0000 && address <= 0x1FFF) {
            ramEnabled = (value & 0x0F) == 0x0A;
        } else if (address >= 0x2000 && address <= 0x3FFF) {
            int bank = value & 0x7F;
            if (bank == 0) bank = 1;
            romBank = bank;
        } else if (address >= 0x4000 && address <= 0x5FFF) {
            ramBank = value;
        } else if (address >= 0xA000 && address <= 0xBFFF) {
            if (ramEnabled && ramBank <= 0x03) {
                int targetAddress = (ramBank * 0x2000) + (address - 0xA000);
                ram[targetAddress & 0x7FFF] = (byte) value;
            }
        }
    }

    @Override
    public byte[] getRamData() {
        return ram;
    }

    @Override
    public void loadRamData(byte[] data) {
        System.arraycopy(data, 0, ram, 0, Math.min(data.length, ram.length));
    }
}
public class ROMOnly implements MBC {
    private final byte[] rom;

    public ROMOnly(byte[] rom) {
        this.rom = rom;
    }

    @Override
    public int readByte(int address) {
        if (address >= 0x0000 && address <= 0x7FFF) {
            return (address < rom.length) ? (rom[address] & 0xFF) : 0xFF;
        }
        return 0xFF;
    }

    @Override
    public void writeByte(int address, int value) {
    }
}
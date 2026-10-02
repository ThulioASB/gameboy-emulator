public class MBCFactory {
    public static MBC createMBC(byte[] rom) {
        if (rom.length < 0x0150) {
            return new ROMOnly(rom);
        }

        int type = rom[0x0147] & 0xFF;

        return switch (type) {
            case 0x01, 0x02, 0x03 -> new MBC1(rom);
            case 0x0F, 0x10, 0x11, 0x12, 0x13 -> new MBC3(rom);
            default -> new ROMOnly(rom);
        };
    }
}
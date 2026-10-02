public class BitUtils {
    public static int toUnsigned8(int value) {
        return value & 0xFF;
    }

    public static int toUnsigned16(int value) {
        return value & 0xFFFF;
    }

    public static int combineBytes(int high, int low) {
        return ((high & 0xFF) << 8) | (low & 0xFF);
    }
}
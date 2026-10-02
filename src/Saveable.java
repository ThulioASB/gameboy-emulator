public interface Saveable {
    byte[] getRamData();
    void loadRamData(byte[] data);
}
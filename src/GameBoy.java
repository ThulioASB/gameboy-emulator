public class GameBoy {
    private final Registers reg = new Registers();
    private final MMU mmu = new MMU();
    private final CPU cpu = new CPU(reg, mmu);
    private final Timer timer = new Timer(mmu);
    private final PPU ppu = new PPU(mmu);
    private final APU apu = new APU(mmu);

    public GameBoy() {
        mmu.setTimer(timer);
        mmu.setApu(apu);
    }

    public void runFrame() {
        int cyclesThisFrame = 0;
        final int MAX_CYCLES_PER_FRAME = 70224;

        while (cyclesThisFrame < MAX_CYCLES_PER_FRAME) {
            int mCycles = cpu.step();
            int tCycles = mCycles * 4;

            timer.step(tCycles);
            ppu.step(tCycles);
            apu.step(tCycles);

            cyclesThisFrame += tCycles;
        }
    }

    public int[] getPixels() {
        return ppu.getScreenBuffer();
    }
}
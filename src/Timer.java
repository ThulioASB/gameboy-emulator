public class Timer {
    private final MMU mmu;

    private int internalCounter = 0;

    public Timer(MMU mmu) {
        this.mmu = mmu;
    }

    public void step(int tCycles) {
        int tac = mmu.readByte(0xFF07);
        boolean timerEnabled = (tac & 0x04) != 0;
        int timerMask = 1 << getBitForFrequency(tac & 0x03);

        for (int cycle = 0; cycle < tCycles; cycle++) {
            boolean previousSignal = timerEnabled && (internalCounter & timerMask) != 0;
            internalCounter = (internalCounter + 1) & 0xFFFF;
            boolean currentSignal = timerEnabled && (internalCounter & timerMask) != 0;
            if (previousSignal && !currentSignal) {
                incrementTima();
            }
        }

        mmu.setDivDirectly((internalCounter >> 8) & 0xFF);
    }

    private void incrementTima() {
        int tima = mmu.readByte(0xFF05);

        if (tima == 0xFF) {
            int tma = mmu.readByte(0xFF06);
            mmu.writeByte(0xFF05, tma);

            int ifReg = mmu.readByte(0xFF0F);
            mmu.writeByte(0xFF0F, ifReg | 0x04);
        } else {
            mmu.writeByte(0xFF05, tima + 1);
        }
    }

    private int getBitForFrequency(int clockSelect) {
        switch (clockSelect) {
            case 0: return 9;
            case 1: return 3;
            case 2: return 5;
            case 3: return 7; 
            default: return 9;
        }
    }

    public void resetDiv() {
        internalCounter = 0;
        mmu.setDivDirectly(0);
    }
}
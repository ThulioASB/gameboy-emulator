import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class APU {
    private final MMU mmu;
    private SourceDataLine audioLine;

    private int ch1FrequencyCounter = 0;
    private int ch1DutyIndex = 0;
    private int ch1Volume = 0;
    private boolean ch1Enabled = false;

    private static final int[][] DUTY_PATTERNS = {
        {0, 0, 0, 0, 0, 0, 0, 1},
        {1, 0, 0, 0, 0, 0, 0, 1},
        {1, 0, 0, 0, 0, 1, 1, 1},
        {0, 1, 1, 1, 1, 1, 1, 0}
    };

    private static final int SAMPLE_RATE = 44100;
    private int sampleCounter = 0;

    public APU(MMU mmu) {
        this.mmu = mmu;
        initAudioLine();
    }

    private void initAudioLine() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, 8, 1, false, true);
            audioLine = AudioSystem.getSourceDataLine(format);
            audioLine.open(format, 4096);
            audioLine.start();
        } catch (Exception ignored) {
        }
    }

    public void step(int tCycles) {
        boolean soundMasterEnable = (mmu.readByte(0xFF26) & 0x80) != 0;
        if (!soundMasterEnable) {
            ch1Enabled = false;
            return;
        }

        updateChannel1(tCycles);

        sampleCounter += tCycles;
        if (sampleCounter >= (4194304 / SAMPLE_RATE)) {
            sampleCounter -= (4194304 / SAMPLE_RATE);
            generateAndSendSample();
        }
    }

    private void updateChannel1(int tCycles) {
        int nr13 = mmu.readByte(0xFF13);
        int nr14 = mmu.readByte(0xFF14);

        if ((nr14 & 0x80) != 0) {
            ch1Enabled = true;
            ch1Volume = (mmu.readByte(0xFF12) >> 4) & 0x0F;
            mmu.writeByte(0xFF14, nr14 & 0x7F);
        }

        int freqRaw = nr13 | ((nr14 & 0x07) << 8);
        int period = (2048 - freqRaw) * 4;
        if (period <= 0) period = 2048;

        ch1FrequencyCounter += tCycles;
        if (ch1FrequencyCounter >= period) {
            ch1FrequencyCounter -= period;
            ch1DutyIndex = (ch1DutyIndex + 1) % 8;
        }
    }

    private void generateAndSendSample() {
        if (audioLine == null || !audioLine.isOpen()) return;

        // Evita bloqueio na escrita se o buffer da placa de som estiver cheio
        if (audioLine.available() < 128) return;

        int nr11 = mmu.readByte(0xFF11);
        int dutyType = (nr11 >> 6) & 0x03;

        int sample = 0;
        if (ch1Enabled) {
            int bit = DUTY_PATTERNS[dutyType][ch1DutyIndex];
            sample = bit * ch1Volume * 8;
        }

        byte[] buffer = new byte[]{(byte) (sample & 0xFF)};
        audioLine.write(buffer, 0, 1);
    }
}
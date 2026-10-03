import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class APU {
    private static final int CPU_CLOCK = 4_194_304;
    private static final int SAMPLE_RATE = 44_100;
    private static final int SAMPLES_PER_BUFFER = 512;
    private static final int[] DUTY_PATTERNS = {
        0b00000001,
        0b10000001,
        0b10000111,
        0b01111110
    };
    private static final int[] NOISE_DIVISORS = {8, 16, 32, 48, 64, 80, 96, 112};

    private final MMU mmu;
    private final byte[] audioBuffer = new byte[SAMPLES_PER_BUFFER * 2];
    private SourceDataLine audioLine;
    private int bufferedBytes;
    private long sampleAccumulator;
    private int frameSequencerCycles;
    private int frameSequencerStep;

    private boolean pulse1Enabled;
    private int pulse1Timer;
    private int pulse1DutyPosition;
    private int pulse1Length;
    private int pulse1Volume;
    private int pulse1EnvelopeTimer;
    private int pulse1SweepTimer;
    private int pulse1SweepFrequency;
    private boolean pulse1SweepEnabled;

    private boolean pulse2Enabled;
    private int pulse2Timer;
    private int pulse2DutyPosition;
    private int pulse2Length;
    private int pulse2Volume;
    private int pulse2EnvelopeTimer;

    private boolean waveEnabled;
    private int waveTimer;
    private int wavePosition;
    private int waveLength;

    private boolean noiseEnabled;
    private long noiseTimer;
    private int noiseLength;
    private int noiseVolume;
    private int noiseEnvelopeTimer;
    private int noiseLfsr = 0x7FFF;

    public APU(MMU mmu) {
        this.mmu = mmu;
        initAudioLine();
    }

    private void initAudioLine() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try {
            audioLine = AudioSystem.getSourceDataLine(format);
            audioLine.open(format, SAMPLES_PER_BUFFER * 4);
            audioLine.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            audioLine = null;
            System.err.println("Audio output unavailable: " + e.getMessage());
        }
    }

    public void step(int tCycles) {
        if ((mmu.readByte(0xFF26) & 0x80) == 0) {
            disableChannels();
            sampleAccumulator = 0;
            bufferedBytes = 0;
            return;
        }

        clockFrameSequencer(tCycles);
        sampleAccumulator += (long) tCycles * SAMPLE_RATE;
        while (sampleAccumulator >= CPU_CLOCK) {
            sampleAccumulator -= CPU_CLOCK;
            advanceOscillators();
            appendSample(mixSample());
        }
    }

    private void disableChannels() {
        pulse1Enabled = false;
        pulse2Enabled = false;
        waveEnabled = false;
        noiseEnabled = false;
        frameSequencerCycles = 0;
        frameSequencerStep = 0;
    }

    private void clockFrameSequencer(int tCycles) {
        frameSequencerCycles += tCycles;
        while (frameSequencerCycles >= 8192) {
            frameSequencerCycles -= 8192;
            if ((frameSequencerStep & 1) == 0) {
                clockLengths();
            }
            if (frameSequencerStep == 2 || frameSequencerStep == 6) {
                clockSweep();
            }
            if (frameSequencerStep == 7) {
                clockEnvelopes();
            }
            frameSequencerStep = (frameSequencerStep + 1) & 7;
        }
    }

    private void clockLengths() {
        if (pulse1Enabled && (mmu.readByte(0xFF14) & 0x40) != 0 && pulse1Length > 0 && --pulse1Length == 0) {
            pulse1Enabled = false;
        }
        if (pulse2Enabled && (mmu.readByte(0xFF19) & 0x40) != 0 && pulse2Length > 0 && --pulse2Length == 0) {
            pulse2Enabled = false;
        }
        if (waveEnabled && (mmu.readByte(0xFF1E) & 0x40) != 0 && waveLength > 0 && --waveLength == 0) {
            waveEnabled = false;
        }
        if (noiseEnabled && (mmu.readByte(0xFF23) & 0x40) != 0 && noiseLength > 0 && --noiseLength == 0) {
            noiseEnabled = false;
        }
    }

    private void clockEnvelopes() {
        pulse1Volume = clockEnvelope(pulse1Volume, pulse1EnvelopeTimer, 0xFF12);
        pulse1EnvelopeTimer = nextEnvelopeTimer(pulse1EnvelopeTimer, 0xFF12);
        pulse2Volume = clockEnvelope(pulse2Volume, pulse2EnvelopeTimer, 0xFF17);
        pulse2EnvelopeTimer = nextEnvelopeTimer(pulse2EnvelopeTimer, 0xFF17);
        noiseVolume = clockEnvelope(noiseVolume, noiseEnvelopeTimer, 0xFF21);
        noiseEnvelopeTimer = nextEnvelopeTimer(noiseEnvelopeTimer, 0xFF21);
    }

    private int clockEnvelope(int volume, int timer, int register) {
        if (timer > 1) {
            return volume;
        }
        int envelope = mmu.readByte(register);
        int next = volume + (((envelope & 0x08) != 0) ? 1 : -1);
        return (envelope & 0x07) == 0 || next < 0 || next > 15 ? volume : next;
    }

    private int nextEnvelopeTimer(int timer, int register) {
        if (timer > 1) {
            return timer - 1;
        }
        int period = mmu.readByte(register) & 0x07;
        return period == 0 ? 8 : period;
    }

    private void clockSweep() {
        if (!pulse1SweepEnabled || --pulse1SweepTimer > 0) {
            return;
        }
        int sweep = mmu.readByte(0xFF10);
        int period = (sweep >> 4) & 0x07;
        pulse1SweepTimer = period == 0 ? 8 : period;
        if (period == 0 || (sweep & 0x07) == 0) {
            return;
        }

        int delta = pulse1SweepFrequency >> (sweep & 0x07);
        int next = (sweep & 0x08) != 0
            ? pulse1SweepFrequency - delta
            : pulse1SweepFrequency + delta;
        if (next > 2047 || next < 0) {
            pulse1Enabled = false;
            return;
        }

        pulse1SweepFrequency = next;
        mmu.writeByte(0xFF13, next & 0xFF);
        mmu.writeByte(0xFF14, (mmu.readByte(0xFF14) & 0xF8) | ((next >> 8) & 0x07));
        calculateSweepOverflow();
    }

    private void calculateSweepOverflow() {
        int sweep = mmu.readByte(0xFF10);
        int delta = pulse1SweepFrequency >> (sweep & 0x07);
        int next = (sweep & 0x08) != 0
            ? pulse1SweepFrequency - delta
            : pulse1SweepFrequency + delta;
        if (next > 2047 || next < 0) {
            pulse1Enabled = false;
        }
    }

    private void advanceOscillators() {
        if (pulse1Enabled) {
            clockPulseTimer(true);
        }
        if (pulse2Enabled) {
            clockPulseTimer(false);
        }
        if (waveEnabled) {
            waveTimer -= CPU_CLOCK;
            int period = Math.max(1, (2048 - waveFrequency()) * 2) * SAMPLE_RATE;
            while (waveTimer <= 0) {
                waveTimer += period;
                wavePosition = (wavePosition + 1) & 31;
            }
        }
        if (noiseEnabled) {
            noiseTimer -= CPU_CLOCK;
            long period = (long) noisePeriod() * SAMPLE_RATE;
            while (noiseTimer <= 0) {
                noiseTimer += period;
                int feedback = (noiseLfsr ^ (noiseLfsr >> 1)) & 1;
                noiseLfsr = (noiseLfsr >> 1) | (feedback << 14);
                if ((mmu.readByte(0xFF22) & 0x08) != 0) {
                    noiseLfsr = (noiseLfsr & ~(1 << 6)) | (feedback << 6);
                }
            }
        }
    }

    private void clockPulseTimer(boolean firstChannel) {
        int timer = firstChannel ? pulse1Timer : pulse2Timer;
        timer -= CPU_CLOCK;
        int period = Math.max(1, (2048 - pulseFrequency(firstChannel)) * 4) * SAMPLE_RATE;
        while (timer <= 0) {
            timer += period;
            if (firstChannel) pulse1DutyPosition = (pulse1DutyPosition + 1) & 7;
            else pulse2DutyPosition = (pulse2DutyPosition + 1) & 7;
        }
        if (firstChannel) pulse1Timer = timer;
        else pulse2Timer = timer;
    }

    private int mixSample() {
        int[] channelSamples = {
            pulseSample(true),
            pulseSample(false),
            waveSample(),
            noiseSample()
        };
        int routing = mmu.readByte(0xFF25);
        int left = 0;
        int right = 0;
        int leftCount = 0;
        int rightCount = 0;

        for (int channel = 0; channel < channelSamples.length; channel++) {
            if ((routing & (1 << channel)) != 0) {
                right += channelSamples[channel];
                rightCount++;
            }
            if ((routing & (1 << (channel + 4))) != 0) {
                left += channelSamples[channel];
                leftCount++;
            }
        }

        int volume = mmu.readByte(0xFF24);
        left = scaleMix(left, leftCount, ((volume >> 4) & 0x07) + 1);
        right = scaleMix(right, rightCount, (volume & 0x07) + 1);
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, (left + right) / 2));
    }

    private int scaleMix(int sample, int count, int volume) {
        return count == 0 ? 0 : (sample / count) * volume / 8;
    }

    private int pulseSample(boolean firstChannel) {
        boolean enabled = firstChannel ? pulse1Enabled : pulse2Enabled;
        int dutyPosition = firstChannel ? pulse1DutyPosition : pulse2DutyPosition;
        int volume = firstChannel ? pulse1Volume : pulse2Volume;
        int nrx1 = firstChannel ? 0xFF11 : 0xFF16;
        int nrx2 = firstChannel ? 0xFF12 : 0xFF17;
        if (!enabled || (mmu.readByte(nrx2) & 0xF8) == 0) {
            return 0;
        }
        int duty = (mmu.readByte(nrx1) >> 6) & 0x03;
        int bit = (DUTY_PATTERNS[duty] >> (7 - dutyPosition)) & 1;
        return (bit == 0 ? -volume : volume) * 500;
    }

    private int waveSample() {
        if (!waveEnabled || (mmu.readByte(0xFF1A) & 0x80) == 0) {
            return 0;
        }
        int volumeCode = (mmu.readByte(0xFF1C) >> 5) & 0x03;
        if (volumeCode == 0) {
            return 0;
        }
        int waveByte = mmu.readByte(0xFF30 + (wavePosition >> 1));
        int sample = (wavePosition & 1) == 0 ? waveByte >> 4 : waveByte & 0x0F;
        sample >>= volumeCode - 1;
        return (sample - 8) * 500;
    }

    private int noiseSample() {
        if (!noiseEnabled || (mmu.readByte(0xFF21) & 0xF8) == 0) {
            return 0;
        }
        return (noiseLfsr & 1) == 0 ? noiseVolume * 500 : -noiseVolume * 500;
    }

    private int pulseFrequency(boolean firstChannel) {
        int lowRegister = firstChannel ? 0xFF13 : 0xFF18;
        int highRegister = firstChannel ? 0xFF14 : 0xFF19;
        return mmu.readByte(lowRegister) | ((mmu.readByte(highRegister) & 0x07) << 8);
    }

    private int waveFrequency() {
        return mmu.readByte(0xFF1D) | ((mmu.readByte(0xFF1E) & 0x07) << 8);
    }

    private int noisePeriod() {
        int polynomial = mmu.readByte(0xFF22);
        return Math.max(1, NOISE_DIVISORS[polynomial & 0x07] << ((polynomial >> 4) & 0x0F));
    }

    private void appendSample(int sample) {
        if (audioLine == null || !audioLine.isOpen()) {
            return;
        }

        audioBuffer[bufferedBytes++] = (byte) sample;
        audioBuffer[bufferedBytes++] = (byte) (sample >> 8);
        if (bufferedBytes == audioBuffer.length) {
            if (audioLine.available() >= bufferedBytes) {
                audioLine.write(audioBuffer, 0, bufferedBytes);
            }
            bufferedBytes = 0;
        }
    }

    public void close() {
        if (audioLine != null) {
            audioLine.stop();
            audioLine.close();
        }
    }

    public void writeRegister(int address, int value) {
        if ((address == 0xFF14 || address == 0xFF19 || address == 0xFF1E || address == 0xFF23)
            && (value & 0x80) != 0) {
            triggerChannel(address);
        }
    }

    private void triggerChannel(int address) {
        switch (address) {
            case 0xFF14 -> {
                pulse1Enabled = (mmu.readByte(0xFF12) & 0xF8) != 0;
                if (pulse1Length == 0) pulse1Length = 64;
                pulse1Timer = Math.max(1, (2048 - pulseFrequency(true)) * 4) * SAMPLE_RATE;
                pulse1DutyPosition = 0;
                pulse1Volume = (mmu.readByte(0xFF12) >> 4) & 0x0F;
                pulse1EnvelopeTimer = envelopePeriod(0xFF12);
                pulse1SweepFrequency = pulseFrequency(true);
                int sweepPeriod = (mmu.readByte(0xFF10) >> 4) & 0x07;
                pulse1SweepTimer = sweepPeriod == 0 ? 8 : sweepPeriod;
                pulse1SweepEnabled = sweepPeriod != 0 || (mmu.readByte(0xFF10) & 0x07) != 0;
                if ((mmu.readByte(0xFF10) & 0x07) != 0) calculateSweepOverflow();
            }
            case 0xFF19 -> {
                pulse2Enabled = (mmu.readByte(0xFF17) & 0xF8) != 0;
                if (pulse2Length == 0) pulse2Length = 64;
                pulse2Timer = Math.max(1, (2048 - pulseFrequency(false)) * 4) * SAMPLE_RATE;
                pulse2DutyPosition = 0;
                pulse2Volume = (mmu.readByte(0xFF17) >> 4) & 0x0F;
                pulse2EnvelopeTimer = envelopePeriod(0xFF17);
            }
            case 0xFF1E -> {
                waveEnabled = (mmu.readByte(0xFF1A) & 0x80) != 0;
                if (waveLength == 0) waveLength = 256;
                waveTimer = Math.max(1, (2048 - waveFrequency()) * 2) * SAMPLE_RATE;
                wavePosition = 0;
            }
            case 0xFF23 -> {
                noiseEnabled = (mmu.readByte(0xFF21) & 0xF8) != 0;
                if (noiseLength == 0) noiseLength = 64;
                noiseTimer = (long) noisePeriod() * SAMPLE_RATE;
                noiseVolume = (mmu.readByte(0xFF21) >> 4) & 0x0F;
                noiseEnvelopeTimer = envelopePeriod(0xFF21);
                noiseLfsr = 0x7FFF;
            }
            default -> {
            }
        }
        mmu.writeByte(address, mmu.readByte(address) & 0x7F);
    }

    private int envelopePeriod(int register) {
        int period = mmu.readByte(register) & 0x07;
        return period == 0 ? 8 : period;
    }

    public void writeLengthRegister(int address, int value) {
        switch (address) {
            case 0xFF11 -> pulse1Length = 64 - (value & 0x3F);
            case 0xFF16 -> pulse2Length = 64 - (value & 0x3F);
            case 0xFF1B -> waveLength = 256 - value;
            case 0xFF20 -> noiseLength = 64 - (value & 0x3F);
            default -> {
            }
        }
    }
}

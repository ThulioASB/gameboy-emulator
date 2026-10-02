import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java Main <caminho_da_rom.gb>");
            return;
        }

        String romPath = args[0];

        SwingUtilities.invokeLater(() -> {
            try {
                Registers reg = new Registers();
                MMU mmu = new MMU();
                
                Joypad joypad = new Joypad(mmu);
                mmu.setJoypad(joypad);

                CPU cpu = new CPU(reg, mmu);
                Timer timer = new Timer(mmu);
                PPU ppu = new PPU(mmu);
                APU apu = new APU(mmu);

                mmu.setTimer(timer);

                byte[] romData = Files.readAllBytes(Path.of(romPath));
                mmu.loadRom(romData);

                SaveManager saveManager = new SaveManager(mmu.getMbc(), romPath);
                saveManager.loadSave();

                DisplayWindow window = new DisplayWindow("Game Boy Emulator", joypad);

                window.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent e) {
                        saveManager.save();
                    }
                });

                window.setVisible(true);

                Thread emulatorThread = new Thread(() -> {
                    final double NS_PER_FRAME = 1_000_000_000.0 / 59.73;
                    long lastTime = System.nanoTime();
                    int saveTimer = 0;

                    while (true) {
                        long now = System.nanoTime();
                        if (now - lastTime >= NS_PER_FRAME) {
                            lastTime = now;

                            int cyclesThisFrame = 0;
                            while (cyclesThisFrame < 70224) {
                                int mCycles = cpu.step();
                                int tCycles = mCycles * 4;

                                timer.step(tCycles);
                                ppu.step(tCycles);
                                apu.step(tCycles);

                                cyclesThisFrame += tCycles;
                            }

                            window.renderFrame(ppu.getScreenBuffer());

                            saveTimer++;
                            if (saveTimer >= 3600) {
                                saveManager.save();
                                saveTimer = 0;
                            }
                        }

                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignored) {}
                    }
                });

                emulatorThread.start();

            } catch (IOException e) {
                System.err.println("Erro ao carregar a ROM: " + e.getMessage());
            }
        });
    }
}
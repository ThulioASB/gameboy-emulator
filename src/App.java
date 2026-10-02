import java.io.File;
import java.io.FileInputStream;

public class App {
    public static void main(String[] args) {
        MMU mmu = new MMU();
        CPU cpu = new CPU(mmu);
        Display display = new Display();
        display.attachKeyListener(mmu);
        PPU ppu = new PPU(mmu, display);

        File romFile = new File("roms/tetris.gb");
        if (!romFile.exists()) {
            System.out.println("Coloque um ficheiro .gb válido na pasta roms/");
            return;
        }

        try (FileInputStream fis = new FileInputStream(romFile)) {
            byte[] romData = fis.readAllBytes();
            mmu.loadRom(romData);
            System.out.println("ROM carregada com sucesso! A iniciar janela...");

            final int CYCLES_PER_FRAME = 70224;

            while (true) {
                long frameStart = System.currentTimeMillis();
                int cyclesThisFrame = 0;

                while (cyclesThisFrame < CYCLES_PER_FRAME) {
                    int cycles = cpu.step();
                    mmu.step(cycles);
                    ppu.step(cycles);
                    cyclesThisFrame += cycles;
                }

                long frameTime = System.currentTimeMillis() - frameStart;
                long sleepTime = 16 - frameTime; // ~60 FPS

                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ignored) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
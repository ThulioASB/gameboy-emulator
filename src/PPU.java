public class PPU {
    private final MMU mmu;
    private final Display display;
    private int cyclesCount = 0;
    private final int[] frameBuffer = new int[Display.WIDTH * Display.HEIGHT];

    private static final int[] PALETTE = {
        0xE0F8D0,
        0x88C070,
        0x346856,
        0x081820
    };

    public PPU(MMU mmu, Display display) {
        this.mmu = mmu;
        this.display = display;
    }

    public void step(int cycles) {
        cyclesCount += cycles;

        int currentLine = mmu.readByte(0xFF44);
        int stat = mmu.readByte(0xFF41);

        if (currentLine >= 144) {
            stat = (stat & ~0x03) | 0x01;
        } else if (cyclesCount < 80) {
            stat = (stat & ~0x03) | 0x02;
        } else if (cyclesCount < 248) {
            stat = (stat & ~0x03) | 0x03;
        } else {
            stat = (stat & ~0x03) | 0x00;
        }
        mmu.writeByte(0xFF41, stat);

        if (cyclesCount >= 456) {
            cyclesCount -= 456;

            if (currentLine < 144) {
                renderScanline(currentLine);
            }

            currentLine++;

            if (currentLine == 144) {
                display.render(frameBuffer);
                int ifReg = mmu.readByte(0xFF0F);
                mmu.writeByte(0xFF0F, ifReg | 0x01);
            } else if (currentLine > 153) {
                currentLine = 0;
            }

            mmu.setLYDirect(currentLine);
        }
    }

    private void renderScanline(int line) {
        int lcdc = mmu.readByte(0xFF40);

        if ((lcdc & 0x80) == 0) {
            for (int x = 0; x < Display.WIDTH; x++) {
                frameBuffer[line * Display.WIDTH + x] = PALETTE[0];
            }
            return;
        }

        boolean windowEnable = (lcdc & 0x20) != 0;
        int wy = mmu.readByte(0xFF4A);
        int wx = mmu.readByte(0xFF4B) - 7;
        boolean renderWindow = windowEnable && line >= wy;

        int bgMapAddress = ((lcdc & 0x08) != 0) ? 0x9C00 : 0x9800;
        int winMapAddress = ((lcdc & 0x40) != 0) ? 0x9C00 : 0x9800;

        boolean unsignedMode = (lcdc & 0x10) != 0;
        int bgp = mmu.readByte(0xFF47);
        int scy = mmu.readByte(0xFF42);
        int scx = mmu.readByte(0xFF43);

        for (int x = 0; x < Display.WIDTH; x++) {
            boolean useWindow = renderWindow && x >= wx;
            int pixelX, pixelY, tileMapAddress;

            if (useWindow) {
                pixelX = x - wx;
                pixelY = line - wy;
                tileMapAddress = winMapAddress;
            } else {
                pixelX = (x + scx) & 0xFF;
                pixelY = (line + scy) & 0xFF;
                tileMapAddress = bgMapAddress;
            }

            int tileX = (pixelX / 8) & 0x1F;
            int tileY = (pixelY / 8) & 0x1F;
            int tileIndex = mmu.readByte(tileMapAddress + (tileY * 32) + tileX);

            int address;
            if (unsignedMode) {
                address = 0x8000 + (tileIndex * 16) + ((pixelY % 8) * 2);
            } else {
                byte signedIndex = (byte) tileIndex;
                address = 0x9000 + (signedIndex * 16) + ((pixelY % 8) * 2);
            }

            int byte1 = mmu.readByte(address);
            int byte2 = mmu.readByte(address + 1);

            int bit = 7 - (pixelX % 8);
            int pixelColorNum = (((byte2 >> bit) & 0x01) << 1) | ((byte1 >> bit) & 0x01);
            int colorIndex = (bgp >> (pixelColorNum * 2)) & 0x03;

            frameBuffer[line * Display.WIDTH + x] = PALETTE[colorIndex];
        }
    }
}
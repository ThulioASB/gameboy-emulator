public class PPU {
    private final MMU mmu;
    private final int[] screenBuffer = new int[160 * 144];
    private int cycleCounter = 0;

    private static final int[] PALETTE_COLORS = {
        0xFFE0F8D0,
        0xFF88C070,
        0xFF346856,
        0xFF081820
    };

    public PPU(MMU mmu) {
        this.mmu = mmu;
    }

    public void step(int tCycles) {
        int lcdc = mmu.readByte(0xFF40);
        boolean lcdEnabled = (lcdc & 0x80) != 0;

        if (!lcdEnabled) {
            cycleCounter = 0;
            mmu.setLyDirectly(0);
            setLcdStatusMode(0);
            return;
        }

        cycleCounter += tCycles;
        int currentLy = mmu.readByteDirectly(0xFF44);

        if (cycleCounter >= 456) {
            cycleCounter -= 456;
            currentLy = (currentLy + 1) % 154;
            mmu.setLyDirectly(currentLy);

            checkCoincidenceFlag(currentLy);

            if (currentLy == 144) {
                int ifReg = mmu.readByte(0xFF0F);
                mmu.writeByte(0xFF0F, ifReg | 0x01);
                checkStatInterrupt(1);
            }
        }

        if (currentLy >= 144) {
            setLcdStatusMode(1);
        } else {
            int currentMode = getLcdStatusMode();
            if (cycleCounter < 80) {
                if (currentMode != 2) {
                    setLcdStatusMode(2);
                    checkStatInterrupt(2);
                }
            } else if (cycleCounter < 80 + 172) {
                setLcdStatusMode(3);
            } else {
                if (currentMode != 0) {
                    renderScanline(currentLy);
                    setLcdStatusMode(0);
                    checkStatInterrupt(0);
                }
            }
        }
    }

    private void checkCoincidenceFlag(int currentLy) {
        int lyc = mmu.readByteDirectly(0xFF45);
        int stat = mmu.readByteDirectly(0xFF41);

        if (currentLy == lyc) {
            stat |= 0x04;
            if ((stat & 0x40) != 0) {
                triggerStatInterrupt();
            }
        } else {
            stat &= ~0x04;
        }

        mmu.setStatDirectly(stat);
    }

    private void checkStatInterrupt(int mode) {
        int stat = mmu.readByte(0xFF41);
        boolean trigger = false;

        if (mode == 0 && (stat & 0x08) != 0) trigger = true;
        if (mode == 1 && (stat & 0x10) != 0) trigger = true;
        if (mode == 2 && (stat & 0x20) != 0) trigger = true;

        if (trigger) {
            triggerStatInterrupt();
        }
    }

    private void triggerStatInterrupt() {
        int ifReg = mmu.readByte(0xFF0F);
        mmu.writeByte(0xFF0F, ifReg | 0x02);
    }

    private void renderScanline(int scanline) {
        int lcdc = mmu.readByte(0xFF40);
        
        if ((lcdc & 0x01) != 0) {
            renderBackgroundScanline(scanline, lcdc);
        }
        
        if ((lcdc & 0x20) != 0) {
            renderWindowScanline(scanline, lcdc);
        }
        
        if ((lcdc & 0x02) != 0) {
            renderSpritesScanline(scanline, lcdc);
        }
    }

    private void renderBackgroundScanline(int scanline, int lcdc) {
        int scx = mmu.readByteDirectly(0xFF42);
        int scy = mmu.readByteDirectly(0xFF43);
        int bgp = mmu.readByteDirectly(0xFF47);

        int tileMapAddress = ((lcdc & 0x08) != 0) ? 0x9C00 : 0x9800;
        int tileDataAddress = ((lcdc & 0x10) != 0) ? 0x8000 : 0x8800;
        boolean isUnsigned = (lcdc & 0x10) != 0;

        int yPos = (scanline + scy) & 0xFF;
        int tileRow = yPos / 8;

        for (int x = 0; x < 160; x++) {
            int xPos = (x + scx) & 0xFF;
            int tileCol = xPos / 8;

            int tileIndexAddress = tileMapAddress + (tileRow * 32) + tileCol;
            int tileIndex = mmu.readByteDirectly(tileIndexAddress);

            int tileAddress;
            if (isUnsigned) {
                tileAddress = tileDataAddress + (tileIndex * 16);
            } else {
                byte signedIndex = (byte) tileIndex;
                tileAddress = 0x9000 + (signedIndex * 16);
            }

            int lineInTile = (yPos % 8) * 2;
            int byte1 = mmu.readByteDirectly(tileAddress + lineInTile);
            int byte2 = mmu.readByteDirectly(tileAddress + lineInTile + 1);

            int bitIndex = 7 - (xPos % 8);
            int pixelColorId = (((byte2 >> bitIndex) & 1) << 1) | ((byte1 >> bitIndex) & 1);

            int colorNum = (bgp >> (pixelColorId * 2)) & 0x03;
            screenBuffer[scanline * 160 + x] = PALETTE_COLORS[colorNum];
        }
    }

    private void renderWindowScanline(int scanline, int lcdc) {
        int wx = mmu.readByteDirectly(0xFF4B) - 7;
        int wy = mmu.readByteDirectly(0xFF4A);

        if (scanline < wy || wx >= 160) {
            return;
        }

        int bgp = mmu.readByteDirectly(0xFF47);
        int tileMapAddress = ((lcdc & 0x40) != 0) ? 0x9C00 : 0x9800;
        int tileDataAddress = ((lcdc & 0x10) != 0) ? 0x8000 : 0x8800;
        boolean isUnsigned = (lcdc & 0x10) != 0;

        int yPos = scanline - wy;
        int tileRow = yPos / 8;

        for (int x = Math.max(0, wx); x < 160; x++) {
            int xPos = x - wx;
            int tileCol = xPos / 8;

            int tileIndexAddress = tileMapAddress + (tileRow * 32) + tileCol;
            int tileIndex = mmu.readByteDirectly(tileIndexAddress);

            int tileAddress;
            if (isUnsigned) {
                tileAddress = tileDataAddress + (tileIndex * 16);
            } else {
                byte signedIndex = (byte) tileIndex;
                tileAddress = 0x9000 + (signedIndex * 16);
            }

            int lineInTile = (yPos % 8) * 2;
            int byte1 = mmu.readByteDirectly(tileAddress + lineInTile);
            int byte2 = mmu.readByteDirectly(tileAddress + lineInTile + 1);

            int bitIndex = 7 - (xPos % 8);
            int pixelColorId = (((byte2 >> bitIndex) & 1) << 1) | ((byte1 >> bitIndex) & 1);

            int colorNum = (bgp >> (pixelColorId * 2)) & 0x03;
            screenBuffer[scanline * 160 + x] = PALETTE_COLORS[colorNum];
        }
    }

    private void renderSpritesScanline(int scanline, int lcdc) {
        boolean use8x16 = (lcdc & 0x04) != 0;
        int spriteHeight = use8x16 ? 16 : 8;

        int spritesDrawn = 0;

        for (int i = 0; i < 40; i++) {
            int oamAddress = 0xFE00 + (i * 4);
            int yPos = mmu.readByteDirectly(oamAddress) - 16;
            int xPos = mmu.readByteDirectly(oamAddress + 1) - 8;
            int tileIndex = mmu.readByteDirectly(oamAddress + 2);
            int attributes = mmu.readByteDirectly(oamAddress + 3);

            if (scanline < yPos || scanline >= (yPos + spriteHeight)) {
                continue;
            }

            spritesDrawn++;
            if (spritesDrawn > 10) {
                break;
            }

            if (xPos < -7 || xPos >= 160) {
                continue;
            }

            boolean priority = (attributes & 0x80) != 0;
            boolean yFlip = (attributes & 0x40) != 0;
            boolean xFlip = (attributes & 0x20) != 0;
            int paletteAddress = (attributes & 0x10) != 0 ? 0xFF49 : 0xFF48;
            int obp = mmu.readByteDirectly(paletteAddress);

            int lineInSprite = scanline - yPos;
            if (yFlip) {
                lineInSprite = spriteHeight - 1 - lineInSprite;
            }

            if (use8x16) {
                tileIndex &= 0xFE;
            }

            int tileAddress = 0x8000 + (tileIndex * 16) + (lineInSprite * 2);
            int byte1 = mmu.readByteDirectly(tileAddress);
            int byte2 = mmu.readByteDirectly(tileAddress + 1);

            for (int col = 0; col < 8; col++) {
                int pixelX = xPos + col;
                if (pixelX < 0 || pixelX >= 160) {
                    continue;
                }

                int bitIndex = xFlip ? col : (7 - col);
                int pixelColorId = (((byte2 >> bitIndex) & 1) << 1) | ((byte1 >> bitIndex) & 1);

                if (pixelColorId == 0) {
                    continue;
                }

                if (priority && screenBuffer[scanline * 160 + pixelX] != PALETTE_COLORS[0]) {
                    continue;
                }

                int colorNum = (obp >> (pixelColorId * 2)) & 0x03;
                screenBuffer[scanline * 160 + pixelX] = PALETTE_COLORS[colorNum];
            }
        }
    }

    private void setLcdStatusMode(int mode) {
        int stat = mmu.readByteDirectly(0xFF41);
        mmu.setStatDirectly((stat & ~0x03) | (mode & 0x03));
    }

    private int getLcdStatusMode() {
        return mmu.readByteDirectly(0xFF41) & 0x03;
    }

    public int[] getScreenBuffer() {
        return screenBuffer;
    }
}
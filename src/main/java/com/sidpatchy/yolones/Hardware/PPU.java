package com.sidpatchy.yolones.Hardware;

import com.sidpatchy.yolones.Hardware.Mappers.Mapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PPU {
    private static final Logger logger = LogManager.getLogger(PPU.class);
    private PPUMemory memory;
    private int[] framebuffer = new int[256 * 240];  // RGB output

    // Registers
    private int ppuCtrl = 0;      // 0x2000
    private int ppuMask = 0;      // 0x2001
    private int ppuStatus = 0;    // 0x2002
    private int oamAddr = 0;      // 0x2003
    private int scrollX = 0;      // 0x2005 (1st write)
    private int scrollY = 0;      // 0x2005 (2nd write)
    private int ppuAddr = 0;      // 0x2006
    private int ppuData = 0;      // 0x2007

    // OAM (Object Attribute Memory) for sprites
    private int[] oam = new int[256];
    private int[] bgPixels = new int[256]; // 0: transparent, 1: opaque

    // Internal state
    private boolean addrLatch = false;  // Toggle for 0x2005/0x2006
    private int readBuffer = 0;         // Buffered read

    // Timing
    private int scanline = 0;
    private int cycle = 0;
    private Mapper mapper;

    // NES color palette (all 64 colors)
    private static final int[] NES_PALETTE = {
            0x666666, 0x002A88, 0x1412A7, 0x3B00A4, 0x5C007E, 0x6E0040, 0x6C0600, 0x561D00,
            0x333500, 0x0B4800, 0x005200, 0x004F08, 0x00404D, 0x000000, 0x000000, 0x000000,
            0xADADAD, 0x155FD9, 0x4240FF, 0x7527FE, 0xA01ACC, 0xB71E7B, 0xB53120, 0x994E00,
            0x6B6D00, 0x388700, 0x0C9300, 0x008F32, 0x007C8D, 0x000000, 0x000000, 0x000000,
            0xFFFEFF, 0x64B0FF, 0x9290FF, 0xC676FF, 0xF36AFF, 0xFE6ECC, 0xFE8170, 0xEA9E22,
            0xBCBE00, 0x88D800, 0x5CE430, 0x45E082, 0x48CDDE, 0x4F4F4F, 0x000000, 0x000000,
            0xFFFEFF, 0xC0DFFF, 0xD3D2FF, 0xE8C8FF, 0xFBC2FF, 0xFEC4EA, 0xFECCC5, 0xF7D8A5,
            0xE4E594, 0xCFEF96, 0xBDF4AB, 0xB3F3CC, 0xB5EBF2, 0xB8B8B8, 0x000000, 0x000000
    };

    public PPU(PPUMemory mem, Mapper mapper) {
        this.memory = mem;
        this.mapper = mapper;
    }

    // Called by CPU memory when accessing 0x2000-0x2007
    public int readRegister(int addr) {
        switch(addr & 0x2007) {
            case 0x2002:  // PPUSTATUS
                int status = ppuStatus;
                ppuStatus &= 0x7F;  // Clear VBlank flag
                addrLatch = false;   // Reset address latch
                return status;

            case 0x2007:  // PPUDATA
                int data = readBuffer;
                readBuffer = memory.read(ppuAddr);

                // Palette reads are immediate (not buffered)
                if (ppuAddr >= 0x3F00) {
                    data = readBuffer;
                }

                ppuAddr = (ppuAddr + ((ppuCtrl & 0x04) != 0 ? 32 : 1)) & 0x3FFF;
                return data;

            default:
                return 0;  // Other registers are write-only
        }
    }

    public void writeRegister(int addr, int value) {
        value &= 0xFF;

        switch(addr & 0x2007) {
            case 0x2000:  // PPUCTRL
                logger.debug(String.format("PPUCTRL write: 0x%02X (NMI enable: %b)",
                        value, (value & 0x80) != 0));
                ppuCtrl = value;
                break;

            case 0x2001:  // PPUMASK
                logger.debug(String.format("PPUMASK write: 0x%02X (bg=%b, sprites=%b)",
                        value, (value & 0x08) != 0, (value & 0x10) != 0));
                ppuMask = value;
                break;

            case 0x2003:  // OAMADDR
                oamAddr = value & 0xFF;
                break;

            case 0x2004:  // OAMDATA (write)
                // Write to OAM at current address, auto-increment
                oam[oamAddr & 0xFF] = value & 0xFF;
                oamAddr = (oamAddr + 1) & 0xFF;
                break;

            case 0x2005:  // PPUSCROLL
                if (!addrLatch) {
                    scrollX = value;
                } else {
                    scrollY = value;
                }
                addrLatch = !addrLatch;
                break;

            case 0x2006:  // PPUADDR
                if (!addrLatch) {
                    ppuAddr = (value << 8) | (ppuAddr & 0xFF);
                } else {
                    ppuAddr = (ppuAddr & 0xFF00) | value;
                }
                addrLatch = !addrLatch;
                break;

            case 0x2007:  // PPUDATA
                if (ppuAddr >= 0x3F00 && ppuAddr < 0x3F20) {
                    logger.trace(String.format("Palette[0x%02X] = 0x%02X", ppuAddr & 0x1F, value));
                }
                memory.write(ppuAddr, value);
                ppuAddr = (ppuAddr + ((ppuCtrl & 0x04) != 0 ? 32 : 1)) & 0x3FFF;
                break;
        }
    }

    // Called every PPU cycle
    public boolean tick() {
        cycle++;

        if (cycle == 260) {
            if (scanline < 240 || scanline == 261) {
                if ((ppuMask & 0x18) != 0) {
                    mapper.clockIRQ();
                }
            }
        }

        if (cycle >= 341) {
            cycle = 0;
            scanline++;

            if (scanline < 240) {
                renderScanline(scanline);
            }

            if (scanline == 241) {
                ppuStatus |= 0x80;  // Set VBlank flag
                logger.debug(String.format("Scanline 241 (VBlank): PPUCTRL=0x%02X, NMI will fire=%b",
                        ppuCtrl, (ppuCtrl & 0x80) != 0));

                if ((ppuCtrl & 0x80) != 0) {
                    return true;  // Trigger NMI
                }
            }

            if (scanline >= 262) {
                // End of frame
                scanline = 0;
                ppuStatus &= 0x3F;  // Clear VBlank and Sprite 0 hit flags
                // After rendering, reset OAMADDR to 0 as many games expect
                oamAddr = 0;
            }
        }

        return false;  // No NMI
    }

    private void renderScanline(int y) {
        // Clear background with universal background color first if rendering is enabled
        int universalColor = NES_PALETTE[memory.read(0x3F00) & 0x3F];
        for (int x = 0; x < 256; x++) {
            framebuffer[y * 256 + x] = universalColor;
            bgPixels[x] = 0;
        }

        if ((ppuMask & 0x18) == 0) return; // Rendering disabled

        int bgPatternBase = ((ppuCtrl & 0x10) != 0) ? 0x1000 : 0x0000;
        int spritePatternBase = ((ppuCtrl & 0x08) != 0) ? 0x1000 : 0x0000;
        boolean sprites8x16 = (ppuCtrl & 0x20) != 0;

        // Simplified background rendering for the scanline
        if ((ppuMask & 0x08) != 0) {
            for (int x = 0; x < 256; x++) {
                int realX = x + scrollX;
                int realY = y + scrollY;
                int ntIndex = (ppuCtrl & 0x03);
                if (realX >= 256) {
                    ntIndex ^= 0x01;
                    realX %= 256;
                }
                if (realY >= 240) {
                    ntIndex ^= 0x02;
                    realY %= 240;
                }
                int nametableBase = 0x2000 + ntIndex * 0x400;

                int tileX = realX / 8;
                int tileY = realY / 8;
                int tileIndex = memory.read(nametableBase + tileY * 32 + tileX);

                int attrAddr = nametableBase + 0x3C0 + (tileY / 4) * 8 + (tileX / 4);
                int attrByte = memory.read(attrAddr);
                int quadrant = ((tileY & 0x02) << 1) | (tileX & 0x02);
                int shift = quadrant * 2;
                int paletteIndex = (attrByte >> shift) & 0x03;

                int tileAddr = bgPatternBase + tileIndex * 16;
                int row = realY % 8;
                int lowByte = memory.read(tileAddr + row);
                int highByte = memory.read(tileAddr + row + 8);
                int col = realX % 8;
                int bit0 = (lowByte >> (7 - col)) & 1;
                int bit1 = (highByte >> (7 - col)) & 1;
                int colorIndex = (bit1 << 1) | bit0;

                if (colorIndex != 0) {
                    int paletteAddr = 0x3F00 + paletteIndex * 4 + colorIndex;
                    framebuffer[y * 256 + x] = NES_PALETTE[memory.read(paletteAddr) & 0x3F];
                    bgPixels[x] = 1;
                }
            }
        }

        // Render sprites for this scanline
        if ((ppuMask & 0x10) != 0) {
            for (int i = 0; i < 64; i++) {
                int spriteY = (oam[i * 4] & 0xFF);
                if (y < spriteY || y >= spriteY + (sprites8x16 ? 16 : 8)) continue;

                int tile = oam[i * 4 + 1] & 0xFF;
                int attr = oam[i * 4 + 2] & 0xFF;
                int spriteX = oam[i * 4 + 3] & 0xFF;
                int palette = attr & 0x03;
                boolean flipH = (attr & 0x40) != 0;
                boolean flipV = (attr & 0x80) != 0;
                boolean priority = (attr & 0x20) != 0; // 0: in front, 1: behind

                int row = y - spriteY;
                if (flipV) row = (sprites8x16 ? 15 : 7) - row;

                int tileAddr;
                if (!sprites8x16) {
                    tileAddr = spritePatternBase + tile * 16 + row;
                } else {
                    int baseTable = (tile & 1) != 0 ? 0x1000 : 0x0000;
                    int actualTile = (tile & 0xFE) + (row / 8);
                    tileAddr = baseTable + actualTile * 16 + (row % 8);
                }

                int lowByte = memory.read(tileAddr);
                int highByte = memory.read(tileAddr + 8);

                for (int col = 0; col < 8; col++) {
                    int x = spriteX + col;
                    if (x >= 256) continue;

                    int pixelCol = flipH ? col : 7 - col;
                    int bit0 = (lowByte >> pixelCol) & 1;
                    int bit1 = (highByte >> pixelCol) & 1;
                    int colorIndex = (bit1 << 1) | bit0;

                    if (colorIndex != 0) {
                        // Sprite 0 hit detection
                        if (i == 0 && bgPixels[x] != 0 && x < 255) {
                            ppuStatus |= 0x40;
                        }

                        if (!priority || bgPixels[x] == 0) {
                            int paletteAddr = 0x3F10 + palette * 4 + colorIndex;
                            framebuffer[y * 256 + x] = NES_PALETTE[memory.read(paletteAddr) & 0x3F];
                        }
                    }
                }
            }
        }
    }

    private void drawBgTile(int screenX, int screenY, int tileIndex, int paletteIndex, int patternBase) {
        int tileAddr = patternBase + tileIndex * 16;

        for (int row = 0; row < 8; row++) {
            int lowByte = memory.read(tileAddr + row);
            int highByte = memory.read(tileAddr + row + 8);

            for (int col = 0; col < 8; col++) {
                int bit0 = (lowByte >> (7 - col)) & 1;
                int bit1 = (highByte >> (7 - col)) & 1;
                int colorIndex = (bit1 << 1) | bit0;

                // Get color from palette (color 0 always uses universal background color at 0x3F00)
                int paletteAddr = (colorIndex == 0)
                        ? 0x3F00
                        : 0x3F00 + paletteIndex * 4 + colorIndex;
                int nesColorIndex = memory.read(paletteAddr) & 0x3F;
                int color = NES_PALETTE[nesColorIndex];

                // Draw pixel
                int pixelX = screenX + col;
                int pixelY = screenY + row;
                if (pixelX < 256 && pixelY < 240) {
                    framebuffer[pixelY * 256 + pixelX] = color;
                }
            }
        }
    }

    private void drawSprite8x8(int screenX, int screenY, int tileIndex, int paletteIndex, int patternBase,
                                boolean flipH, boolean flipV, boolean behindBg) {
        int tileAddr = patternBase + tileIndex * 16;

        for (int row = 0; row < 8; row++) {
            int srcRow = flipV ? (7 - row) : row;
            int lowByte = memory.read(tileAddr + srcRow);
            int highByte = memory.read(tileAddr + srcRow + 8);

            for (int col = 0; col < 8; col++) {
                int srcCol = flipH ? col : (7 - col);
                int bit0 = (lowByte >> srcCol) & 1;
                int bit1 = (highByte >> srcCol) & 1;
                int colorIndex = (bit1 << 1) | bit0;
                if (colorIndex == 0) continue; // transparent

                // Sprite palette base at 0x3F10
                int paletteAddr = 0x3F10 + paletteIndex * 4 + colorIndex;
                int nesColorIndex = memory.read(paletteAddr) & 0x3F;
                int color = NES_PALETTE[nesColorIndex];

                int pixelX = screenX + col;
                int pixelY = screenY + row;
                if (pixelX < 256 && pixelY < 240) {
                    int idx = pixelY * 256 + pixelX;
                    if (behindBg) {
                        // Only draw if bg pixel is universal background (approximation)
                        if (framebuffer[idx] == NES_PALETTE[memory.read(0x3F00) & 0x3F]) {
                            framebuffer[idx] = color;
                        }
                    } else {
                        framebuffer[idx] = color;
                    }
                }
            }
        }
    }

    public int[] getFramebuffer() {
        return framebuffer;
    }

    public int getScanline() {
        return scanline;
    }

    public int getCycle() {
        return cycle;
    }
}

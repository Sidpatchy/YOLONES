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
    private int ppuData = 0;      // 0x2007

    // Internal Loopy Registers
    private int v = 0; // Current VRAM address (15 bits)
    private int t = 0; // Temporary VRAM address (15 bits)
    private int x = 0; // Fine X scroll (3 bits)
    private boolean w = false; // Write latch

    // OAM (Object Attribute Memory) for sprites
    private int[] oam = new int[256];
    private int[] bgPixels = new int[256]; // 0: transparent, 1: opaque

    // Internal state
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
        switch(addr & 0x0007) {
            case 0x0002:  // PPUSTATUS
                int status = ppuStatus;
                ppuStatus &= 0x7F;  // Clear VBlank flag
                w = false;          // Reset address latch
                return status;

            case 0x0004:  // OAMDATA
                return oam[oamAddr & 0xFF];

            case 0x0007:  // PPUDATA
                int data = readBuffer;
                readBuffer = memory.read(v & 0x3FFF);

                // Palette reads are immediate (not buffered)
                if ((v & 0x3FFF) >= 0x3F00) {
                    data = readBuffer;
                }

                v = (v + ((ppuCtrl & 0x04) != 0 ? 32 : 1)) & 0x7FFF;
                return data;

            default:
                return 0;  // Other registers are write-only
        }
    }

    public void writeRegister(int addr, int value) {
        value &= 0xFF;

        switch(addr & 0x0007) {
            case 0x0000:  // PPUCTRL
                ppuCtrl = value;
                // t: ...GH.. ........ = d: ......GH
                t = (t & 0xF3FF) | ((value & 0x03) << 10);
                break;

            case 0x0001:  // PPUMASK
                ppuMask = value;
                break;

            case 0x0003:  // OAMADDR
                oamAddr = value & 0xFF;
                break;

            case 0x0004:  // OAMDATA (write)
                // Write to OAM at current address, auto-increment
                oam[oamAddr & 0xFF] = value & 0xFF;
                oamAddr = (oamAddr + 1) & 0xFF;
                break;

            case 0x0005:  // PPUSCROLL
                if (!w) {
                    // t: ....... ...ABCDE = d: ABCDE...
                    // x:              FGH = d: .....FGH
                    // w:                  = 1
                    t = (t & 0xFFE0) | (value >> 3);
                    x = value & 0x07;
                    w = true;
                } else {
                    // t: FGH..AB CDE..... = d: ABCDE FGH
                    // w:                  = 0
                    t = (t & 0x8C1F) | ((value & 0x07) << 12) | ((value & 0xF8) << 2);
                    w = false;
                }
                break;

            case 0x0006:  // PPUADDR
                if (!w) {
                    // t: .ABCDEF GH...... = d: ..ABCDEF
                    // t: H...... ........ = 0
                    // w:                  = 1
                    t = (t & 0x00FF) | ((value & 0x3F) << 8);
                    w = true;
                } else {
                    // t: ....... ABCDEFGH = d: ABCDEFGH
                    // v:                  = t
                    // w:                  = 0
                    t = (t & 0xFF00) | value;
                    v = t;
                    w = false;
                }
                break;

            case 0x0007:  // PPUDATA
                memory.write(v & 0x3FFF, value);
                v = (v + ((ppuCtrl & 0x04) != 0 ? 32 : 1)) & 0x7FFF;
                break;
        }
    }

    // Called every PPU cycle
    public boolean tick() {
        if ((ppuMask & 0x18) != 0) {
            if (cycle == 257 && (scanline < 240 || scanline == 261)) {
                // Copy all bits related to horizontal position from t to v
                v = (v & 0xFBE0) | (t & 0x041F);
            }

            if (scanline == 261 && cycle >= 280 && cycle <= 304) {
                // Copy all bits related to vertical position from t to v
                v = (v & 0x841F) | (t & 0x7BE0);
            }
        }

        cycle++;

        if (cycle == 260 && (scanline < 240 || scanline == 261) && (ppuMask & 0x18) != 0) {
            mapper.clockIRQ();
        }

        if (cycle >= 341) {
            cycle = 0;
            
            if (scanline < 240) {
                renderScanline(scanline);
                if ((ppuMask & 0x18) != 0) {
                    incrementY();
                }
            }

            scanline++;

            if (scanline == 241) {
                ppuStatus |= 0x80;  // Set VBlank flag
                if ((ppuCtrl & 0x80) != 0) {
                    return true;  // Trigger NMI
                }
            }

            if (scanline == 261) {
                ppuStatus &= 0x1F; // Clear VBlank, Sprite 0 hit, and Sprite Overflow
            }

            if (scanline >= 262) {
                scanline = 0;
                oamAddr = 0;
            }
        }

        return false;  // No NMI
    }

    private void incrementY() {
        if ((v & 0x7000) != 0x7000) {
            v += 0x1000;
        } else {
            v &= 0x0FFF;
            int y = (v & 0x03E0) >> 5;
            if (y == 29) {
                y = 0;
                v ^= 0x0800;
            } else if (y == 31) {
                y = 0;
            } else {
                y++;
            }
            v = (v & 0xFC1F) | (y << 5);
        }
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
            // Start of scanline: copy horizontal bits from v to a local register for rendering
            int tempV = v;

            for (int dot = 0; dot < 256; dot++) {
                int fineX = (x + dot) % 8;
                if (dot > 0 && fineX == 0) {
                    // Increment coarse X in tempV
                    if ((tempV & 0x001F) == 31) {
                        tempV &= 0xFFE0;
                        tempV ^= 0x0400;
                    } else {
                        tempV++;
                    }
                }

                int ntAddr = 0x2000 | (tempV & 0x0FFF);
                int tileIndex = memory.read(ntAddr);

                int coarseX = tempV & 0x001F;
                int coarseY = (tempV & 0x03E0) >> 5;
                int ntIndex = (tempV & 0x0C00) >> 10;
                int nametableBase = 0x2000 + ntIndex * 0x400;

                int attrAddr = nametableBase + 0x3C0 + (coarseY / 4) * 8 + (coarseX / 4);
                int attrByte = memory.read(attrAddr);
                int quadrant = ((coarseY % 4) / 2) * 2 + ((coarseX % 4) / 2);
                int shift = quadrant * 2;
                int paletteIndex = (attrByte >> shift) & 0x03;

                int fineY = (tempV >> 12) & 0x07;
                int tileAddr = bgPatternBase + tileIndex * 16 + fineY;
                int lowByte = memory.read(tileAddr);
                int highByte = memory.read(tileAddr + 8);
                
                int bit0 = (lowByte >> (7 - fineX)) & 1;
                int bit1 = (highByte >> (7 - fineX)) & 1;
                int colorIndex = (bit1 << 1) | bit0;

                if (colorIndex != 0) {
                    if (dot >= 8 || (ppuMask & 0x02) != 0) {
                        int paletteAddr = 0x3F00 + paletteIndex * 4 + colorIndex;
                        framebuffer[y * 256 + dot] = NES_PALETTE[memory.read(paletteAddr) & 0x3F];
                        bgPixels[dot] = 1;
                    }
                }
            }
        }

        // Render sprites for this scanline
        if ((ppuMask & 0x10) != 0) {
            for (int i = 0; i < 64; i++) {
                // NES sprites are delayed by one scanline. The value in OAM is Y-1.
                // So if OAM has Y, the sprite appears on scanlines Y+1 to Y+8.
                int spriteY = (oam[i * 4] & 0xFF) + 1;
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
                    tileAddr = spritePatternBase + tile * 16 + (row % 8);
                } else {
                    int baseTable = (tile & 1) != 0 ? 0x1000 : 0x0000;
                    int actualTile = (tile & 0xFE) + ((row >= 8) ? 1 : 0);
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
                            // Check for masking of first 8 pixels
                            boolean bgVisible = (ppuMask & 0x08) != 0;
                            boolean sprVisible = (ppuMask & 0x10) != 0;
                            boolean bgLeftMasked = (x < 8) && ((ppuMask & 0x02) == 0);
                            boolean sprLeftMasked = (x < 8) && ((ppuMask & 0x04) == 0);

                            if (bgVisible && sprVisible && !bgLeftMasked && !sprLeftMasked) {
                                ppuStatus |= 0x40;
                            }
                        }

                        // Sprite rendering
                        if (x >= 8 || (ppuMask & 0x04) != 0) {
                            if (!priority || bgPixels[x] == 0) {
                                int paletteAddr = 0x3F10 + palette * 4 + colorIndex;
                                framebuffer[y * 256 + x] = NES_PALETTE[memory.read(paletteAddr) & 0x3F];
                            }
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

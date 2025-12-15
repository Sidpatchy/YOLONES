package com.sidpatchy.yolones.Hardware;

public class PPUMemory {
    // 2KB of nametable RAM (physically two 1KB tables; mapped via mirroring)
    private final int[] nametableRAM = new int[0x800];
    private final int[] paletteRAM = new int[32];    // Palette memory
    private final byte[] chrROM;                     // Pattern tables (from cartridge)
    private final boolean mirrorVertical;            // True: vertical, False: horizontal

    public PPUMemory(byte[] chrData, boolean mirrorVertical) {
        this.chrROM = (chrData != null ? chrData : new byte[0]);
        this.mirrorVertical = mirrorVertical;
    }

    private int mapNametableAddress(int addr) {
        // Normalize to 0x2000-0x2FFF and mirror 0x3000-0x3EFF down
        addr &= 0x3FFF;
        if (addr >= 0x3000 && addr < 0x3F00) {
            addr -= 0x1000;
        }
        int offset = (addr - 0x2000) & 0x0FFF; // 0..0xFFF within nametable region
        int tableIndex = offset / 0x400;       // 0..3 (which nametable)
        int inTable = offset & 0x03FF;         // 0..0x3FF within the table

        // Map 4 logical tables to 2 physical tables based on mirroring
        int physicalTable;
        if (mirrorVertical) {
            // Vertical: NT0=phys0, NT1=phys1, NT2=phys0, NT3=phys1
            physicalTable = (tableIndex & 1);
        } else {
            // Horizontal: NT0=phys0, NT1=phys0, NT2=phys1, NT3=phys1
            physicalTable = (tableIndex >> 1);
        }
        return (physicalTable * 0x400) | inTable; // index into 2KB RAM
    }

    public int read(int addr) {
        addr &= 0x3FFF;  // Mirror to 14-bit address space

        if (addr < 0x2000) {
            // Pattern tables (CHR-ROM or CHR-RAM)
            if (chrROM.length == 0) return 0; // No CHR; return 0 for safety
            int index = addr % chrROM.length; // robust for any length
            return chrROM[index] & 0xFF;
        } else if (addr < 0x3F00) {
            // Nametables with proper mirroring, including 0x3000-0x3EFF mirror handled in mapper
            int ntIndex = mapNametableAddress(addr);
            return nametableRAM[ntIndex] & 0xFF;
        } else {
            // Palette RAM
            int paddr = addr & 0x1F;
            if (paddr == 0x10) paddr = 0x00;  // Mirrored universal background entry
            if (paddr == 0x14) paddr = 0x04;
            if (paddr == 0x18) paddr = 0x08;
            if (paddr == 0x1C) paddr = 0x0C;
            return paletteRAM[paddr] & 0xFF;
        }
    }

    public void write(int addr, int value) {
        addr &= 0x3FFF;
        value &= 0xFF;

        if (addr < 0x2000) {
            // CHR-ROM is read-only. If this cart had CHR-RAM, we'd allow writes.
            if (chrROM.length == 0) {
                // If no CHR ROM provided, treat as CHR-RAM (8KB)
                // For minimalism, ignore writes since cart implementation didn't expose CHR-RAM.
            }
        } else if (addr < 0x3F00) {
            // Nametables with mirroring
            int ntIndex = mapNametableAddress(addr);
            nametableRAM[ntIndex] = value;
        } else {
            // Palette RAM
            int paddr = addr & 0x1F;
            if (paddr == 0x10) paddr = 0x00;
            if (paddr == 0x14) paddr = 0x04;
            if (paddr == 0x18) paddr = 0x08;
            if (paddr == 0x1C) paddr = 0x0C;
            paletteRAM[paddr] = value;
        }
    }
}


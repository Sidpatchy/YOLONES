package com.sidpatchy.yolones.Hardware;

import com.sidpatchy.yolones.Hardware.Mappers.Mapper;

public class PPUMemory {
    // 2KB of nametable RAM (physically two 1KB tables; mapped via mirroring)
    private final int[] nametableRAM = new int[0x800];
    private final int[] paletteRAM = new int[32];    // Palette memory
    private final Mapper mapper;
    private final boolean mirrorVertical;            // True: vertical, False: horizontal

    public PPUMemory(Mapper mapper, boolean mirrorVertical) {
        this.mapper = mapper;
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
        int mMode = mapper.getMirroringMode();
        boolean effectiveVertical = (mMode == -1) ? mirrorVertical : (mMode == 0);

        if (effectiveVertical) {
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
            // Pattern tables (CHR-ROM or CHR-RAM) via Mapper
            return mapper.chrRead(addr);
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
            // Pattern tables via Mapper
            mapper.chrWrite(addr, value);
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


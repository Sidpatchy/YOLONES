package com.sidpatchy.yolones.Hardware.Mappers;

public class Mapper0 implements Mapper {
    private final byte[] prgROM;
    private final byte[] chrROM;

    public Mapper0(byte[] prgROM, byte[] chrROM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
    }

    @Override
    public int read(int address) {
        if (address >= 0x8000) {
            int index = address - 0x8000;
            if (prgROM.length == 16384) {
                index %= 16384;
            }
            return prgROM[index] & 0xFF;
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        // NROM has no registers
    }

    @Override
    public int chrRead(int address) {
        if (chrROM.length > 0) {
            return chrROM[address & 0x1FFF] & 0xFF;
        }
        return 0;
    }

    @Override
    public void chrWrite(int address, int value) {
        // NROM usually has CHR-ROM, but some variants might have CHR-RAM
        // For now, assume CHR-ROM
    }
}

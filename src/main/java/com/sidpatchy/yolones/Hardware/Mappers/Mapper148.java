package com.sidpatchy.yolones.Hardware.Mappers;

public class Mapper148 implements Mapper {
    private final byte[] prgROM;
    private final byte[] chrROM;
    private int prgBank = 0;
    private int chrBank = 0;

    public Mapper148(byte[] prgROM, byte[] chrROM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
    }

    @Override
    public int read(int address) {
        if (address >= 0x8000) {
            int index = (prgBank * 0x8000 + (address - 0x8000)) % prgROM.length;
            return prgROM[index] & 0xFF;
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0x8000) {
            chrBank = value & 0x07;
            prgBank = (value >> 3) & 0x1F;
        }
    }

    @Override
    public int chrRead(int address) {
        if (chrROM.length > 0) {
            int index = (chrBank * 0x2000 + (address & 0x1FFF)) % chrROM.length;
            return chrROM[index] & 0xFF;
        }
        return 0;
    }

    @Override
    public void chrWrite(int address, int value) {
        // Mapper 148 usually has CHR-ROM
    }
}

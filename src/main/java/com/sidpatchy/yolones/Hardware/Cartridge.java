package com.sidpatchy.yolones.Hardware;

import com.sidpatchy.yolones.Hardware.Mappers.Mapper;
import com.sidpatchy.yolones.Hardware.Mappers.Mapper0;
import com.sidpatchy.yolones.Hardware.Mappers.Mapper1;
import com.sidpatchy.yolones.Hardware.Mappers.Mapper4;
import com.sidpatchy.yolones.Hardware.Mappers.Mapper148;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Cartridge {
    private byte[] prgROM;  // Program ROM (CPU reads this)
    private byte[] chrROM;  // Character ROM (PPU reads this)
    private int mapperNumber;
    private boolean mirrorVertical;  // PPU mirroring mode
    private Mapper mapper;

    public Cartridge(String romFilePath) throws IOException {
        byte[] romData = Files.readAllBytes(Paths.get(romFilePath));

        // Parse iNES header (first 16 bytes)
        // Bytes 0-3: "NES" + 0x1A (magic number)
        int prgRomSize = (romData[4] & 0xFF) * 16384;  // 16KB units
        int chrRomSize = (romData[5] & 0xFF) * 8192;   // 8KB units

        mapperNumber = ((romData[6] >> 4) & 0x0F) | (romData[7] & 0xF0);

        // Check for "DiskDude" or other junk in bytes 7-15
        boolean hasJunk = false;
        for (int i = 12; i < 16; i++) {
            if (romData[i] != 0) {
                hasJunk = true;
                break;
            }
        }
        if (hasJunk) {
            mapperNumber &= 0x0F;
        }

        mirrorVertical = (romData[6] & 0x01) != 0;

        // Load PRG ROM (starts at byte 16, after header)
        prgROM = new byte[prgRomSize];
        System.arraycopy(romData, 16, prgROM, 0, prgRomSize);

        // Load CHR ROM (comes after PRG ROM)
        if (chrRomSize > 0) {
            chrROM = new byte[chrRomSize];
            System.arraycopy(romData, 16 + prgRomSize, chrROM, 0, chrRomSize);
        } else {
            chrROM = new byte[8192]; // CHR-RAM
        }

        switch (mapperNumber) {
            case 0:
                mapper = new Mapper0(prgROM, chrROM);
                break;
            case 1:
                mapper = new Mapper1(prgROM, chrROM);
                break;
            case 4:
                mapper = new Mapper4(prgROM, chrROM);
                break;
            case 148:
                mapper = new Mapper148(prgROM, chrROM);
                break;
            default:
                throw new UnsupportedOperationException("Mapper " + mapperNumber + " not implemented");
        }
    }

    public int read(int address) {
        return mapper.read(address);
    }

    public void write(int address, int value) {
        mapper.write(address, value);
    }

    public byte[] getChrROM() {
        return chrROM;
    }

    public boolean isMirrorVertical() {
        return mirrorVertical;
    }

    public Mapper getMapper() {
        return mapper;
    }
}

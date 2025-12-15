package com.sidpatchy.yolones.Hardware;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Cartridge {
    private byte[] prgROM;  // Program ROM (CPU reads this)
    private byte[] chrROM;  // Character ROM (PPU reads this)
    private int mapperNumber;
    private boolean mirrorVertical;  // PPU mirroring mode

    public Cartridge(String romFilePath) throws IOException {
        byte[] romData = Files.readAllBytes(Paths.get(romFilePath));

        // Parse iNES header (first 16 bytes)
        // Bytes 0-3: "NES" + 0x1A (magic number)
        int prgRomSize = romData[4] * 16384;  // 16KB units
        int chrRomSize = romData[5] * 8192;   // 8KB units

        mapperNumber = ((romData[6] >> 4) & 0x0F) | (romData[7] & 0xF0);
        mirrorVertical = (romData[6] & 0x01) != 0;

        // Load PRG ROM (starts at byte 16, after header)
        prgROM = new byte[prgRomSize];
        System.arraycopy(romData, 16, prgROM, 0, prgRomSize);

        // Load CHR ROM (comes after PRG ROM)
        chrROM = new byte[chrRomSize];
        System.arraycopy(romData, 16 + prgRomSize, chrROM, 0, chrRomSize);
    }

    public int read(int address) {
        // For mapper 0 (NROM), it's simple:
        if (address >= 0x8000) {
            // If PRG ROM is 16KB, mirror it
            int index = address - 0x8000;
            if (prgROM.length == 16384) {
                index %= 16384;  // Mirror the 16KB
            }
            return prgROM[index] & 0xFF;
        }
        return 0;
    }

    public void write(int address, int value) {
        // TODO -
        //  ROM is read-only, but some mappers use writes for bank switching
        //  Just ignoring writes for now
    }

    public int readCHR(int address) {
        // PPU will call this for graphics data
        return chrROM[address] & 0xFF;
    }
}

package com.sidpatchy.yolones.Hardware.Mappers;

public class Mapper4 implements Mapper {
    private final byte[] prgROM;
    private final byte[] chrROM;
    private final byte[] prgRAM = new byte[0x2000];

    private int targetRegister = 0;
    private int prgBankMode = 0;
    private int chrInversion = 0;
    private final int[] registers = new int[8];

    private int irqLatch = 0;
    private int irqCounter = 0;
    private boolean irqEnabled = false;
    private boolean irqReload = false;
    private boolean irqPending = false;
    private int mirroringMode = 0; // 0: Vertical, 1: Horizontal

    private final int[] prgBanks = new int[4];
    private final int[] chrBanks = new int[8];

    public Mapper4(byte[] prgROM, byte[] chrROM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
        updateBanks();
    }

    private void updateBanks() {
        if (prgBankMode == 0) {
            prgBanks[0] = registers[6];
            prgBanks[1] = registers[7];
            prgBanks[2] = (prgROM.length / 0x2000) - 2;
            prgBanks[3] = (prgROM.length / 0x2000) - 1;
        } else {
            prgBanks[0] = (prgROM.length / 0x2000) - 2;
            prgBanks[1] = registers[7];
            prgBanks[2] = registers[6];
            prgBanks[3] = (prgROM.length / 0x2000) - 1;
        }

        if (chrInversion == 0) {
            chrBanks[0] = registers[0] & 0xFE;
            chrBanks[1] = registers[0] | 0x01;
            chrBanks[2] = registers[1] & 0xFE;
            chrBanks[3] = registers[1] | 0x01;
            chrBanks[4] = registers[2];
            chrBanks[5] = registers[3];
            chrBanks[6] = registers[4];
            chrBanks[7] = registers[5];
        } else {
            chrBanks[0] = registers[2];
            chrBanks[1] = registers[3];
            chrBanks[2] = registers[4];
            chrBanks[3] = registers[5];
            chrBanks[4] = registers[0] & 0xFE;
            chrBanks[5] = registers[0] | 0x01;
            chrBanks[6] = registers[1] & 0xFE;
            chrBanks[7] = registers[1] | 0x01;
        }
    }

    @Override
    public int read(int address) {
        if (address >= 0x8000) {
            int bank = (address - 0x8000) / 0x2000;
            int offset = (address - 0x8000) % 0x2000;
            int prgIndex = (prgBanks[bank] * 0x2000 + offset) % prgROM.length;
            return prgROM[prgIndex] & 0xFF;
        } else if (address >= 0x6000) {
            return prgRAM[address - 0x6000] & 0xFF;
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0x8000 && address <= 0x9FFF) {
            if ((address & 1) == 0) {
                targetRegister = value & 0x07;
                prgBankMode = (value >> 6) & 0x01;
                chrInversion = (value >> 7) & 0x01;
            } else {
                registers[targetRegister] = value;
            }
            updateBanks();
        } else if (address >= 0xA000 && address <= 0xBFFF) {
            if ((address & 1) == 0) {
                mirroringMode = value & 0x01;
            } else {
                // PRG RAM Protect
            }
        } else if (address >= 0xC000 && address <= 0xDFFF) {
            if ((address & 1) == 0) {
                irqLatch = value;
            } else {
                irqReload = true;
            }
        } else if (address >= 0xE000 && address <= 0xFFFF) {
            if ((address & 1) == 0) {
                irqEnabled = false;
                irqPending = false;
            } else {
                irqEnabled = true;
            }
        } else if (address >= 0x6000 && address <= 0x7FFF) {
            prgRAM[address - 0x6000] = (byte) value;
        }
    }

    @Override
    public int chrRead(int address) {
        int bank = address / 0x0400;
        int offset = address % 0x0400;
        int chrIndex = (chrBanks[bank] * 0x0400 + offset) % Math.max(1, chrROM.length);
        if (chrROM.length == 0) return 0; // CHR-RAM case not fully handled but avoided crash
        return chrROM[chrIndex] & 0xFF;
    }

    @Override
    public void chrWrite(int address, int value) {
        // Assume CHR-ROM for now
    }

    @Override
    public int getMirroringMode() {
        return mirroringMode;
    }

    @Override
    public boolean hasIRQ() {
        return irqPending;
    }

    @Override
    public void clockIRQ() {
        if (irqCounter == 0 || irqReload) {
            irqCounter = irqLatch;
            irqReload = false;
        } else {
            irqCounter--;
        }
        if (irqCounter == 0 && irqEnabled) {
            irqPending = true;
        }
    }
}

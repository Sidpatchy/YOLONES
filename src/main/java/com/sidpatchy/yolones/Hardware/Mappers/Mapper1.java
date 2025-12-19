package com.sidpatchy.yolones.Hardware.Mappers;

public class Mapper1 implements Mapper {
    private final byte[] prgROM;
    private final byte[] chrROM;
    private final byte[] prgRAM = new byte[0x2000];

    // MMC1 Registers
    private int shiftRegister = 0x10;
    private int control = 0x0C; // Initial state: PRG bank mode 3
    private int chrBank0 = 0;
    private int chrBank1 = 0;
    private int prgBank = 0;

    // Derived states
    private int mirroringMode = 0;
    private int prgBankMode = 3;
    private int chrBankMode = 0;

    public Mapper1(byte[] prgROM, byte[] chrROM) {
        this.prgROM = prgROM;
        this.chrROM = chrROM;
    }

    @Override
    public int read(int address) {
        if (address >= 0x8000) {
            int prgBankSize = 16384;
            int numBanks = prgROM.length / prgBankSize;
            int bank0, bank1;

            switch (prgBankMode) {
                case 0:
                case 1:
                    // 32KB mode
                    int bank = (prgBank & 0x0E) % (numBanks / 2);
                    return prgROM[(bank * 32768) + (address - 0x8000)] & 0xFF;
                case 2:
                    // Fix first bank at $8000, switch 16KB bank at $C000
                    if (address < 0xC000) {
                        bank0 = 0;
                        return prgROM[bank0 * prgBankSize + (address - 0x8000)] & 0xFF;
                    } else {
                        bank1 = prgBank % numBanks;
                        return prgROM[bank1 * prgBankSize + (address - 0xC000)] & 0xFF;
                    }
                case 3:
                default:
                    // Fix last bank at $C000, switch 16KB bank at $8000
                    if (address < 0xC000) {
                        bank0 = prgBank % numBanks;
                        return prgROM[bank0 * prgBankSize + (address - 0x8000)] & 0xFF;
                    } else {
                        bank1 = numBanks - 1;
                        return prgROM[bank1 * prgBankSize + (address - 0xC000)] & 0xFF;
                    }
            }
        } else if (address >= 0x6000) {
            return prgRAM[address - 0x6000] & 0xFF;
        }
        return 0;
    }

    @Override
    public void write(int address, int value) {
        if (address >= 0x8000) {
            if ((value & 0x80) != 0) {
                shiftRegister = 0x10;
                control |= 0x0C;
                updateControl();
            } else {
                boolean complete = (shiftRegister & 1) != 0;
                shiftRegister >>= 1;
                shiftRegister |= (value & 1) << 4;

                if (complete) {
                    writeRegister(address, shiftRegister);
                    shiftRegister = 0x10;
                }
            }
        } else if (address >= 0x6000 && address <= 0x7FFF) {
            prgRAM[address - 0x6000] = (byte) value;
        }
    }

    private void writeRegister(int address, int value) {
        if (address <= 0x9FFF) {
            control = value;
            updateControl();
        } else if (address <= 0xBFFF) {
            chrBank0 = value;
        } else if (address <= 0xDFFF) {
            chrBank1 = value;
        } else {
            prgBank = value & 0x0F;
            // bit 4 might be used for PRG RAM disable or larger PRG ROMs (MMC1B/C)
        }
    }

    private void updateControl() {
        mirroringMode = control & 0x03;
        // 0: one-screen, lower bank; 1: one-screen, upper bank; 2: vertical; 3: horizontal
        prgBankMode = (control >> 2) & 0x03;
        chrBankMode = (control >> 4) & 0x01;
    }

    @Override
    public int chrRead(int address) {
        if (chrROM.length == 0) return 0; // Should be handled as CHR-RAM elsewhere if needed

        int bankSize = (chrBankMode == 0) ? 8192 : 4096;
        int numBanks = chrROM.length / bankSize;

        if (chrBankMode == 0) {
            int bank = chrBank0 % numBanks;
            return chrROM[bank * 8192 + (address & 0x1FFF)] & 0xFF;
        } else {
            if (address < 0x1000) {
                int bank = chrBank0 % numBanks;
                return chrROM[bank * 4096 + (address & 0x0FFF)] & 0xFF;
            } else {
                int bank = chrBank1 % numBanks;
                return chrROM[bank * 4096 + (address - 0x1000)] & 0xFF;
            }
        }
    }

    @Override
    public void chrWrite(int address, int value) {
        // MMC1 can have CHR-RAM. If chrROM is empty or used as RAM:
        // For now, let's assume it might be RAM if small or explicitly allowed.
        // Many MMC1 games use CHR-RAM (8KB).
        // If chrROM was allocated as 8KB in Cartridge.java when chrRomSize was 0, it acts as RAM.
    }

    @Override
    public int getMirroringMode() {
        switch (mirroringMode) {
            case 0: return 2; // One-screen lower (simplified)
            case 1: return 3; // One-screen upper (simplified)
            case 2: return 0; // Vertical
            case 3: return 1; // Horizontal
            default: return -1;
        }
    }
}

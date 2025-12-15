package com.sidpatchy.yolones.Hardware;

public class Memory {
    private byte[] ram = new byte[0x0800];  // 2KB internal RAM
    private Cartridge cartridge;

    public Memory(Cartridge cart) {
        this.cartridge = cart;
    }

    public int read(int address) {
        address &= 0xFFFF;  // Keep it 16-bit

        if (address < 0x2000) {
            // Internal RAM, mirrored every 0x0800 bytes
            return ram[address & 0x07FF] & 0xFF;

        } else if (address < 0x4000) {
            // PPU registers (0x2000-0x2007, mirrored)
            // TODO: implement PPU reads
            return 0;

        } else if (address < 0x4020) {
            // APU and I/O registers
            // TODO: implement controller/APU reads
            return 0;

        } else {
            // Cartridge space (0x4020-0xFFFF)
            return cartridge.read(address) & 0xFF;
        }
    }

    public void write(int address, int value) {
        address &= 0xFFFF;
        value &= 0xFF;  // Keep it 8-bit

        if (address < 0x2000) {
            // Internal RAM
            ram[address & 0x07FF] = (byte) value;

        } else if (address < 0x4000) {
            // PPU registers
            // TODO: implement PPU writes

        } else if (address < 0x4020) {
            // APU and I/O registers
            // TODO: implement controller/APU writes

        } else {
            // Cartridge space (might have RAM)
            cartridge.write(address, value);
        }
    }
}

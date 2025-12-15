package com.sidpatchy.yolones.Hardware;

public class Memory {
    private byte[] ram = new byte[0x0800];  // 2KB internal RAM
    private Cartridge cartridge;
    private PPU ppu;

    private int controllerState = 0;
    private int controllerShift = 0;

    public Memory(Cartridge cart, PPU ppu) {
        this.cartridge = cart;
        this.ppu = ppu;
    }

    public int read(int address) {
        address &= 0xFFFF;  // Keep it 16-bit

        if (address < 0x2000) {
            // Internal RAM, mirrored every 0x0800 bytes
            return ram[address & 0x07FF] & 0xFF;

        } else if (address < 0x4000) {
            // PPU registers (0x2000-0x2007, mirrored)
            return ppu.readRegister(0x2000 + (address & 0x07));

        } else if (address == 0x4016) {
            // Controller 1 read
            int value = (controllerShift & 0x01);
            controllerShift >>= 1;
            return value | 0x40;

        } else if (address == 0x4017) {
            // Controller 2 (not implemented)
            return 0x40;

        } else if (address < 0x4020) {
            // APU and I/O registers
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
            ppu.writeRegister(0x2000 + (address & 0x07), value);

        } else if (address == 0x4016) {
            // Controller strobe
            if ((value & 0x01) != 0) {
                controllerShift = controllerState;
            }

        } else if (address == 0x4014) {
            // OAM DMA: copy 256 bytes from CPU page (value << 8) to PPU OAM
            int base = (value & 0xFF) << 8;
            for (int i = 0; i < 256; i++) {
                int b = read((base + i) & 0xFFFF);
                ppu.writeRegister(0x2004, b);
            }
            // Note: ignoring the 513/514 cycle stall timing for simplicity

        } else if (address < 0x4020) {
            // APU and I/O registers

        } else {
            // Cartridge space (might have RAM)
            cartridge.write(address, value);
        }
    }

    public void setController(int state) {
        controllerState = state;
    }
}

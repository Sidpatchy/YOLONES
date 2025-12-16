package com.sidpatchy.yolones.Hardware;

public class CPUMemory {
    private byte[] ram = new byte[0x0800];  // 2KB internal RAM
    private Cartridge cartridge;
    private PPU ppu;

    // Controller (0x4016) handling
    // NES semantics:
    // - Write bit0=1: strobe high; reading 0x4016 returns A button repeatedly and does NOT shift.
    // - Transition 1->0: latch current controller state, reset index.
    // - While strobe low: each read shifts out one bit (A, B, SELECT, START, UP, DOWN, LEFT, RIGHT).
    //   After 8 reads, 1 is returned on subsequent reads.
    private int controllerState = 0;   // live buttons bitmask from input
    private int controllerShift = 0;   // latched shift register
    private boolean controllerStrobe = false;
    private int controllerIndex = 0;   // number of bits already read (0..8)

    public CPUMemory(Cartridge cart, PPU ppu) {
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
            int value;
            if (controllerStrobe) {
                // While strobe is high, always return current A button state (bit0)
                value = controllerState & 0x01;
            } else {
                if (controllerIndex < 8) {
                    value = controllerShift & 0x01;
                    controllerShift >>= 1;
                    controllerIndex++;
                } else {
                    // After 8 reads, return 1
                    value = 1;
                }
            }
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
            boolean newStrobe = (value & 0x01) != 0;
            // On 1->0 transition, latch the controller state
            if (controllerStrobe && !newStrobe) {
                controllerShift = controllerState;
                controllerIndex = 0;
            }
            controllerStrobe = newStrobe;

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

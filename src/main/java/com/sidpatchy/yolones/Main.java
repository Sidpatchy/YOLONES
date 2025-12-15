package com.sidpatchy.yolones;

import com.sidpatchy.yolones.Hardware.CPU6502;
import com.sidpatchy.yolones.Hardware.Cartridge;
import com.sidpatchy.yolones.Hardware.Memory;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        // 1. Load the ROM
        Cartridge cart = new Cartridge("/var/home/osprey/Downloads/nestest.nes");

        // 2. Create memory system with the cart
        Memory memory = new Memory(cart);

        // 3. Create CPU with memory
        CPU6502 cpu = new CPU6502(memory);

        // 4. Reset the CPU (sets PC to reset vector)
        cpu.reset();

        // 5. Run the emulation loop
        while (cpu.isRunning()) {
            cpu.step();
        }
    }
}


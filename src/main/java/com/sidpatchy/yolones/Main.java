package com.sidpatchy.yolones;

import com.sidpatchy.yolones.Hardware.CPU6502;
import com.sidpatchy.yolones.Hardware.Cartridge;
import com.sidpatchy.yolones.Hardware.Memory;
import com.sidpatchy.yolones.Hardware.PPU;
import com.sidpatchy.yolones.Hardware.PPUMemory;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Load the ROM
        Cartridge cart = new Cartridge("/var/home/osprey/Downloads/nestest.nes");

        // 2. Create PPU memory and PPU
        PPUMemory ppuMemory = new PPUMemory(cart.getChrROM(), cart.isMirrorVertical());
        PPU ppu = new PPU(ppuMemory);

        // 3. Create CPU memory with the cart and PPU
        Memory memory = new Memory(cart, ppu);

        // 4. Create CPU with memory
        CPU6502 cpu = new CPU6502(memory);

        // 5. Create window
        FrameBufferRenderer renderer = new FrameBufferRenderer(3);
        FrameBufferRenderer.createWindow(renderer);

        renderer.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    memory.setController(0x08);  // START button
                }
            }
            public void keyReleased(java.awt.event.KeyEvent e) {
                memory.setController(0x00);
            }
        });
        renderer.setFocusable(true);

        // 6. Reset the CPU (sets PC to reset vector)
        cpu.reset();

        // 7. Run the emulation loop
        while (cpu.isRunning()) {
            cpu.step();

            for (int i = 0; i < 3; i++) {
                if (ppu.tick()) {
                    System.out.println("NMI triggered!");
                    cpu.triggerNMI();
                }
            }

            if (ppu.getScanline() == 0 && ppu.getCycle() == 0) {
                System.out.println("Frame complete, updating display");
                renderer.updateFrame(ppu.getFramebuffer());
            }
        }
    }
}

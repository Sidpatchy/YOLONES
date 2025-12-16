package com.sidpatchy.yolones;

import com.sidpatchy.yolones.Hardware.CPU6502;
import com.sidpatchy.yolones.Hardware.Cartridge;
import com.sidpatchy.yolones.Hardware.CPUMemory;
import com.sidpatchy.yolones.Hardware.PPU;
import com.sidpatchy.yolones.Hardware.PPUMemory;
import com.sidpatchy.yolones.input.ControllerHandler;
import com.sidpatchy.yolones.input.KeyboardController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Load the ROM, various roms listed for testing purposes.
        Cartridge cart = new Cartridge("/var/home/osprey/Downloads/nestest.nes");
        //Cartridge cart = new Cartridge("/var/home/osprey/Games/ROMs/NES Games/AccuracyCoin.nes");
        //Cartridge cart = new Cartridge("/var/home/osprey/Games/ROMs/NES Games/Super Mario Bros. (World).nes");

        // 2. Create PPU memory and PPU
        PPUMemory ppuMemory = new PPUMemory(cart.getChrROM(), cart.isMirrorVertical());
        PPU ppu = new PPU(ppuMemory);

        // 3. Create CPU memory with the cart and PPU
        CPUMemory memory = new CPUMemory(cart, ppu);

        // 4. Create CPU with memory
        CPU6502 cpu = new CPU6502(memory);

        // 5. Create window
        FrameBufferRenderer renderer = new FrameBufferRenderer(3);
        javax.swing.JFrame frame = FrameBufferRenderer.createWindow(renderer);

        // Input setup via ControllerHandler
        // Keyboard input: WASD (D-pad), Z (B), X (A), Enter (Start), Shift (Select)
        ControllerHandler controllerHandler = new ControllerHandler(memory);
        KeyboardController keyboard = new KeyboardController();
        controllerHandler.setController(keyboard, renderer, frame);
        renderer.setFocusable(true);
        renderer.requestFocusInWindow();

        // 6. Reset the CPU (sets PC to reset vector)
        cpu.reset();

        // 7. Run the emulation loop
        while (cpu.isRunning()) {
            // Update input state
            controllerHandler.update();
            cpu.step();

            for (int i = 0; i < 3; i++) {
                if (ppu.tick()) {
                    logger.debug("NMI triggered");
                    cpu.triggerNMI();
                }
            }

            if (ppu.getScanline() == 0 && ppu.getCycle() == 0) {
                logger.info("Frame complete, updating display");
                renderer.updateFrame(ppu.getFramebuffer());
            }
        }
    }
}

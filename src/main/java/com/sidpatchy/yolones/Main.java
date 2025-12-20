package com.sidpatchy.yolones;

import com.sidpatchy.yolones.Hardware.CPU6502;
import com.sidpatchy.yolones.Hardware.Cartridge;
import com.sidpatchy.yolones.Hardware.CPUMemory;
import com.sidpatchy.yolones.Hardware.APU;
import com.sidpatchy.yolones.Hardware.PPU;
import com.sidpatchy.yolones.Hardware.PPUMemory;
import com.sidpatchy.yolones.input.ControllerHandler;
import com.sidpatchy.yolones.input.GamepadController;
import com.sidpatchy.yolones.input.KeyboardController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);
    public static void main(String[] args) throws IOException, InterruptedException {
        // 1. Load the ROM, various roms listed for testing purposes.
        //Cartridge cart = new Cartridge("/var/home/osprey/Downloads/nestest.nes");
        //Cartridge cart = new Cartridge("/var/home/osprey/Games/ROMs/NES Games/AccuracyCoin.nes");
        //Cartridge cart = new Cartridge("/var/home/osprey/Games/ROMs/NES Games/Super Mario Bros. 3 (USA).nes");
        Cartridge cart = new Cartridge("/var/home/osprey/Downloads/Super Mario Bros. (Japan, USA).nes");

        // 2. Create PPU memory and PPU
        PPUMemory ppuMemory = new PPUMemory(cart.getMapper(), cart.isMirrorVertical());
        PPU ppu = new PPU(ppuMemory, cart.getMapper());

        // 3. Create APU, CPU memory with the cart, PPU, and APU
        APU apu = new APU();
        CPUMemory memory = new CPUMemory(cart, ppu, apu);

        // 4. Create CPU with memory
        CPU6502 cpu = new CPU6502(memory);

        // 5. Create window and audio
        FrameBufferRenderer renderer = new FrameBufferRenderer(3);
        javax.swing.JFrame frame = FrameBufferRenderer.createWindow(renderer);
        AudioPlayer audioPlayer = new AudioPlayer();

        // Input setup via ControllerHandler
        // Priority: Gamepad -> Keyboard
        ControllerHandler controllerHandler = new ControllerHandler(memory);
        GamepadController gamepad = new GamepadController();
        if (gamepad.isConnected()) {
            controllerHandler.setController(gamepad, renderer, frame);
        } else {
            KeyboardController keyboard = new KeyboardController();
            controllerHandler.setController(keyboard, renderer, frame);
        }
        renderer.setFocusable(true);
        renderer.requestFocusInWindow();

        // 6. Reset the CPU (sets PC to reset vector)
        cpu.reset();

        // 7. Run the emulation loop
        final double TARGET_FPS = 60.098;
        final double CPU_FREQ = 1789773.0;
        final long NS_PER_FRAME = (long) (1_000_000_000 / TARGET_FPS);
        final int CYCLES_PER_FRAME = 29780; // Roughly 262 * 341 / 3
        long lastFrameTime = System.nanoTime();

        double cyclesPerSample = CPU_FREQ / audioPlayer.getSampleRate();
        double audioCycleCounter = 0;

        while (cpu.isRunning()) {
            int cyclesThisFrame = 0;
            while (cyclesThisFrame < CYCLES_PER_FRAME) {
                // Update input state (maybe not every instruction, but every frame is too slow)
                // For now, let's keep it here or move it to a specific point.
                if (cyclesThisFrame % 100 == 0) {
                    controllerHandler.update();
                }

                int cycles = cpu.step();
                cyclesThisFrame += cycles;

                for (int i = 0; i < cycles; i++) {
                    apu.tick();
                    audioCycleCounter++;
                    if (audioCycleCounter >= cyclesPerSample) {
                        audioPlayer.addSample(apu.getSample());
                        audioCycleCounter -= cyclesPerSample;
                    }
                }

                for (int i = 0; i < cycles * 3; i++) {
                    if (ppu.tick()) {
                        cpu.triggerNMI();
                    }
                }

                if (cart.getMapper().hasIRQ() || apu.hasIRQ()) {
                    cpu.triggerIRQ();
                }
            }

            // Frame is "complete" (reached cycle target)
            renderer.updateFrame(ppu.getFramebuffer());

            // Sync to frame rate
            // We use the audio buffer as our primary timing source.
            // If the audio buffer is full, line.write() will block and slow us down to real-time.
            // If the audio buffer is NOT full, we might be running too fast, so we sleep a bit.
            
            int available = audioPlayer.getAvailableBytes();
            int total = audioPlayer.getBufferSize();
            int buffered = total - available;
            
            // We want to maintain a healthy audio cushion to prevent stuttering.
            // If we have less than 40ms buffered, we don't sleep at all, allowing the 
            // emulator to run as fast as possible to fill the buffer.
            // 40ms @ 44100Hz 16-bit mono = 44100 * 0.04 * 2 = 3528 bytes.
            if (buffered > 3528) {
                long currentTime = System.nanoTime();
                long elapsedTime = currentTime - lastFrameTime;
                long sleepTimeNs = NS_PER_FRAME - elapsedTime;

                if (sleepTimeNs > 1_000_000) {
                    Thread.sleep(sleepTimeNs / 1_000_000);
                }
            }
            
            lastFrameTime = System.nanoTime();
        }
    }
}

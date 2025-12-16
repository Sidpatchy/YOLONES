package com.sidpatchy.yolones.Hardware;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CPU6502 {
    private static final Logger logger = LogManager.getLogger(CPU6502.class);
    // Status flag constants
    private static final int FLAG_CARRY     = 0b00000001;
    private static final int FLAG_ZERO      = 0b00000010;
    private static final int FLAG_INTERRUPT = 0b00000100;
    private static final int FLAG_DECIMAL   = 0b00001000;
    private static final int FLAG_BREAK     = 0b00010000;
    private static final int FLAG_UNUSED    = 0b00100000;
    private static final int FLAG_OVERFLOW  = 0b01000000;
    private static final int FLAG_NEGATIVE  = 0b10000000;

    // Registers
    private boolean running = true;
    private int A, X, Y;   // Registers (8-bit)
    private int PC;        // Program Counter (16-bit)
    private int SP;        // Stack Pointer (8-bit)
    private int status;    // Status flags

    private CPUMemory memory;

    public CPU6502(CPUMemory mem) {
        memory = mem;
    }

    public void step() {
        int opcode = memory.read(PC);

        if (logger.isTraceEnabled()) {
            logger.trace(String.format("%04X  %02X     A:%02X X:%02X Y:%02X P:%02X SP:%02X",
                    PC, opcode, A, X, Y, status, SP));
        }

        PC++;
        executeInstruction(opcode);
    }

    public void reset() {
        // Read reset vector from 0xFFFC/0xFFFD
        int low = memory.read(0xFFFC);
        int high = memory.read(0xFFFD);
        PC = (high << 8) | low;

        // Initialize other stuff
        SP = 0xFD;
        status = FLAG_UNUSED | FLAG_INTERRUPT;  // Bit 5 (unused, always 1) and bit 2 (IRQ disable)
        A = X = Y = 0;
    }

    private void setCarry(boolean set) {
        if (set) {
            status |= FLAG_CARRY;
        } else {
            status &= ~FLAG_CARRY;
        }
    }

    private boolean getCarry() {
        return (status & FLAG_CARRY) != 0;
    }

    public void triggerNMI() {
        // Push PC (high byte first)
        memory.write(0x0100 + SP--, (PC >> 8) & 0xFF);
        memory.write(0x0100 + SP--, PC & 0xFF);

        // Push status (without break flag, with unused flag set)
        memory.write(0x0100 + SP--, (status & ~FLAG_BREAK) | FLAG_UNUSED);

        // Set interrupt disable flag
        status |= FLAG_INTERRUPT;

        // Jump to NMI vector at 0xFFFA/0xFFFB
        int low = memory.read(0xFFFA);
        int high = memory.read(0xFFFB);
        PC = (high << 8) | low;
    }

    public void executeInstruction(int opcode) {
        int zpAddr;
        int low;
        int high;
        int addr;
        int returnAddr;
        int offset;
        int value;
        int result;

        // https://www.nesdev.org/wiki/Visual6502wiki/6502_all_256_Opcodes
        // https://www.nesdev.org/obelisk-6502-guide/reference.html
        switch(opcode) {
            case 0x00: // BRK
                PC++; // BRK is technically a 2-byte instruction; skip the padding byte

                // Push PC to stack (High byte first)
                memory.write(0x0100 + SP--, (PC >> 8) & 0xFF);
                memory.write(0x0100 + SP--, PC & 0xFF);

                // Push Status Register to stack
                // For BRK, we set the BREAK flag (bit 4) and the UNUSED flag (bit 5)
                memory.write(0x0100 + SP--, status | FLAG_BREAK | FLAG_UNUSED);

                // Set Interrupt Disable flag to prevent further IRQs
                status |= FLAG_INTERRUPT;

                // Jump to the IRQ/BRK vector at 0xFFFE/0xFFFF
                low = memory.read(0xFFFE);
                high = memory.read(0xFFFF);
                PC = (high << 8) | low;
                break;

            // Load / Store
            case 0xA9: // LDA immediate
                A = readImmediate();
                setZeroAndNegativeFlags(A);
                break;
            case 0xA5: // LDA zero page
                A = readZeroPage();
                setZeroAndNegativeFlags(A);
                break;
            case 0xB5: // LDA zero page,X
                A = readZeroPageX();
                setZeroAndNegativeFlags(A);
                break;
            case 0xAD: // LDA absolute
                A = readAbsolute();
                setZeroAndNegativeFlags(A);
                break;
            case 0xBD: // LDA absolute,X
                A = readAbsoluteX();
                setZeroAndNegativeFlags(A);
                break;
            case 0xB9: // LDA absolute,Y
                A = readAbsoluteY();
                setZeroAndNegativeFlags(A);
                break;
            case 0xA1: // LDA (indirect,X)
                A = readIndirectX();
                setZeroAndNegativeFlags(A);
                break;
            case 0xB1: // LDA (indirect),Y
                A = readIndirectY();
                setZeroAndNegativeFlags(A);
                break;
            case 0x85: // STA zero page
                zpAddr = memory.read(PC++);
                memory.write(zpAddr, A);
                break;
            case 0x95: // STA zero page, X
                zpAddr = (memory.read(PC++) + X) & 0xFF;
                memory.write(zpAddr, A);
                break;
            case 0x8D: // STA absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                memory.write(addr, A);
                break;
            case 0x9D: // STA absolute, X
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                memory.write((addr + X) & 0xFFFF, A);
                break;
            case 0x99: // STA absolute, Y
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                memory.write((addr + Y) & 0xFFFF, A);
                break;
            case 0x81: // STA (indirect,X)
                zpAddr = memory.read(PC++);
                int zpX = (zpAddr + X) & 0xFF;
                low = memory.read(zpX);
                high = memory.read((zpX + 1) & 0xFF);
                addr = (high << 8) | low;
                memory.write(addr, A);
                break;
            case 0x91: // STA (indirect),Y
                zpAddr = memory.read(PC++);
                low = memory.read(zpAddr);
                high = memory.read((zpAddr + 1) & 0xFF);
                addr = ((high << 8) | low);
                addr = (addr + Y) & 0xFFFF;
                memory.write(addr, A);
                break;
            case 0x8E: // STX absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                memory.write(addr, X);
                break;
            case 0x86: // STX zero page
                zpAddr = memory.read(PC++);
                memory.write(zpAddr, X);
                break;
            case 0x96: // STX zero page, Y
                zpAddr = (memory.read(PC++) + Y) & 0xFF;
                memory.write(zpAddr, X);
                break;
            case 0x84: // STY zero page
                zpAddr = memory.read(PC++);
                memory.write(zpAddr, Y);
                break;
            case 0x94: // STY zero page, X
                zpAddr = (memory.read(PC++) + X) & 0xFF;
                memory.write(zpAddr, Y);
                break;
            case 0x8C: // STY absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                memory.write(addr, Y);
                break;
            case 0xA2: // LDX immediate
                X = memory.read(PC++);
                setZeroAndNegativeFlags(X);
                break;
            case 0xA6: // LDX zero page
                X = readZeroPage();
                setZeroAndNegativeFlags(X);
                break;
            case 0xB6: // LDX zero page,Y
                zpAddr = (memory.read(PC++) + Y) & 0xFF;
                X = memory.read(zpAddr);
                setZeroAndNegativeFlags(X);
                break;
            case 0xAE: // LDX absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                X = memory.read(addr);
                setZeroAndNegativeFlags(X);
                break;
            case 0xBE: // LDX absolute,Y
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                addr = (addr + Y) & 0xFFFF;
                X = memory.read(addr);
                setZeroAndNegativeFlags(X);
                break;
            case 0xA0: // LDY immediate
                Y = memory.read(PC++);
                setZeroAndNegativeFlags(Y);
                break;
            case 0xA4: // LDY zero page
                Y = readZeroPage();
                setZeroAndNegativeFlags(Y);
                break;
            case 0xB4: // LDY zero page,X
                Y = readZeroPageX();
                setZeroAndNegativeFlags(Y);
                break;
            case 0xAC: // LDY absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                Y = memory.read(addr);
                setZeroAndNegativeFlags(Y);
                break;
            case 0xBC: // LDY absolute,X
                Y = readAbsoluteX();
                setZeroAndNegativeFlags(Y);
                break;
            case 0x83: // SAX (indirect,X)
                memory.write(readIndexedIndirect(), A & X);
                break;
            case 0x87: // SAX zero page
                memory.write(memory.read(PC++), A & X);
                break;
            case 0x8F: // SAX absolute
                memory.write(readAbsoluteAddr(), A & X);
                break;
            case 0x97: // SAX zero page,Y
                memory.write((memory.read(PC++) + Y) & 0xFF, A & X);
                break;
            case 0xA3: // LAX (indirect,X)
                A = X = readIndirectX();
                setZeroAndNegativeFlags(A);
                break;
            case 0xA7: // LAX zero page
                A = X = readZeroPage();
                setZeroAndNegativeFlags(A);
                break;
            case 0xAF: // LAX absolute
                A = X = readAbsolute();
                setZeroAndNegativeFlags(A);
                break;
            case 0xB3: // LAX (indirect),Y
                A = X = readIndirectY();
                setZeroAndNegativeFlags(A);
                break;
            case 0xB7: // LAX zero page,Y
                A = X = memory.read((memory.read(PC++) + Y) & 0xFF);
                setZeroAndNegativeFlags(A);
                break;
            case 0xBF: // LAX absolute,Y
                A = X = readAbsoluteY();
                setZeroAndNegativeFlags(A);
                break;

            // Transfers
            case 0xAA: // TAX (A -> X)
                X = A;
                setZeroAndNegativeFlags(X);
                break;
            case 0x8A: // TXA (X -> A)
                A = X;
                setZeroAndNegativeFlags(A);
                break;
            case 0xA8: // TAY (A -> Y)
                Y = A;
                setZeroAndNegativeFlags(Y);
                break;
            case 0x98: // TYA (Y -> A)
                A = Y;
                setZeroAndNegativeFlags(A);
                break;
            case 0xBA: // TSX (SP -> X)
                X = SP;
                setZeroAndNegativeFlags(X);
                break;

            // Stack Operations
            case 0x9A: // TXS (X -> SP, set up stack)
                SP = X;
                break;
            case 0x48: // PHA (push A to stack)
                memory.write(0x0100 + SP, A);
                SP--;
                break;
            case 0x68: // PLA (pull A from stack)
                A = memory.read(0x0100 + ++SP);
                setZeroAndNegativeFlags(A);
                break;
            case 0x08: // PHP (push status to stack)
                memory.write(0x0100 + SP--, status | FLAG_BREAK | FLAG_UNUSED);
                break;
            case 0x28: // PLP (pull status from stack)
                status = memory.read(0x0100 + ++SP) & ~FLAG_BREAK;
                break;

            // Jumps / Calls
            case 0x4C: // JMP absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                PC = addr;
                break;
            case 0x6C: // JMP indirect
                low = memory.read(PC++);
                high = memory.read(PC++);
                int indirectAddr = (high << 8) | low;

                // Emulate 6502 page-wrap bug when low byte is 0xFF
                int ptrLow = memory.read(indirectAddr);
                int nextAddr = (indirectAddr & 0xFF00) | ((indirectAddr + 1) & 0x00FF);
                int ptrHigh = memory.read(nextAddr);
                PC = (ptrHigh << 8) | ptrLow;
                break;
            case 0x20: // JSR (call subroutine)
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;

                // Push return address onto stack
                returnAddr = PC - 1;
                memory.write(0x0100 + SP--, (returnAddr >> 8) & 0xFF);
                memory.write(0x0100 + SP--, returnAddr & 0xFF);

                PC = addr; // jump to subroutine
                break;
            case 0x60: // RTS (return from subroutine)
                low = memory.read(0x0100 + ++SP);
                high = memory.read(0x0100 + ++SP);
                PC = ((high << 8) | low) + 1;
                break;
            case 0x40: // RTI (return from interrupt)
                status = (memory.read(0x0100 + ++SP) & ~FLAG_BREAK) | FLAG_UNUSED;  // Status first
                low = memory.read(0x0100 + ++SP);     // Then PC low
                high = memory.read(0x0100 + ++SP);    // Then PC high
                PC = (high << 8) | low;
                break;

            // Branches
            case 0xD0: // BNE (branch if not equal/zero flag clear)
                offset = (byte) memory.read(PC++);  // Cast to signed byte
                if ((status & FLAG_ZERO) == 0) {
                    PC += offset;
                }
                break;
            case 0xF0: // BEQ (branch if equal/zero flag set)
                offset = (byte) memory.read(PC++);  // Cast to signed byte
                if ((status & FLAG_ZERO) != 0) {
                    PC += offset;
                }
                break;
            case 0x10: // BPL (branch if plus/negative flag clear)
                offset = (byte) memory.read(PC++);  // Cast to signed byte
                if ((status & FLAG_NEGATIVE) == 0) {
                    PC += offset;
                }
                break;
            case 0x30: // BMI (branch if minus/negative flag set)
                offset = (byte) memory.read(PC++);  // Cast to signed byte
                if ((status & FLAG_NEGATIVE) != 0) {
                    PC += offset;
                }
                break;
            case 0x90: // BCC (branch if carry flag clear)
                offset = (byte) memory.read(PC++);
                if ((status & FLAG_CARRY) == 0) {
                    PC += offset;
                }
                break;
            case 0xB0: // BCS (branch if carry flag set)
                offset = (byte) memory.read(PC++);
                if ((status & FLAG_CARRY) != 0) {
                    PC += offset;
                }
                break;
            case 0x50: // BVC (branch if overflow flag clear)
                offset = (byte) memory.read(PC++);
                if ((status & FLAG_OVERFLOW) == 0) {
                    PC += offset;
                }
                break;
            case 0x70: // BVS (branch if overflow flag set)
                offset = (byte) memory.read(PC++);
                if ((status & FLAG_OVERFLOW) != 0) {
                    PC += offset;
                }
                break;

            // Increment / Decrement
            case 0xE6: // INC zero page
                zpAddr = memory.read(PC++);
                value = (memory.read(zpAddr) + 1) & 0xFF;
                memory.write(zpAddr, value);
                setZeroAndNegativeFlags(value);
                break;
            case 0xF6: // INC zero page,X
                zpAddr = (memory.read(PC++) + X) & 0xFF;
                value = (memory.read(zpAddr) + 1) & 0xFF;
                memory.write(zpAddr, value);
                setZeroAndNegativeFlags(value);
                break;
            case 0xEE: // INC absolute
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                value = (memory.read(addr) + 1) & 0xFF;
                memory.write(addr, value);
                setZeroAndNegativeFlags(value);
                break;
            case 0xFE: // INC absolute,X
                low = memory.read(PC++);
                high = memory.read(PC++);
                addr = (high << 8) | low;
                addr = (addr + X) & 0xFFFF;
                value = (memory.read(addr) + 1) & 0xFF;
                memory.write(addr, value);
                setZeroAndNegativeFlags(value);
                break;
            case 0xE8: // INX (increment X)
                X = (X + 1) & 0xFF;
                setZeroAndNegativeFlags(X);
                break;
            case 0xC8: // INY (increment Y)
                Y = (Y + 1) & 0xFF;
                setZeroAndNegativeFlags(Y);
                break;
            case 0xC6: // DEC zero page
                dec(memory.read(PC++));
                break;
            case 0xD6: // DEC zero page,X
                dec((memory.read(PC++) + X) & 0xFF);
                break;
            case 0xCE: // DEC absolute
                dec(readAbsoluteAddr());
                break;
            case 0xDE: // DEC absolute,X
                dec(readAbsoluteXAddr());
                break;
            case 0xCA: // DEX (decrement X)
                X = (X - 1) & 0xFF;
                setZeroAndNegativeFlags(X);
                break;
            case 0x88: // DEY (decrement Y)
                Y = (Y - 1) & 0xFF;
                setZeroAndNegativeFlags(Y);
                break;

            // Comparison
            case 0xC9: // CMP immediate
                cmp(A, readImmediate());
                break;
            case 0xC5: // CMP zero page
                cmp(A, readZeroPage());
                break;
            case 0xD5: // CMP zero page,X
                cmp(A, readZeroPageX());
                break;
            case 0xCD: // CMP absolute
                cmp(A, readAbsolute());
                break;
            case 0xDD: // CMP absolute,X
                cmp(A, readAbsoluteX());
                break;
            case 0xD9: // CMP absolute,Y
                cmp(A, readAbsoluteY());
                break;
            case 0xC1: // CMP (indirect,X)
                cmp(A, readIndirectX());
                break;
            case 0xD1: // CMP (indirect),Y
                cmp(A, readIndirectY());
                break;
            case 0xE0: // CPX immediate
                cmp(X, readImmediate());
                break;
            case 0xE4: // CPX zero page
                cmp(X, readZeroPage());
                break;
            case 0xEC: // CPX absolute
                cmp(X, readAbsolute());
                break;
            case 0xC0: // CPY immediate
                cmp(Y, readImmediate());
                break;
            case 0xC4: // CPY zero page
                cmp(Y, readZeroPage());
                break;
            case 0xCC: // CPY absolute
                cmp(Y, readAbsolute());
                break;

            // AND
            case 0x29: // AND immediate
                A &= readImmediate();
                setZeroAndNegativeFlags(A);
                break;
            case 0x25: // AND zero page
                A &= readZeroPage();
                setZeroAndNegativeFlags(A);
                break;
            case 0x35: // AND zero page,X
                A &= readZeroPageX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x2D: // AND absolute
                A &= readAbsolute();
                setZeroAndNegativeFlags(A);
                break;
            case 0x3D: // AND absolute,X
                A &= readAbsoluteX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x39: // AND absolute,Y
                A &= readAbsoluteY();
                setZeroAndNegativeFlags(A);
                break;
            case 0x21: // AND (indirect,X)
                A &= readIndirectX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x31:
                A &= readIndirectY();
                setZeroAndNegativeFlags(A);
                break;

            // OR
            case 0x09: // OR immediate
                A |= readImmediate();
                setZeroAndNegativeFlags(A);
                break;
            case 0x05: // OR zero page
                A |= readZeroPage();
                setZeroAndNegativeFlags(A);
                break;
            case 0x15: // OR zero page,X
                A |= readZeroPageX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x0D: // OR absolute
                A |= readAbsolute();
                setZeroAndNegativeFlags(A);
                break;
            case 0x1D: // OR absolute,X
                A |= readAbsoluteX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x19: // OR absolute,Y
                A |= readAbsoluteY();
                setZeroAndNegativeFlags(A);
                break;
            case 0x01: // OR (indirect,X)
                A |= readIndirectX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x11: // OR (indirect),Y
                A |= readIndirectY();
                setZeroAndNegativeFlags(A);
                break;

            // Exclusive OR
            case 0x49: // EOR immediate
                A ^= readImmediate();
                setZeroAndNegativeFlags(A);
                break;
            case 0x45: // EOR zero page
                A ^= readZeroPage();
                setZeroAndNegativeFlags(A);
                break;
            case 0x55: // EOR zero page,X
                A ^= readZeroPageX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x4D: // EOR absolute
                A ^= readAbsolute();
                setZeroAndNegativeFlags(A);
                break;
            case 0x5D: // EOR absolute,X
                A ^= readAbsoluteX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x59: // EOR absolute,Y
                A ^= readAbsoluteY();
                setZeroAndNegativeFlags(A);
                break;
            case 0x41: // EOR (indirect,X)
                A ^= readIndirectX();
                setZeroAndNegativeFlags(A);
                break;
            case 0x51: // EOR (indirect),Y
                A ^= readIndirectY();
                setZeroAndNegativeFlags(A);
                break;

            // Subtract with Carry
            case 0xE9: // SBC immediate
                sbc(readImmediate());
                break;
            case 0xE5: // SBC zero page
                sbc(readZeroPage());
                break;
            case 0xF5: // SBC zero page,X
                sbc(readZeroPageX());
                break;
            case 0xED: // SBC absolute
                sbc(readAbsolute());
                break;
            case 0xFD: // SBC absolute,X
                sbc(readAbsoluteX());
                break;
            case 0xF9: // SBC absolute,Y
                sbc(readAbsoluteY());
                break;
            case 0xE1: // SBC (indirect,X)
                sbc(readIndirectX());
                break;
            case 0xF1: // SBC (indirect),Y
                sbc(readIndirectY());
                break;

            // Add with Carry
            case 0x69: // ADC immediate
                adc(readImmediate());
                break;
            case 0x65: // ADC zero page
                adc(readZeroPage());
                break;
            case 0x75: // ADC zero page,X
                adc(readZeroPageX());
                break;
            case 0x6D: // ADC absolute
                adc(readAbsolute());
                break;
            case 0x7D: // ADC absolute,X
                adc(readAbsoluteX());
                break;
            case 0x79: // ADC absolute,Y
                adc(readAbsoluteY());
                break;
            case 0x61: // ADC (indirect,X)
                adc(readIndirectX());
                break;
            case 0x71: // ADC (indirect),Y
                adc(readIndirectY());
                break;

            // Shifts and Rotates
            case 0x4A: // LSR accumulator
                setCarry((A & 0x01) != 0);  // Bit 0 goes to carry
                A = (A >> 1) & 0x7F;        // Shift right, clear bit 7
                setZeroAndNegativeFlags(A);
                break;
            case 0x46: // LSR zero page
                lsr(memory.read(PC++), true);
                break;
            case 0x56: // LSR zero page,X
                lsr((memory.read(PC++) + X) & 0xFF, true);
                break;
            case 0x4E: // LSR absolute
                lsr(readAbsoluteAddr(), false);
                break;
            case 0x5E: // LSR absolute,X
                lsr(readAbsoluteXAddr(), false);
                break;
            case 0x0A: // ASL accumulator
                setCarry((A & 0x80) != 0);  // Bit 7 goes to carry
                A = (A << 1) & 0xFF;        // Shift left, bit 0 becomes 0
                setZeroAndNegativeFlags(A);
                break;
            case 0x06: // ASL zero page
                asl(memory.read(PC++));
                break;
            case 0x16: // ASL zero page,X
                asl((memory.read(PC++) + X) & 0xFF);
                break;
            case 0x0E: // ASL absolute
                asl(readAbsoluteAddr());
                break;
            case 0x1E: // ASL absolute,X
                asl(readAbsoluteXAddr());
                break;
            case 0x2A: // ROL accumulator
                rol_accumulator();
                break;
            case 0x26: // ROL zero page
                rol(memory.read(PC++));
                break;
            case 0x36: // ROL zero page,X
                rol((memory.read(PC++) + X) & 0xFF);
                break;
            case 0x2E: // ROL absolute
                rol(readAbsoluteAddr());
                break;
            case 0x3E: // ROL absolute,X
                rol(readAbsoluteXAddr());
                break;
            case 0x6A: // ROR accumulator
                ror_accumulator();
                break;
            case 0x66: // ROR zero page
                ror(memory.read(PC++));
                break;
            case 0x76: // ROR zero page,X
                ror((memory.read(PC++) + X) & 0xFF);
                break;
            case 0x6E: // ROR absolute
                ror(readAbsoluteAddr());
                break;
            case 0x7E: // ROR absolute,X
                ror(readAbsoluteXAddr());
                break;

            // Flags
            case 0x18: // CLC (clear carry flag)
                status &= ~FLAG_CARRY;
                break;
            case 0x38: // SEC (set carry flag)
                status |= FLAG_CARRY;
                break;
            case 0xD8: // CLD (clear decimal flag)
                status &= ~FLAG_DECIMAL;
                break;
            case 0xF8: // SED (set decimal flag)
                status |= FLAG_DECIMAL;
                break;
            case 0x78: // SEI (set interrupt disable flag)
                status |= FLAG_INTERRUPT;
                break;
            case 0x58: // CLI (clear interrupt disable flag)
                status &= ~FLAG_INTERRUPT;
                break;
            case 0xB8: // CLV (clear overflow flag)
                status &= ~FLAG_OVERFLOW;
                break;

            // Bit Test
            case 0x24: // BIT zero page
                bit(readZeroPage());
                break;
            case 0x2C: // BIT absolute
                bit(readAbsolute());
                break;

            // Illegals
            // SLO family (ASL + ORA)
            case 0x07: rmwCombo(memory.read(PC++), this::aslValue, this::ora); break;
            case 0x17: rmwCombo((memory.read(PC++) + X) & 0xFF, this::aslValue, this::ora); break;
            case 0x0F: rmwCombo(readAbsoluteAddr(), this::aslValue, this::ora); break;
            case 0x1F: rmwCombo(readAbsoluteXAddr(), this::aslValue, this::ora); break;
            case 0x1B: rmwCombo((readAbsoluteAddr() + Y) & 0xFFFF, this::aslValue, this::ora); break;
            case 0x03: rmwCombo(readIndexedIndirect(), this::aslValue, this::ora); break;
            case 0x13: rmwCombo(readIndirectYAddr(), this::aslValue, this::ora); break;

            // RLA family (ROL + AND)
            case 0x27: rmwCombo(memory.read(PC++), this::rolValue, this::and); break;
            case 0x37: rmwCombo((memory.read(PC++) + X) & 0xFF, this::rolValue, this::and); break;
            case 0x2F: rmwCombo(readAbsoluteAddr(), this::rolValue, this::and); break;
            case 0x3F: rmwCombo(readAbsoluteXAddr(), this::rolValue, this::and); break;
            case 0x3B: rmwCombo((readAbsoluteAddr() + Y) & 0xFFFF, this::rolValue, this::and); break;
            case 0x23: rmwCombo(readIndexedIndirect(), this::rolValue, this::and); break;
            case 0x33: rmwCombo(readIndirectYAddr(), this::rolValue, this::and); break;

            // SRE family (LSR + EOR)
            case 0x47: rmwCombo(memory.read(PC++), this::lsrValue, this::eor); break;
            case 0x57: rmwCombo((memory.read(PC++) + X) & 0xFF, this::lsrValue, this::eor); break;
            case 0x4F: rmwCombo(readAbsoluteAddr(), this::lsrValue, this::eor); break;
            case 0x5F: rmwCombo(readAbsoluteXAddr(), this::lsrValue, this::eor); break;
            case 0x5B: rmwCombo((readAbsoluteAddr() + Y) & 0xFFFF, this::lsrValue, this::eor); break;
            case 0x43: rmwCombo(readIndexedIndirect(), this::lsrValue, this::eor); break;
            case 0x53: rmwCombo(readIndirectYAddr(), this::lsrValue, this::eor); break;

            // RRA family (ROR + ADC)
            case 0x67: rmwCombo(memory.read(PC++), this::rorValue, this::adc); break;
            case 0x77: rmwCombo((memory.read(PC++) + X) & 0xFF, this::rorValue, this::adc); break;
            case 0x6F: rmwCombo(readAbsoluteAddr(), this::rorValue, this::adc); break;
            case 0x7F: rmwCombo(readAbsoluteXAddr(), this::rorValue, this::adc); break;
            case 0x7B: rmwCombo((readAbsoluteAddr() + Y) & 0xFFFF, this::rorValue, this::adc); break;
            case 0x63: rmwCombo(readIndexedIndirect(), this::rorValue, this::adc); break;
            case 0x73: rmwCombo(readIndirectYAddr(), this::rorValue, this::adc); break;

            // DCP family (DEC + CMP)
            case 0xC7: rmwCombo(memory.read(PC++), this::decValue, v -> cmp(A, v)); break;
            case 0xD7: rmwCombo((memory.read(PC++) + X) & 0xFF, this::decValue, v -> cmp(A, v)); break;
            case 0xCF: rmwCombo(readAbsoluteAddr(), this::decValue, v -> cmp(A, v)); break;
            case 0xDF: rmwCombo(readAbsoluteXAddr(), this::decValue, v -> cmp(A, v)); break;
            case 0xDB: rmwCombo((readAbsoluteAddr() + Y) & 0xFFFF, this::decValue, v -> cmp(A, v)); break;
            case 0xC3: rmwCombo(readIndexedIndirect(), this::decValue, v -> cmp(A, v)); break;
            case 0xD3: rmwCombo(readIndirectYAddr(), this::decValue, v -> cmp(A, v)); break;

            // ISC family (INC + SBC)
            case 0xE7: rmwCombo(memory.read(PC++), this::incValue, this::sbc); break;
            case 0xF7: rmwCombo((memory.read(PC++) + X) & 0xFF, this::incValue, this::sbc); break;
            case 0xEF: rmwCombo(readAbsoluteAddr(), this::incValue, this::sbc); break;
            case 0xFF: rmwCombo(readAbsoluteXAddr(), this::incValue, this::sbc); break;
            case 0xFB: rmwCombo((readAbsoluteAddr() + Y) & 0xFFFF, this::incValue, this::sbc); break;
            case 0xE3: rmwCombo(readIndexedIndirect(), this::incValue, this::sbc); break;
            case 0xF3: rmwCombo(readIndirectYAddr(), this::incValue, this::sbc); break;

            case 0x0B, 0x2B: // ANC (AND + set carry from bit 7)
                A &= readImmediate();
                setZeroAndNegativeFlags(A);
                setCarry((A & 0x80) != 0);
                break;

            case 0x4B: // ALR (AND + LSR)
                A &= readImmediate();
                setCarry((A & 0x01) != 0);
                A = (A >> 1) & 0x7F;
                setZeroAndNegativeFlags(A);
                break;

            case 0x6B: // ARR (AND + ROR with special carry/overflow)
                A &= readImmediate();
                boolean oldCarry = getCarry();
                A = ((A >> 1) | (oldCarry ? 0x80 : 0)) & 0xFF;

                // Special carry/overflow logic
                setCarry((A & 0x40) != 0);  // Bit 6 -> Carry
                boolean overflow = ((A & 0x40) != 0) ^ ((A & 0x20) != 0);  // Bit 6 XOR bit 5
                if (overflow) {
                    status |= FLAG_OVERFLOW;
                } else {
                    status &= ~FLAG_OVERFLOW;
                }
                setZeroAndNegativeFlags(A);
                break;

            case 0x8B: // XAA (highly unstable - magic constant varies by chip)
                // Most common behavior: A = (A | 0xEE) & X & immediate
                // But 0xEE can be 0x00, 0xFF, 0x11, etc. depending on hardware
                // Using 0xEE as most common for NES
                A = (A | 0xEE) & X & readImmediate();
                setZeroAndNegativeFlags(A);
                break;

            case 0xAB: // LAX immediate (aka LXA/OAL) — unstable
                // On NMOS 6502 as used in the NES, this reads the immediate, ANDs it with
                // (A OR a hardware-specific constant), then loads both A and X with the result.
                // The constant varies by chip; 0xEE is a commonly observed value on NES.
                // This matches hardware tests better than the "pure immediate" variant.
                int immLax = readImmediate();
                A = X = (A | 0xEE) & immLax;
                setZeroAndNegativeFlags(A);
                break;

            case 0xBB: // LAS absolute,Y (SP AND memory -> A, X, SP)
                value = readAbsoluteY();
                A = X = SP = SP & value;
                setZeroAndNegativeFlags(A);
                break;

            case 0xCB: // AXS/SBX (A AND X, then subtract without borrow)
                int operand = readImmediate();
                result = (A & X) - operand;
                setCarry(result >= 0);
                X = result & 0xFF;
                setZeroAndNegativeFlags(X);
                break;

            case 0xEB: // SBC (duplicate of official 0xE9)
                sbc(readImmediate());
                break;

            // Fucky wucky 0x9X family
            case 0x93: // SHA (indirect),Y
                executeUnstableStore(readIndirectAddr(), Y, A & X);
                break;
            case 0x9F: // SHA absolute,Y
                executeUnstableStore(readAbsoluteAddr(), Y, A & X);
                break;
            case 0x9B: // TAS absolute,Y
                SP = A & X;
                executeUnstableStore(readAbsoluteAddr(), Y, SP);
                break;
            case 0x9C: // SHY absolute,X
                executeUnstableStore(readAbsoluteAddr(), X, Y);
                break;
            case 0x9E: // SHX absolute,Y
                executeUnstableStore(readAbsoluteAddr(), Y, X);
                break;

            // Misc.
            case 0xEA: // NOP (no operation)
                break;

            case 0x1A, 0x3A, 0x5A, 0x7A, 0xDA, 0xFA: // NOP (implied)
                break;

            case 0x80, 0x82, 0x89, 0xC2, 0xE2: // NOP immediate (2 bytes)
                PC++;
                break;

            case 0x04, 0x44, 0x64: // NOP zero page (2 bytes)
                PC++;
                break;

            case 0x14, 0x34, 0x54, 0x74, 0xD4, 0xF4: // NOP zero page,X (2 bytes)
                PC++;
                break;

            case 0x0C: // NOP absolute (3 bytes)
                PC += 2;
                break;

            case 0x1C, 0x3C, 0x5C, 0x7C, 0xDC, 0xFC: // NOP absolute,X
                readAbsoluteX();  // Actually performs the read (page cross timing)
                break;

            default:
                logger.error(String.format("Unknown opcode: 0x%02X at PC: 0x%04X", opcode, PC - 1));
                running = false;  // Halt on unknown opcode
                break;
        }
    }

    private void setZeroAndNegativeFlags(int value) {
        // Zero flag
        if (value == 0) {
            status |= FLAG_ZERO;
        } else {
            status &= ~FLAG_ZERO;
        }

        // Negative flag (bit 7)
        if ((value & 0x80) != 0) {
            status |= FLAG_NEGATIVE;
        } else {
            status &= ~FLAG_NEGATIVE;
        }
    }

    // Helper methods for addressing modes
    private int readImmediate() {
        return memory.read(PC++);
    }

    private int readZeroPage() {
        int addr = memory.read(PC++);
        return memory.read(addr);
    }

    private int readZeroPageX() {
        int addr = (memory.read(PC++) + X) & 0xFF;
        return memory.read(addr);
    }

    private int readAbsolute() {
        int low = memory.read(PC++);
        int high = memory.read(PC++);
        int addr = (high << 8) | low;
        return memory.read(addr);
    }

    private int readAbsoluteX() {
        int low = memory.read(PC++);
        int high = memory.read(PC++);
        int base = (high << 8) | low;
        int addr = (base + X) & 0xFFFF;
        return memory.read(addr);
    }

    private int readAbsoluteY() {
        int low = memory.read(PC++);
        int high = memory.read(PC++);
        int base = (high << 8) | low;
        int addr = (base + Y) & 0xFFFF;
        return memory.read(addr);
    }

    private int readIndirectX() {
        int zpAddr = (memory.read(PC++) + X) & 0xFF;
        int low = memory.read(zpAddr);
        int high = memory.read((zpAddr + 1) & 0xFF);
        return memory.read((high << 8) | low);
    }

    private int readIndirectY() {
        int zpAddr = memory.read(PC++);
        int low = memory.read(zpAddr);
        int high = memory.read((zpAddr + 1) & 0xFF);
        int base = (high << 8) | low;
        int addr = (base + Y) & 0xFFFF;
        return memory.read(addr);
    }

    private int readIndirectYAddr() {
        int zpAddr = memory.read(PC++);
        int low = memory.read(zpAddr);
        int high = memory.read((zpAddr + 1) & 0xFF);
        int base = (high << 8) | low;
        return (base + Y) & 0xFFFF;
    }

    private int readIndirectAddr() {
        int zpAddr = memory.read(PC++);
        int low = memory.read(zpAddr);
        int high = memory.read((zpAddr + 1) & 0xFF);
        return (high << 8) | low;
    }

    private int readAbsoluteAddr() {
        int low = memory.read(PC++);
        int high = memory.read(PC++);
        return (high << 8) | low;
    }

    private int readAbsoluteXAddr() {
        int low = memory.read(PC++);
        int high = memory.read(PC++);
        int base = (high << 8) | low;
        return (base + X) & 0xFFFF;
    }

    private int readIndexedIndirect() {
        int zpAddr = (memory.read(PC++) + X) & 0xFF;
        int low = memory.read(zpAddr);
        int high = memory.read((zpAddr + 1) & 0xFF);
        return (high << 8) | low;
    }

    private void cmp(int register, int value) {
        int result = register - value;
        setCarry(register >= value);
        setZeroAndNegativeFlags(result & 0xFF);
    }

    private void adc(int value) {
        int result = A + value + (getCarry() ? 1 : 0);

        // Carry flag: set if result > 255
        setCarry(result > 0xFF);

        // Overflow: set if sign bit is wrong
        // (positive + positive = negative) or (negative + negative = positive)
        boolean overflow = ((A ^ result) & (value ^ result) & 0x80) != 0;
        if (overflow) {
            status |= FLAG_OVERFLOW;
        } else {
            status &= ~FLAG_OVERFLOW;
        }

        A = result & 0xFF;
        setZeroAndNegativeFlags(A);
    }

    private void sbc(int value) {
        adc(value ^ 0xFF);
    }

    private void bit(int value) {
        // Zero flag: set if (A & value) == 0
        if ((A & value) == 0) {
            status |= FLAG_ZERO;
        } else {
            status &= ~FLAG_ZERO;
        }

        // Overflow flag: copy bit 6 of value
        if ((value & 0x40) != 0) {
            status |= FLAG_OVERFLOW;
        } else {
            status &= ~FLAG_OVERFLOW;
        }

        // Negative flag: copy bit 7 of value
        if ((value & 0x80) != 0) {
            status |= FLAG_NEGATIVE;
        } else {
            status &= ~FLAG_NEGATIVE;
        }
    }

    private void lsr(int addr, boolean zeroPage) {
        int value = memory.read(addr);
        setCarry((value & 0x01) != 0);
        value = (value >> 1) & 0x7F;
        memory.write(addr, value);
        setZeroAndNegativeFlags(value);
    }

    private void asl(int addr) {
        int value = memory.read(addr);
        setCarry((value & 0x80) != 0);
        value = (value << 1) & 0xFF;
        memory.write(addr, value);
        setZeroAndNegativeFlags(value);
    }

    private void rol_accumulator() {
        boolean oldCarry = getCarry();
        setCarry((A & 0x80) != 0);
        A = ((A << 1) | (oldCarry ? 1 : 0)) & 0xFF;
        setZeroAndNegativeFlags(A);
    }

    private void rol(int addr) {
        int value = memory.read(addr);
        boolean oldCarry = getCarry();
        setCarry((value & 0x80) != 0);
        value = ((value << 1) | (oldCarry ? 1 : 0)) & 0xFF;
        memory.write(addr, value);
        setZeroAndNegativeFlags(value);
    }

    private void ror_accumulator() {
        boolean oldCarry = getCarry();
        setCarry((A & 0x01) != 0);
        A = ((A >> 1) | (oldCarry ? 0x80 : 0)) & 0xFF;
        setZeroAndNegativeFlags(A);
    }

    private void ror(int addr) {
        int value = memory.read(addr);
        boolean oldCarry = getCarry();
        setCarry((value & 0x01) != 0);
        value = ((value >> 1) | (oldCarry ? 0x80 : 0)) & 0xFF;
        memory.write(addr, value);
        setZeroAndNegativeFlags(value);
    }

    private void dec(int addr) {
        int value = (memory.read(addr) - 1) & 0xFF;
        memory.write(addr, value);
        setZeroAndNegativeFlags(value);
    }

    private void ora(int value) {
        A |= value;
        setZeroAndNegativeFlags(A);
    }

    private void and(int value) {
        A &= value;
        setZeroAndNegativeFlags(A);
    }

    private void eor(int value) {
        A ^= value;
        setZeroAndNegativeFlags(A);
    }

    // Modified versions that return values for chaining
    private int aslValue(int value) {
        setCarry((value & 0x80) != 0);
        return (value << 1) & 0xFF;
    }

    private int lsrValue(int value) {
        setCarry((value & 0x01) != 0);
        return (value >> 1) & 0x7F;
    }

    private int rolValue(int value) {
        boolean oldCarry = getCarry();
        setCarry((value & 0x80) != 0);
        return ((value << 1) | (oldCarry ? 1 : 0)) & 0xFF;
    }

    private int rorValue(int value) {
        boolean oldCarry = getCarry();
        setCarry((value & 0x01) != 0);
        return ((value >> 1) | (oldCarry ? 0x80 : 0)) & 0xFF;
    }

    private int incValue(int value) {
        return (value + 1) & 0xFF;
    }

    private int decValue(int value) {
        return (value - 1) & 0xFF;
    }

    // Generic RMW combo helper
    private void rmwCombo(int addr, java.util.function.UnaryOperator<Integer> modify,
                          java.util.function.Consumer<Integer> accumOp) {
        int value = memory.read(addr);
        value = modify.apply(value);
        memory.write(addr, value);
        accumOp.accept(value);
    }

    private void executeUnstableStore(int base, int index, int value) {
        int baseHigh = (base >> 8) & 0xFF;
        int effectiveAddr = (base + index) & 0xFFFF;
        int effectiveHigh = (effectiveAddr >> 8) & 0xFF;
        int lowByte = effectiveAddr & 0xFF;

        boolean pageCrossed = baseHigh != effectiveHigh;

        // The AND mask is always (baseHigh + 1), applied to both value AND address
        int andResult = value & (baseHigh + 1);

        int targetAddr;
        if (pageCrossed) {
            // HIGH BYTE OF ADDRESS IS ALSO CORRUPTED
            targetAddr = (andResult << 8) | lowByte;

            // Dummy write at base page (wrong high byte, correct low byte)
            int dummyAddr = (baseHigh << 8) | lowByte;
            memory.write(dummyAddr, andResult);
        } else {
            targetAddr = effectiveAddr;
        }

        memory.write(targetAddr, andResult);
    }

    public boolean isRunning() { return running; }
}

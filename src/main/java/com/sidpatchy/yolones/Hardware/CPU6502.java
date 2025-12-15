package com.sidpatchy.yolones.Hardware;

public class CPU6502 {
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

    private Memory memory;

    public CPU6502(Memory mem) {
        memory = mem;
    }

    public void step() {
        int opcode = memory.read(PC++);
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

    public void executeInstruction(int opcode) {
        // https://www.nesdev.org/wiki/Visual6502wiki/6502_all_256_Opcodes
        switch(opcode) {
            case 0x00: // BRK
                running = false;
                break;

            // Load / Store
            case 0xA9: // LDA immediate
                break;
            case 0xA5: // LDA zero page
                break;
            case 0xAD: // LDA absolute
                break;
            case 0x85: // STA zero page
                break;
            case 0x8D: // STA absolute
                break;
            case 0xA2: // LDX immediate
                break;
            case 0xA0: // LDY immediate
                break;

            // Transfers
            case 0xAA: // TAX (A -> X)
                break;
            case 0x8A: // TXA (X -> A)
                break;
            case 0xA8: // TAY (A -> Y)
                break;
            case 0x98: // TYA (Y -> A)
                break;

            // Stack Operations
            case 0x9A: // TXS (X -> SP, set up stack)
                break;
            case 0x48: // PHA (push A to stack)
                break;
            case 0x68: // PLA (pull A from stack)
                break;

            // Jumps / Calls
            case 0x4C: // JMP absolute
                break;
            case 0x20: // JSR (call subroutine)
                break;
            case 0x60: // RTS (return from subroutine)
                break;

            // Branches
            case 0xD0: // BNE (branch if not equal/zero flag clear)
                break;
            case 0xF0: // BEQ (branch if equal/zero flag set)
                break;
            case 0x10: // BPL (branch if plus/negative flag clear)
                break;
            case 0x30: // BMI (branch if minus/negative flag set)
                break;

            // Increment / Decrement
            case 0xE8: // INX (increment X)
                break;
            case 0xC8: // INY (increment Y)
                break;
            case 0xCA: // DEX (decrement X)
                break;
            case 0x88: // DEY (decrement Y)
                break;

            // Comparison
            case 0xC9: // CMP immediate (compare with A)
                break;

            // Flags
            case 0x18: // CLC (clear carry flag)
                break;
            case 0x38: // SEC (set carry flag)
                break;
            case 0xD8: // CLD (clear decimal flag) - NES ignores this?
                break;

            // Misc.
            case 0xEA: // NOP (no operation)
                break;

            default:
                System.out.printf("Unknown opcode: 0x%02X at PC: 0x%04X\n", opcode, PC - 1);
                running = false;  // Halt on unknown opcode
                break;
        }
    }

    public boolean isRunning() { return running; }
}

package com.sidpatchy.yolones.Hardware.Mappers;

public interface Mapper {
    int read(int address);
    void write(int address, int value);
    
    // For PPU
    int chrRead(int address);
    void chrWrite(int address, int value);
    
    default boolean hasIRQ() { return false; }
    default void clockIRQ() { }
    default int getMirroringMode() { return -1; } // -1 means use hardwired mirroring
}

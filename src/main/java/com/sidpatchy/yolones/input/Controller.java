package com.sidpatchy.yolones.input;

import java.awt.*;

/**
 * Abstraction for input controllers (keyboard, gamepad, etc.).
 * Implementations should provide their current NES button bitmask state
 * (bit0=A, bit1=B, bit2=SELECT, bit3=START, bit4=UP, bit5=DOWN, bit6=LEFT, bit7=RIGHT),
 * and optionally install/uninstall any required listeners on UI components.
 */
public interface Controller {
    /** Returns the current NES controller bitmask. */
    int getState();

    /** Optional per-frame polling hook for controllers that require it. */
    default void tick() {}

    /**
     * Install the controller on the provided components (e.g., add listeners).
     * Implementations that don't require listeners may no-op.
     */
    default void installOn(Component... components) {}

    /**
     * Uninstall the controller from the provided components (e.g., remove listeners).
     */
    default void uninstallFrom(Component... components) {}
}

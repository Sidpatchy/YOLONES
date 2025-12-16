package com.sidpatchy.yolones;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Basic, extensible keyboard controller for NES-style input.
 *
 * NES controller bit layout expected by the current Memory implementation:
 * bit0=A, bit1=B, bit2=SELECT, bit3=START, bit4=UP, bit5=DOWN, bit6=LEFT, bit7=RIGHT.
 */
public class KeyboardController implements KeyListener {
    public enum Button { A, B, SELECT, START, UP, DOWN, LEFT, RIGHT }

    private final Map<Integer, Button> keymap;
    private final Set<Button> pressed = EnumSet.noneOf(Button.class);

    public KeyboardController() {
        this(defaultKeymap());
    }

    public KeyboardController(Map<Integer, Button> keymap) {
        this.keymap = new HashMap<>(keymap);
        // Initialize controller state
        // no-op: starts with empty pressed set
    }

    /**
     * Default mapping:
     * - D-Pad: W/A/S/D -> UP/LEFT/DOWN/RIGHT
     * - A/B: X -> A, Z -> B
     * - Start/Select: ENTER -> START, RIGHT_SHIFT -> SELECT
     */
    public static Map<Integer, Button> defaultKeymap() {
        Map<Integer, Button> map = new HashMap<>();
        map.put(KeyEvent.VK_W, Button.UP);
        map.put(KeyEvent.VK_S, Button.DOWN);
        map.put(KeyEvent.VK_A, Button.LEFT);
        map.put(KeyEvent.VK_D, Button.RIGHT);

        map.put(KeyEvent.VK_X, Button.A);
        map.put(KeyEvent.VK_Z, Button.B);

        map.put(KeyEvent.VK_ENTER, Button.START);
        map.put(KeyEvent.VK_SHIFT, Button.SELECT); // Any Shift; Right Shift preferred
        return Collections.unmodifiableMap(map);
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }

    @Override
    public void keyPressed(KeyEvent e) {
        Button b = keymap.get(e.getKeyCode());
        if (b != null) {
            if (pressed.add(b)) {
                // state changed
            }
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        Button b = keymap.get(e.getKeyCode());
        if (b != null) {
            pressed.remove(b);
            e.consume();
        }
    }

    public int getStateMask() {
        int mask = 0;
        if (pressed.contains(Button.A))      mask |= 1 << 0;
        if (pressed.contains(Button.B))      mask |= 1 << 1;
        if (pressed.contains(Button.SELECT)) mask |= 1 << 2;
        if (pressed.contains(Button.START))  mask |= 1 << 3;
        if (pressed.contains(Button.UP))     mask |= 1 << 4;
        if (pressed.contains(Button.DOWN))   mask |= 1 << 5;
        if (pressed.contains(Button.LEFT))   mask |= 1 << 6;
        if (pressed.contains(Button.RIGHT))  mask |= 1 << 7;
        return mask;
    }
}

package com.sidpatchy.yolones.input;

import net.java.games.input.Component;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumSet;
import java.util.Set;

/**
 * Gamepad controller for NES-style input using JInput.
 * Uses Nintendo's ABXY layout by default.
 * Bitmask: bit0=A, bit1=B, bit2=SELECT, bit3=START, bit4=UP, bit5=DOWN, bit6=LEFT, bit7=RIGHT.
 */
public class GamepadController implements Controller {
    private static final Logger logger = LogManager.getLogger(GamepadController.class);

    private net.java.games.input.Controller gamepad;
    private final Set<KeyboardController.Button> pressed = EnumSet.noneOf(KeyboardController.Button.class);

    public GamepadController() {
        findGamepad();
    }

    private void findGamepad() {
        net.java.games.input.Controller[] controllers = ControllerEnvironment.getDefaultEnvironment().getControllers();
        for (net.java.games.input.Controller c : controllers) {
            if (c.getType() == net.java.games.input.Controller.Type.GAMEPAD ||
                c.getType() == net.java.games.input.Controller.Type.STICK) {
                gamepad = c;
                logger.info("Found gamepad: {}", c.getName());
                break;
            }
        }
    }

    public boolean isConnected() {
        return gamepad != null;
    }

    @Override
    public int getState() {
        int mask = 0;
        if (pressed.contains(KeyboardController.Button.A))      mask |= 1 << 0;
        if (pressed.contains(KeyboardController.Button.B))      mask |= 1 << 1;
        if (pressed.contains(KeyboardController.Button.SELECT)) mask |= 1 << 2;
        if (pressed.contains(KeyboardController.Button.START))  mask |= 1 << 3;
        if (pressed.contains(KeyboardController.Button.UP))     mask |= 1 << 4;
        if (pressed.contains(KeyboardController.Button.DOWN))   mask |= 1 << 5;
        if (pressed.contains(KeyboardController.Button.LEFT))   mask |= 1 << 6;
        if (pressed.contains(KeyboardController.Button.RIGHT))  mask |= 1 << 7;
        return mask;
    }

    @Override
    public void tick() {
        if (gamepad == null) return;

        if (!gamepad.poll()) {
            logger.warn("Gamepad disconnected: {}", gamepad.getName());
            gamepad = null;
            return;
        }

        EventQueue queue = gamepad.getEventQueue();
        Event event = new Event();
        while (queue.getNextEvent(event)) {
            Component component = event.getComponent();
            Component.Identifier id = component.getIdentifier();
            float value = event.getValue();
            boolean isPressed = value > 0.5f;

            if (id instanceof Component.Identifier.Button) {
                handleButton(id, isPressed);
            } else if (id == Component.Identifier.Axis.POV) {
                handlePOV(value);
            } else if (id instanceof Component.Identifier.Axis) {
                handleAxis(id, value);
            }
        }
    }

    void handleButton(Component.Identifier id, boolean isPressed) {
        // Requested mapping:
        // Nintendo Y (Left) -> NES B
        // Nintendo X (Top) -> NES B
        // Nintendo B (Bottom) -> NES A
        // Nintendo A (Right) -> NES A

        // JInput common indices for controllers:
        // _0: Bottom (Nintendo B)
        // _1: Right  (Nintendo A)
        // _2: Left   (Nintendo Y)
        // _3: Top    (Nintendo X)

        if (id == Component.Identifier.Button._0 || id == Component.Identifier.Button._1 ||
            id == Component.Identifier.Button.A || id == Component.Identifier.Button.B) {
             updateButton(KeyboardController.Button.A, isPressed);
        } else if (id == Component.Identifier.Button._2 || id == Component.Identifier.Button._3 ||
                   id == Component.Identifier.Button.X || id == Component.Identifier.Button.Y) {
             updateButton(KeyboardController.Button.B, isPressed);
        } else if (id == Component.Identifier.Button._8 || id == Component.Identifier.Button.SELECT || id == Component.Identifier.Button._6) {
             updateButton(KeyboardController.Button.SELECT, isPressed);
        } else if (id == Component.Identifier.Button._9 || id == Component.Identifier.Button.START || id == Component.Identifier.Button._7) {
             updateButton(KeyboardController.Button.START, isPressed);
        }
    }

    void handlePOV(float value) {
        pressed.remove(KeyboardController.Button.UP);
        pressed.remove(KeyboardController.Button.DOWN);
        pressed.remove(KeyboardController.Button.LEFT);
        pressed.remove(KeyboardController.Button.RIGHT);

        if (value == Component.POV.UP || value == Component.POV.UP_LEFT || value == Component.POV.UP_RIGHT) {
            pressed.add(KeyboardController.Button.UP);
        }
        if (value == Component.POV.DOWN || value == Component.POV.DOWN_LEFT || value == Component.POV.DOWN_RIGHT) {
            pressed.add(KeyboardController.Button.DOWN);
        }
        if (value == Component.POV.LEFT || value == Component.POV.UP_LEFT || value == Component.POV.DOWN_LEFT) {
            pressed.add(KeyboardController.Button.LEFT);
        }
        if (value == Component.POV.RIGHT || value == Component.POV.UP_RIGHT || value == Component.POV.DOWN_RIGHT) {
            pressed.add(KeyboardController.Button.RIGHT);
        }
    }

    void handleAxis(Component.Identifier id, float value) {
        float threshold = 0.5f;
        if (id == Component.Identifier.Axis.X) {
            if (value < -threshold) {
                pressed.add(KeyboardController.Button.LEFT);
                pressed.remove(KeyboardController.Button.RIGHT);
            } else if (value > threshold) {
                pressed.add(KeyboardController.Button.RIGHT);
                pressed.remove(KeyboardController.Button.LEFT);
            } else {
                pressed.remove(KeyboardController.Button.LEFT);
                pressed.remove(KeyboardController.Button.RIGHT);
            }
        } else if (id == Component.Identifier.Axis.Y) {
            if (value < -threshold) {
                pressed.add(KeyboardController.Button.UP);
                pressed.remove(KeyboardController.Button.DOWN);
            } else if (value > threshold) {
                pressed.add(KeyboardController.Button.DOWN);
                pressed.remove(KeyboardController.Button.UP);
            } else {
                pressed.remove(KeyboardController.Button.UP);
                pressed.remove(KeyboardController.Button.DOWN);
            }
        }
    }

    void updateButton(KeyboardController.Button button, boolean isPressed) {
        if (isPressed) {
            pressed.add(button);
        } else {
            pressed.remove(button);
        }
    }
}

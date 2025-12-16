package com.sidpatchy.yolones.input;

import com.sidpatchy.yolones.Hardware.Memory;

import java.awt.*;

/**
 * Manages the active controller and synchronizes its state to NES memory.
 */
public class ControllerHandler {
    private final Memory memory;
    private Controller controller;
    private Component[] installedOn = new Component[0];

    public ControllerHandler(Memory memory) {
        this.memory = memory;
    }

    /**
     * Set and install a new active controller. Previous controller is uninstalled.
     */
    public void setController(Controller controller, Component... installOn) {
        if (this.controller != null && installedOn != null) {
            this.controller.uninstallFrom(installedOn);
        }
        this.controller = controller;
        this.installedOn = installOn != null ? installOn : new Component[0];
        if (this.controller != null && installedOn != null) {
            this.controller.installOn(installedOn);
        }
    }

    /**
     * Poll controller and write its state to Memory.
     */
    public void update() {
        if (controller == null) return;
        controller.tick();
        memory.setController(controller.getState());
    }
}

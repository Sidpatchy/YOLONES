package com.sidpatchy.yolones.Hardware;

public class APU {
    // Length counter table
    private static final int[] LENGTH_TABLE = {
        10, 254, 20, 2, 40, 4, 80, 6, 160, 8, 60, 10, 14, 12, 26, 14,
        12, 16, 24, 18, 48, 20, 96, 22, 192, 24, 72, 26, 16, 28, 32, 30
    };

    private static final int[][] DUTY_TABLE = {
        {0, 1, 0, 0, 0, 0, 0, 0},
        {0, 1, 1, 0, 0, 0, 0, 0},
        {0, 1, 1, 1, 1, 0, 0, 0},
        {1, 0, 0, 1, 1, 1, 1, 1}
    };

    private static final int[] TRIANGLE_TABLE = {
        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0,
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };

    private static final int[] NOISE_PERIOD_TABLE = {
        4, 8, 16, 32, 64, 96, 128, 160, 202, 254, 380, 508, 762, 1016, 2034, 4068
    };

    // Pulse 1
    private boolean p1Enabled = false;
    private int p1Duty = 0;
    private int p1DutyPos = 0;
    private int p1Timer = 0;
    private int p1TimerReload = 0;
    private int p1TimerDivider = 0;
    private int p1LengthCounter = 0;
    private boolean p1LengthCounterHalt = false;
    private boolean p1ConstantVolume = false;
    private int p1Volume = 0;

    // Pulse 1 Sweep
    private boolean p1SweepEnabled = false;
    private int p1SweepPeriod = 0;
    private boolean p1SweepNegate = false;
    private int p1SweepShift = 0;
    private boolean p1SweepReload = false;
    private int p1SweepDivider = 0;

    // Pulse 1 Envelope
    private boolean p1EnvStart = false;
    private int p1EnvDivider = 0;
    private int p1EnvDecay = 0;

    // Pulse 2
    private boolean p2Enabled = false;
    private int p2Duty = 0;
    private int p2DutyPos = 0;
    private int p2Timer = 0;
    private int p2TimerReload = 0;
    private int p2TimerDivider = 0;
    private int p2LengthCounter = 0;
    private boolean p2LengthCounterHalt = false;
    private boolean p2ConstantVolume = false;
    private int p2Volume = 0;

    // Pulse 2 Sweep
    private boolean p2SweepEnabled = false;
    private int p2SweepPeriod = 0;
    private boolean p2SweepNegate = false;
    private int p2SweepShift = 0;
    private boolean p2SweepReload = false;
    private int p2SweepDivider = 0;

    // Pulse 2 Envelope
    private boolean p2EnvStart = false;
    private int p2EnvDivider = 0;
    private int p2EnvDecay = 0;

    // Triangle
    private boolean triEnabled = false;
    private int triTimer = 0;
    private int triTimerReload = 0;
    private int triStep = 0;
    private int triLengthCounter = 0;
    private boolean triLengthCounterHalt = false;
    private int triLinearCounter = 0;
    private int triLinearCounterReload = 0;
    private boolean triLinearCounterControl = false;
    private boolean triLinearCounterReloadFlag = false;

    // Noise
    private boolean noiseEnabled = false;
    private int noiseTimer = 0;
    private int noiseTimerReload = 0;
    private int noiseShiftRegister = 1;
    private boolean noiseMode = false;
    private int noiseLengthCounter = 0;
    private boolean noiseLengthCounterHalt = false;
    private boolean noiseConstantVolume = false;
    private int noiseVolume = 0;

    // Noise Envelope
    private boolean noiseEnvStart = false;
    private int noiseEnvDivider = 0;
    private int noiseEnvDecay = 0;

    // Frame Counter
    private int frameCounterMode = 4;
    private int frameCounterCycle = 0;
    private boolean irqInhibit = false;
    private boolean frameIRQ = false;

    public APU() {
    }

    public void writeRegister(int address, int value) {
        switch (address) {
            case 0x4000:
                p1Duty = (value >> 6) & 0x03;
                p1LengthCounterHalt = (value & 0x20) != 0;
                p1ConstantVolume = (value & 0x10) != 0;
                p1Volume = value & 0x0F;
                break;
            case 0x4001:
                p1SweepEnabled = (value & 0x80) != 0;
                p1SweepPeriod = (value >> 4) & 0x07;
                p1SweepNegate = (value & 0x08) != 0;
                p1SweepShift = value & 0x07;
                p1SweepReload = true;
                break;
            case 0x4002:
                p1TimerReload = (p1TimerReload & 0x0700) | (value & 0xFF);
                break;
            case 0x4003:
                p1TimerReload = (p1TimerReload & 0x00FF) | ((value & 0x07) << 8);
                if (p1Enabled) {
                    p1LengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F];
                }
                p1DutyPos = 0; // Reset duty cycle
                p1EnvStart = true;
                break;
            case 0x4004:
                p2Duty = (value >> 6) & 0x03;
                p2LengthCounterHalt = (value & 0x20) != 0;
                p2ConstantVolume = (value & 0x10) != 0;
                p2Volume = value & 0x0F;
                break;
            case 0x4005:
                p2SweepEnabled = (value & 0x80) != 0;
                p2SweepPeriod = (value >> 4) & 0x07;
                p2SweepNegate = (value & 0x08) != 0;
                p2SweepShift = value & 0x07;
                p2SweepReload = true;
                break;
            case 0x4006:
                p2TimerReload = (p2TimerReload & 0x0700) | (value & 0xFF);
                break;
            case 0x4007:
                p2TimerReload = (p2TimerReload & 0x00FF) | ((value & 0x07) << 8);
                if (p2Enabled) {
                    p2LengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F];
                }
                p2DutyPos = 0; // Reset duty cycle
                p2EnvStart = true;
                break;
            case 0x4008:
                triLinearCounterControl = (value & 0x80) != 0;
                triLengthCounterHalt = triLinearCounterControl;
                triLinearCounterReload = value & 0x7F;
                break;
            case 0x400A:
                triTimerReload = (triTimerReload & 0x0700) | (value & 0xFF);
                break;
            case 0x400B:
                triTimerReload = (triTimerReload & 0x00FF) | ((value & 0x07) << 8);
                if (triEnabled) {
                    triLengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F];
                }
                triLinearCounterReloadFlag = true;
                break;
            case 0x400C:
                noiseLengthCounterHalt = (value & 0x20) != 0;
                noiseConstantVolume = (value & 0x10) != 0;
                noiseVolume = value & 0x0F;
                break;
            case 0x400E:
                noiseMode = (value & 0x80) != 0;
                noiseTimerReload = NOISE_PERIOD_TABLE[value & 0x0F];
                break;
            case 0x400F:
                if (noiseEnabled) {
                    noiseLengthCounter = LENGTH_TABLE[(value >> 3) & 0x1F];
                }
                noiseEnvStart = true;
                break;
            case 0x4015:
                p1Enabled = (value & 0x01) != 0;
                if (!p1Enabled) p1LengthCounter = 0;
                p2Enabled = (value & 0x02) != 0;
                if (!p2Enabled) p2LengthCounter = 0;
                triEnabled = (value & 0x04) != 0;
                if (!triEnabled) triLengthCounter = 0;
                noiseEnabled = (value & 0x08) != 0;
                if (!noiseEnabled) noiseLengthCounter = 0;
                break;
            case 0x4017:
                frameCounterMode = (value & 0x80) != 0 ? 5 : 4;
                irqInhibit = (value & 0x40) != 0;
                if (irqInhibit) {
                    frameIRQ = false;
                }
                frameCounterCycle = 0;
                break;
        }
    }

    public int readRegister(int address) {
        if (address == 0x4015) {
            int status = 0;
            if (p1LengthCounter > 0) status |= 0x01;
            if (p2LengthCounter > 0) status |= 0x02;
            if (triLengthCounter > 0) status |= 0x04;
            if (noiseLengthCounter > 0) status |= 0x08;
            if (frameIRQ) status |= 0x40;
            frameIRQ = false; // Reading 0x4015 clears frame IRQ flag
            return status;
        }
        return 0;
    }

    public void tick() {
        // Pulse 1 timer clocks every 2 CPU cycles
        p1TimerDivider = (p1TimerDivider + 1) % 2;
        if (p1TimerDivider == 0) {
            if (p1Timer == 0) {
                p1Timer = p1TimerReload;
                p1DutyPos = (p1DutyPos + 1) % 8;
            } else {
                p1Timer--;
            }
        }

        // Pulse 2 timer clocks every 2 CPU cycles
        p2Divider();

        // Triangle timer clocks every CPU cycle
        if (triTimer == 0) {
            triTimer = triTimerReload;
            if (triLengthCounter > 0 && triLinearCounter > 0) {
                triStep = (triStep + 1) % 32;
            }
        } else {
            triTimer--;
        }

        // Noise timer clocks every CPU cycle
        if (noiseTimer == 0) {
            noiseTimer = noiseTimerReload;
            int bit0 = noiseShiftRegister & 0x01;
            int bit1 = (noiseShiftRegister >> (noiseMode ? 6 : 1)) & 0x01;
            int feedback = bit0 ^ bit1;
            noiseShiftRegister = (noiseShiftRegister >> 1) | (feedback << 14);
        } else {
            noiseTimer--;
        }

        // Frame Counter
        frameCounterCycle++;
        if (frameCounterMode == 4) {
            // 4-step sequence
            switch (frameCounterCycle) {
                case 3728: // Step 1
                    clockEnvelopes();
                    clockLinearCounter();
                    break;
                case 7456: // Step 2
                    clockEnvelopes();
                    clockLinearCounter();
                    clockLengthCounters();
                    break;
                case 11185: // Step 3
                    clockEnvelopes();
                    clockLinearCounter();
                    break;
                case 14914: // Step 4
                    clockEnvelopes();
                    clockLinearCounter();
                    clockLengthCounters();
                    if (!irqInhibit) {
                        frameIRQ = true;
                    }
                    frameCounterCycle = 0;
                    break;
            }
        } else {
            // 5-step sequence
            switch (frameCounterCycle) {
                case 3728: // Step 1
                    clockEnvelopes();
                    clockLinearCounter();
                    break;
                case 7456: // Step 2
                    clockEnvelopes();
                    clockLinearCounter();
                    clockLengthCounters();
                    break;
                case 11185: // Step 3
                    clockEnvelopes();
                    clockLinearCounter();
                    break;
                case 14914: // Step 4
                    // Nothing
                    break;
                case 18640: // Step 5
                    clockEnvelopes();
                    clockLinearCounter();
                    clockLengthCounters();
                    frameCounterCycle = 0;
                    break;
            }
        }
    }

    private void p2Divider() {
        p2TimerDivider = (p2TimerDivider + 1) % 2;
        if (p2TimerDivider == 0) {
            if (p2Timer == 0) {
                p2Timer = p2TimerReload;
                p2DutyPos = (p2DutyPos + 1) % 8;
            } else {
                p2Timer--;
            }
        }
    }

    private void clockLinearCounter() {
        if (triLinearCounterReloadFlag) {
            triLinearCounter = triLinearCounterReload;
        } else if (triLinearCounter > 0) {
            triLinearCounter--;
        }
        if (!triLinearCounterControl) {
            triLinearCounterReloadFlag = false;
        }
    }

    private void clockEnvelopes() {
        // Pulse 1 Envelope
        p1Env();

        // Pulse 2 Envelope
        p2Env();

        // Noise Envelope
        if (!noiseEnvStart) {
            if (noiseEnvDivider == 0) {
                noiseEnvDivider = noiseVolume;
                if (noiseEnvDecay > 0) {
                    noiseEnvDecay--;
                } else if (noiseLengthCounterHalt) {
                    noiseEnvDecay = 15;
                }
            } else {
                noiseEnvDivider--;
            }
        } else {
            noiseEnvStart = false;
            noiseEnvDecay = 15;
            noiseEnvDivider = noiseVolume;
        }
    }

    private void p1Env() {
        if (!p1EnvStart) {
            if (p1EnvDivider == 0) {
                p1EnvDivider = p1Volume;
                if (p1EnvDecay > 0) {
                    p1EnvDecay--;
                } else if (p1LengthCounterHalt) {
                    p1EnvDecay = 15;
                }
            } else {
                p1EnvDivider--;
            }
        } else {
            p1EnvStart = false;
            p1EnvDecay = 15;
            p1EnvDivider = p1Volume;
        }
    }

    private void p2Env() {
        if (!p2EnvStart) {
            if (p2EnvDivider == 0) {
                p2EnvDivider = p2Volume;
                if (p2EnvDecay > 0) {
                    p2EnvDecay--;
                } else if (p2LengthCounterHalt) {
                    p2EnvDecay = 15;
                }
            } else {
                p2EnvDivider--;
            }
        } else {
            p2EnvStart = false;
            p2EnvDecay = 15;
            p2EnvDivider = p2Volume;
        }
    }

    private void clockLengthCounters() {
        if (p1LengthCounter > 0 && !p1LengthCounterHalt) {
            p1LengthCounter--;
        }
        if (p2LengthCounter > 0 && !p2LengthCounterHalt) {
            p2LengthCounter--;
        }
        if (triLengthCounter > 0 && !triLengthCounterHalt) {
            triLengthCounter--;
        }
        if (noiseLengthCounter > 0 && !noiseLengthCounterHalt) {
            noiseLengthCounter--;
        }

        // Pulse 1 Sweep
        if (p1SweepDivider == 0 && p1SweepEnabled && p1SweepShift > 0 && !p1SweepMute(p1TimerReload)) {
            int change = p1TimerReload >> p1SweepShift;
            if (p1SweepNegate) {
                p1TimerReload -= change + 1; // Pulse 1 uses ones' complement
            } else {
                p1TimerReload += change;
            }
        }
        if (p1SweepDivider == 0 || p1SweepReload) {
            p1SweepDivider = p1SweepPeriod;
            p1SweepReload = false;
        } else {
            p1SweepDivider--;
        }

        // Pulse 2 Sweep
        if (p2SweepDivider == 0 && p2SweepEnabled && p2SweepShift > 0 && !p2SweepMute(p2TimerReload)) {
            int change = p2TimerReload >> p2SweepShift;
            if (p2SweepNegate) {
                p2TimerReload -= change; // Pulse 2 uses twos' complement
            } else {
                p2TimerReload += change;
            }
        }
        if (p2SweepDivider == 0 || p2SweepReload) {
            p2SweepDivider = p2SweepPeriod;
            p2SweepReload = false;
        } else {
            p2SweepDivider--;
        }
    }

    private boolean p1SweepMute(int timer) {
        return timer < 8 || timer > 0x7FF;
    }

    private boolean p2SweepMute(int timer) {
        return timer < 8 || timer > 0x7FF;
    }

    public float getSample() {
        // Output from Pulse 1
        int p1Out = 0;
        int p1Vol = p1ConstantVolume ? p1Volume : p1EnvDecay;
        if (p1Enabled && p1LengthCounter > 0 && !p1SweepMute(p1TimerReload)) {
            p1Out = DUTY_TABLE[p1Duty][p1DutyPos] * p1Vol;
        }

        // Output from Pulse 2
        int p2Out = 0;
        int p2Vol = p2ConstantVolume ? p2Volume : p2EnvDecay;
        if (p2Enabled && p2LengthCounter > 0 && !p2SweepMute(p2TimerReload)) {
            p2Out = DUTY_TABLE[p2Duty][p2DutyPos] * p2Vol;
        }

        // Output from Triangle
        int triOut = 0;
        if (triEnabled && triLengthCounter > 0 && triLinearCounter > 0 && triTimerReload > 2) {
            triOut = TRIANGLE_TABLE[triStep];
        }

        // Output from Noise
        int noiseOut = 0;
        int nVol = noiseConstantVolume ? noiseVolume : noiseEnvDecay;
        if (noiseEnabled && noiseLengthCounter > 0 && (noiseShiftRegister & 0x01) == 0) {
            noiseOut = nVol;
        }

        // Simple mixer (approximation)
        float pulseSum = p1Out + p2Out;
        float pulseOut = 0;
        if (pulseSum != 0) {
            pulseOut = 95.88f / (8128.0f / pulseSum + 100.0f);
        }

        float tndSum = triOut / 8227.0f + noiseOut / 12241.0f; // DMC is 0
        float tndOut = 0;
        if (tndSum != 0) {
            tndOut = 159.79f / (1.0f / tndSum + 100.0f);
        }

        return pulseOut + tndOut;
    }

    public boolean hasIRQ() {
        return frameIRQ;
    }
}

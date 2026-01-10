/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.gesture

import android.util.Log
import kotlin.math.abs

/**
 * Manages gesture state transitions and emits control events.
 * 
 * Handles:
 * - Debouncing to prevent rapid on/off toggles
 * - Palm rotation tracking for brightness control
 * - State machine for gesture transitions
 */
class GestureStateManager {
    companion object {
        private const val TAG = "GestureStateManager"
    }

    // Current known state
    private var currentGesture: HandGesture = HandGesture.NONE
    private var lastToggleTime: Long = 0L
    private var lastBrightnessTime: Long = 0L
    private var baseRotationAngle: Float = 0f
    private var isLightOn: Boolean = false
    private var currentBrightness: Int = 50  // Track current brightness (1-100)

    // Track if we've already processed the current gesture to avoid repeated triggers
    private var lastProcessedGesture: HandGesture = HandGesture.NONE

    /**
     * Process a hand state and return any gesture event that should be triggered.
     * 
     * @param handState The current hand state from MediaPipe
     * @return GestureEvent indicating what action to take
     */
    fun processHandState(handState: HandState): GestureEvent {
        val currentTime = System.currentTimeMillis()

        // No hand detected - reset state
        if (!handState.isHandDetected) {
            if (currentGesture != HandGesture.NONE) {
                Log.d(TAG, "Hand lost, resetting state")
                currentGesture = HandGesture.NONE
                lastProcessedGesture = HandGesture.NONE
            }
            return GestureEvent.NoAction
        }

        val newGesture = handState.gesture

        // Handle gesture transitions
        return when {
            // Transition to OPEN_PALM (Light ON)
            newGesture == HandGesture.OPEN_PALM && 
            lastProcessedGesture != HandGesture.OPEN_PALM &&
            canToggle(currentTime) -> {
                Log.d(TAG, "Open palm detected - turning light ON")
                currentGesture = HandGesture.OPEN_PALM
                lastProcessedGesture = HandGesture.OPEN_PALM
                lastToggleTime = currentTime
                baseRotationAngle = handState.rotationAngle  // Set base angle for rotation tracking
                isLightOn = true
                GestureEvent.LightOn
            }

            // Transition to CLOSED_FIST (Light OFF) - IMMEDIATE, no debounce for instant response
            newGesture == HandGesture.CLOSED_FIST && 
            lastProcessedGesture != HandGesture.CLOSED_FIST -> {
                Log.d(TAG, "Closed fist detected - turning light OFF immediately")
                currentGesture = HandGesture.CLOSED_FIST
                lastProcessedGesture = HandGesture.CLOSED_FIST
                lastToggleTime = currentTime  // Still update to debounce next palm-on
                isLightOn = false
                GestureEvent.LightOff
            }

            // While palm is open, track rotation for brightness
            newGesture == HandGesture.OPEN_PALM && isLightOn -> {
                currentGesture = HandGesture.OPEN_PALM
                checkRotationForBrightness(handState.rotationAngle, currentTime)
            }

            // Gesture unchanged or transitioning through NONE
            else -> {
                currentGesture = newGesture
                GestureEvent.NoAction
            }
        }
    }

    /**
     * Check palm rotation and emit brightness change events.
     */
    private fun checkRotationForBrightness(currentAngle: Float, currentTime: Long): GestureEvent {
        if (!canChangeBrightness(currentTime)) {
            return GestureEvent.NoAction
        }

        val rotationDelta = currentAngle - baseRotationAngle

        return when {
            // Rotated right enough - increase brightness
            rotationDelta > GestureConfig.ROTATION_THRESHOLD_DEGREES -> {
                val newBrightness = (currentBrightness + GestureConfig.BRIGHTNESS_STEP).coerceIn(1, 100)
                if (newBrightness != currentBrightness) {
                    Log.d(TAG, "Rotation right detected (${rotationDelta}°) - brightness UP to $newBrightness")
                    baseRotationAngle = currentAngle  // Reset base for next increment
                    lastBrightnessTime = currentTime
                    val delta = newBrightness - currentBrightness
                    currentBrightness = newBrightness
                    GestureEvent.BrightnessChange(delta)
                } else {
                    GestureEvent.NoAction
                }
            }

            // Rotated left enough - decrease brightness
            rotationDelta < -GestureConfig.ROTATION_THRESHOLD_DEGREES -> {
                val newBrightness = (currentBrightness - GestureConfig.BRIGHTNESS_STEP).coerceIn(1, 100)
                if (newBrightness != currentBrightness) {
                    Log.d(TAG, "Rotation left detected (${rotationDelta}°) - brightness DOWN to $newBrightness")
                    baseRotationAngle = currentAngle  // Reset base for next decrement
                    lastBrightnessTime = currentTime
                    val delta = newBrightness - currentBrightness
                    currentBrightness = newBrightness
                    GestureEvent.BrightnessChange(delta)
                } else {
                    GestureEvent.NoAction
                }
            }

            else -> GestureEvent.NoAction
        }
    }

    /**
     * Check if enough time has passed since last toggle.
     */
    private fun canToggle(currentTime: Long): Boolean {
        return currentTime - lastToggleTime > GestureConfig.GESTURE_DEBOUNCE_MS
    }

    /**
     * Check if enough time has passed since last brightness change.
     */
    private fun canChangeBrightness(currentTime: Long): Boolean {
        return currentTime - lastBrightnessTime > GestureConfig.BRIGHTNESS_DEBOUNCE_MS
    }

    /**
     * Get current tracked brightness level.
     */
    fun getCurrentBrightness(): Int = currentBrightness

    /**
     * Set brightness level (e.g., when syncing with actual device state).
     */
    fun setBrightness(brightness: Int) {
        currentBrightness = brightness.coerceIn(1, 100)
    }

    /**
     * Get whether light is currently on.
     */
    fun isLightOn(): Boolean = isLightOn

    /**
     * Reset state (e.g., when stream stops).
     */
    fun reset() {
        currentGesture = HandGesture.NONE
        lastProcessedGesture = HandGesture.NONE
        lastToggleTime = 0L
        lastBrightnessTime = 0L
        baseRotationAngle = 0f
        Log.d(TAG, "State manager reset")
    }
}


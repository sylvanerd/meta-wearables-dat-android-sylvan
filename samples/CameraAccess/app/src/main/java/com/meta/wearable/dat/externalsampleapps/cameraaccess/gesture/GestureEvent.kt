/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.gesture

/**
 * Represents the different hand gestures that can be detected.
 */
enum class HandGesture {
    NONE,           // No hand detected or unrecognized gesture
    OPEN_PALM,      // All fingers extended - turns light ON
    CLOSED_FIST,    // All fingers closed - turns light OFF
}

/**
 * Represents the current state of hand tracking.
 */
data class HandState(
    val gesture: HandGesture = HandGesture.NONE,
    val rotationAngle: Float = 0f,  // Palm rotation angle in degrees (-90 to 90)
    val confidence: Float = 0f,      // Detection confidence (0 to 1)
    val isHandDetected: Boolean = false,
)

/**
 * Sealed class representing gesture events that trigger actions.
 */
sealed class GestureEvent {
    /**
     * Light should be turned ON (open palm detected after fist or no hand).
     */
    data object LightOn : GestureEvent()

    /**
     * Light should be turned OFF (closed fist detected after open palm).
     */
    data object LightOff : GestureEvent()

    /**
     * Brightness should be adjusted.
     * @param delta The brightness change (-10 to +10 typically)
     */
    data class BrightnessChange(val delta: Int) : GestureEvent()

    /**
     * No action needed (gesture unchanged or no hand detected).
     */
    data object NoAction : GestureEvent()
}

/**
 * Configuration for gesture detection thresholds.
 */
object GestureConfig {
    // Distance thresholds for palm open/closed detection
    const val PALM_OPEN_THRESHOLD = 0.15f      // Fingertip-to-palm distance for open palm
    const val FIST_CLOSED_THRESHOLD = 0.08f    // Fingertip-to-palm distance for closed fist

    // Rotation thresholds
    const val ROTATION_THRESHOLD_DEGREES = 5f  // Degrees of rotation to trigger brightness change
    const val BRIGHTNESS_STEP = 20              // Brightness change per rotation threshold

    // Debounce timings
    const val GESTURE_DEBOUNCE_MS = 200L        // Cooldown between on/off toggles
    const val BRIGHTNESS_DEBOUNCE_MS = 150L     // Cooldown between brightness changes

    // Frame processing
    const val FRAME_SKIP_COUNT = 3              // Process every Nth frame (0 = process all)
    
    // Gesture processing resolution (lower = faster MediaPipe processing)
    const val GESTURE_FRAME_WIDTH = 320         // Low-res width for MediaPipe
    const val GESTURE_FRAME_HEIGHT = 240        // Low-res height for MediaPipe
}


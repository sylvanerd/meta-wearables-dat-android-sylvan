/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess.gesture

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Processes video frames to detect hand landmarks using MediaPipe.
 * 
 * Detects:
 * - Open palm vs closed fist (for light on/off)
 * - Palm rotation angle (for brightness control)
 */
class HandGestureProcessor(context: Context) {
    companion object {
        private const val TAG = "HandGestureProcessor"
        private const val MODEL_ASSET_PATH = "hand_landmarker.task"

        // Hand landmark indices
        private const val WRIST = 0
        private const val THUMB_TIP = 4
        private const val INDEX_FINGER_TIP = 8
        private const val MIDDLE_FINGER_TIP = 12
        private const val RING_FINGER_TIP = 16
        private const val PINKY_TIP = 20
        private const val MIDDLE_FINGER_MCP = 9  // Palm center reference
    }

    private var handLandmarker: HandLandmarker? = null
    private var isInitialized = false

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET_PATH)
                .setDelegate(Delegate.GPU)  // Use GPU for better performance on Pixel 10 Pro
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE)
                .setNumHands(1)  // Only track one hand for gesture control
                .setMinHandDetectionConfidence(0.3f)
                .setMinHandPresenceConfidence(0.3f)
                .setMinTrackingConfidence(0.5f)
                .build()

            handLandmarker = HandLandmarker.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "HandLandmarker initialized successfully with GPU delegate")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize HandLandmarker with GPU, trying CPU", e)
            try {
                // Fallback to CPU if GPU fails
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET_PATH)
                    .setDelegate(Delegate.CPU)
                    .build()

                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(1)
                    .setMinHandDetectionConfidence(0.3f)
                    .setMinHandPresenceConfidence(0.3f)
                    .setMinTrackingConfidence(0.5f)
                    .build()

                handLandmarker = HandLandmarker.createFromOptions(context, options)
                isInitialized = true
                Log.d(TAG, "HandLandmarker initialized with CPU delegate")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to initialize HandLandmarker", e2)
                isInitialized = false
            }
        }
    }

    /**
     * Process a bitmap frame and return the detected hand state.
     * 
     * @param bitmap The video frame to process
     * @return HandState containing gesture type and rotation angle
     */
    fun processFrame(bitmap: Bitmap): HandState {
        if (!isInitialized || handLandmarker == null) {
            return HandState()
        }

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result = handLandmarker?.detect(mpImage)
            
            if (result == null || result.landmarks().isEmpty()) {
                return HandState(isHandDetected = false)
            }

            analyzeHandLandmarks(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame", e)
            HandState()
        }
    }

    /**
     * Analyze hand landmarks to determine gesture and rotation.
     */
    private fun analyzeHandLandmarks(result: HandLandmarkerResult): HandState {
        val landmarks = result.landmarks().firstOrNull() ?: return HandState()
        val handedness = result.handednesses().firstOrNull()?.firstOrNull()
        val confidence = handedness?.score() ?: 0f

        // Get key landmark positions (normalized 0-1 coordinates)
        val wrist = landmarks[WRIST]
        val thumbTip = landmarks[THUMB_TIP]
        val indexTip = landmarks[INDEX_FINGER_TIP]
        val middleTip = landmarks[MIDDLE_FINGER_TIP]
        val ringTip = landmarks[RING_FINGER_TIP]
        val pinkyTip = landmarks[PINKY_TIP]
        val palmCenter = landmarks[MIDDLE_FINGER_MCP]

        // Calculate average fingertip-to-palm distance
        val avgFingertipDistance = calculateAverageFingertipDistance(
            palmCenter,
            listOf(thumbTip, indexTip, middleTip, ringTip, pinkyTip)
        )

        // Determine gesture based on finger extension
        val gesture = when {
            avgFingertipDistance > GestureConfig.PALM_OPEN_THRESHOLD -> HandGesture.OPEN_PALM
            avgFingertipDistance < GestureConfig.FIST_CLOSED_THRESHOLD -> HandGesture.CLOSED_FIST
            else -> HandGesture.NONE
        }

        // Calculate palm rotation angle (for brightness control when palm is open)
        val rotationAngle = calculatePalmRotation(wrist, middleTip)

        Log.d(TAG, "Gesture: $gesture, Rotation: $rotationAngle°, Distance: $avgFingertipDistance")

        return HandState(
            gesture = gesture,
            rotationAngle = rotationAngle,
            confidence = confidence,
            isHandDetected = true
        )
    }

    /**
     * Calculate average distance from palm center to fingertips.
     * Used to distinguish open palm from closed fist.
     */
    private fun calculateAverageFingertipDistance(
        palmCenter: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        fingertips: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>
    ): Float {
        var totalDistance = 0f
        for (tip in fingertips) {
            val dx = tip.x() - palmCenter.x()
            val dy = tip.y() - palmCenter.y()
            totalDistance += sqrt(dx * dx + dy * dy)
        }
        return totalDistance / fingertips.size
    }

    /**
     * Calculate palm rotation angle based on wrist-to-middle-finger vector.
     * Returns angle in degrees: negative = rotated left, positive = rotated right.
     */
    private fun calculatePalmRotation(
        wrist: com.google.mediapipe.tasks.components.containers.NormalizedLandmark,
        middleTip: com.google.mediapipe.tasks.components.containers.NormalizedLandmark
    ): Float {
        val dx = middleTip.x() - wrist.x()
        val dy = middleTip.y() - wrist.y()
        
        // Calculate angle from vertical (0 = pointing up, negative = left, positive = right)
        val angleRadians = atan2(dx, -dy)  // Negative dy because y increases downward
        val angleDegrees = Math.toDegrees(angleRadians.toDouble()).toFloat()
        
        return angleDegrees.coerceIn(-90f, 90f)
    }

    /**
     * Release resources when no longer needed.
     */
    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        isInitialized = false
        Log.d(TAG, "HandLandmarker closed")
    }
}


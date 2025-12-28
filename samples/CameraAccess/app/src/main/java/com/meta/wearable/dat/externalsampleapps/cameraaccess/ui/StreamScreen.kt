/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// StreamScreen - DAT Camera Streaming UI
//
// This composable demonstrates the main streaming UI for DAT camera functionality. It shows how to
// display live video from wearable devices and handle photo capture.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meta.wearable.dat.camera.types.StreamSessionState
import com.meta.wearable.dat.externalsampleapps.cameraaccess.R
import com.meta.wearable.dat.externalsampleapps.cameraaccess.gesture.HandGesture
import com.meta.wearable.dat.externalsampleapps.cameraaccess.stream.StreamViewModel
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel

@Composable
fun StreamScreen(
    wearablesViewModel: WearablesViewModel,
    goveeApiKey: String = "",
    goveeDeviceId: String = "",
    goveeSku: String = "",
    modifier: Modifier = Modifier,
    streamViewModel: StreamViewModel =
        viewModel(
            factory =
                StreamViewModel.Factory(
                    application = (LocalActivity.current as ComponentActivity).application,
                    wearablesViewModel = wearablesViewModel,
                    goveeApiKey = goveeApiKey,
                    goveeDeviceId = goveeDeviceId,
                    goveeSku = goveeSku,
                ),
        ),
) {
  val streamUiState by streamViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(Unit) { streamViewModel.startStream() }

  Box(modifier = modifier.fillMaxSize()) {
    streamUiState.videoFrame?.let { videoFrame ->
      Image(
          bitmap = videoFrame.asImageBitmap(),
          contentDescription = stringResource(R.string.live_stream),
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
      )
    }
    if (streamUiState.streamSessionState == StreamSessionState.STARTING) {
      CircularProgressIndicator(
          modifier = Modifier.align(Alignment.Center),
      )
    }

    // Gesture status overlay (top-right)
    GestureStatusOverlay(
        isGestureEnabled = streamUiState.isGestureEnabled,
        isHandDetected = streamUiState.handState.isHandDetected,
        gesture = streamUiState.handState.gesture,
        isLightOn = streamUiState.isLightOn,
        brightness = streamUiState.currentBrightness,
        lastAction = streamUiState.lastGestureAction,
        onToggleGesture = { streamViewModel.toggleGestureRecognition() },
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(16.dp),
    )

    Box(modifier = Modifier.fillMaxSize().padding(all = 24.dp)) {
      Row(
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .fillMaxWidth()
                  .height(56.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
      ) {
        SwitchButton(
            label = stringResource(R.string.stop_stream_button_title),
            onClick = {
              streamViewModel.stopStream()
              wearablesViewModel.navigateToDeviceSelection()
            },
            isDestructive = true,
            modifier = Modifier.weight(1f),
        )

        // Timer button
        TimerButton(
            timerMode = streamUiState.timerMode,
            onClick = { streamViewModel.cycleTimerMode() },
        )
        // Photo capture button
        CaptureButton(
            onClick = { streamViewModel.capturePhoto() },
        )
      }
    }

    // Countdown timer display
    streamUiState.remainingTimeSeconds?.let { seconds ->
      val minutes = seconds / 60
      val remainingSeconds = seconds % 60
      Text(
          text = stringResource(id = R.string.time_remaining, minutes, remainingSeconds),
          color = Color.White,
          modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
          textAlign = TextAlign.Center,
      )
    }
  }

  streamUiState.capturedPhoto?.let { photo ->
    if (streamUiState.isShareDialogVisible) {
      SharePhotoDialog(
          photo = photo,
          onDismiss = { streamViewModel.hideShareDialog() },
          onShare = { bitmap ->
            streamViewModel.sharePhoto(bitmap)
            streamViewModel.hideShareDialog()
          },
      )
    }
  }
}

/**
 * Gesture status overlay showing hand detection state and light controls.
 */
@Composable
private fun GestureStatusOverlay(
    isGestureEnabled: Boolean,
    isHandDetected: Boolean,
    gesture: HandGesture,
    isLightOn: Boolean,
    brightness: Int,
    lastAction: String?,
    onToggleGesture: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Column(
      modifier = modifier
          .clip(RoundedCornerShape(12.dp))
          .background(Color.Black.copy(alpha = 0.7f))
          .padding(12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    // Toggle gesture recognition button
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      IconButton(
          onClick = onToggleGesture,
          modifier = Modifier.size(32.dp),
      ) {
        Icon(
            imageVector = if (isGestureEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            contentDescription = "Toggle gesture recognition",
            tint = if (isGestureEnabled) Color.Green else Color.Gray,
        )
      }
      Text(
          text = if (isGestureEnabled) "Gestures ON" else "Gestures OFF",
          color = Color.White,
          fontSize = 12.sp,
      )
    }

    if (isGestureEnabled) {
      Spacer(modifier = Modifier.height(8.dp))

      // Hand detection status
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
            imageVector = Icons.Default.PanTool,
            contentDescription = "Hand",
            tint = if (isHandDetected) Color.Green else Color.Gray,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = when {
              !isHandDetected -> "No hand"
              gesture == HandGesture.OPEN_PALM -> "Open Palm"
              gesture == HandGesture.CLOSED_FIST -> "Closed Fist"
              else -> "Detecting..."
            },
            color = Color.White,
            fontSize = 11.sp,
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Light status
      Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(
            imageVector = if (isLightOn) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
            contentDescription = "Light",
            tint = if (isLightOn) Color.Yellow else Color.Gray,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (isLightOn) "ON" else "OFF",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
      }

      // Brightness
      if (isLightOn) {
        Text(
            text = "Brightness: $brightness%",
            color = Color.White,
            fontSize = 10.sp,
        )
      }

      // Last action feedback
      lastAction?.let { action ->
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = action,
            color = Color.Cyan,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
      }
    }
  }
}

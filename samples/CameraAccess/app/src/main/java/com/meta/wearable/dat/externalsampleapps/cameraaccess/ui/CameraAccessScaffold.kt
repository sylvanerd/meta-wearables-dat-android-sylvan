/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraAccessScaffold - DAT Application Navigation Orchestrator
//
// This scaffold demonstrates a typical DAT application navigation pattern based on device
// registration and streaming states from the DAT API.
//
// DAT State-Based Navigation:
// - HomeScreen: When NOT registered (uiState.isRegistered = false) Shows initial registration UI
//   calling Wearables.startRegistration()
// - NonStreamScreen: When registered (uiState.isRegistered = true) but not streaming Shows device
//   selection, permission checking, and pre-streaming setup
// - StreamScreen: When actively streaming (uiState.isStreaming = true) Shows live video from
//   StreamSession.videoStream and photo capture UI
//
// The scaffold also provides a debug menu (in DEBUG builds) that gives access to
// MockDeviceKitScreen for testing DAT functionality without physical devices.

package com.meta.wearable.dat.externalsampleapps.cameraaccess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.BuildConfig
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraAccessScaffold(
    viewModel: WearablesViewModel,
    onRequestWearablesPermission: suspend (Permission) -> PermissionStatus,
    onTestLight: suspend () -> Unit,
    onSetBrightness: suspend (Int) -> Unit,
    goveeApiKey: String = "",
    goveeDeviceId: String = "",
    goveeSku: String = "",
    modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val coroutineScope = rememberCoroutineScope()
  
  // Brightness slider state (1-100, Govee doesn't accept 0)
  var brightnessValue by remember { mutableFloatStateOf(50f) }

  // Observe camera permission errors and show snackbar
  LaunchedEffect(uiState.recentError) {
    uiState.recentError?.let { errorMessage ->
      snackbarHostState.showSnackbar(errorMessage)
      viewModel.clearCameraPermissionError()
    }
  }

  Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(modifier = Modifier.fillMaxSize()) {
      when {
        uiState.isStreaming ->
            StreamScreen(
                wearablesViewModel = viewModel,
                goveeApiKey = goveeApiKey,
                goveeDeviceId = goveeDeviceId,
                goveeSku = goveeSku,
            )
        uiState.isRegistered ->
            NonStreamScreen(
                viewModel = viewModel,
                onRequestWearablesPermission = onRequestWearablesPermission,
            )
        else ->
            HomeScreen(
                viewModel = viewModel,
            )
      }

      SnackbarHost(
          hostState = snackbarHostState,
          modifier =
              Modifier.align(Alignment.BottomCenter)
                  .navigationBarsPadding()
                  .padding(horizontal = 16.dp, vertical = 32.dp),
          snackbar = { data ->
            Snackbar(
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Camera Access error",
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(data.visuals.message)
              }
            }
          },
      )

      // Govee Light Controls - Left side panel
      Column(
          modifier = Modifier
              .align(Alignment.CenterStart)
              .padding(start = 16.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
              .padding(12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Test Light Button
        FloatingActionButton(
            onClick = { coroutineScope.launch { onTestLight() } },
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ) {
          Icon(Icons.Default.Lightbulb, contentDescription = "Test Light")
        }
        Text(
            "Test Light",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Brightness Slider
        Icon(
            Icons.Default.LightMode,
            contentDescription = "Brightness",
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            "Brightness",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${brightnessValue.toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Vertical-style slider (rotated conceptually via height)
        Slider(
            value = brightnessValue,
            onValueChange = { brightnessValue = it },
            onValueChangeFinished = {
              coroutineScope.launch {
                onSetBrightness(brightnessValue.toInt())
              }
            },
            valueRange = 1f..100f,
            modifier = Modifier.width(120.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
      }

      if (BuildConfig.DEBUG) {
        FloatingActionButton(
            onClick = { viewModel.showDebugMenu() },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
          Icon(Icons.Default.BugReport, contentDescription = "Debug Menu")
        }

        if (uiState.isDebugMenuVisible) {
          ModalBottomSheet(
              onDismissRequest = { viewModel.hideDebugMenu() },
              sheetState = bottomSheetState,
              modifier = Modifier.fillMaxSize(),
          ) {
            MockDeviceKitScreen(modifier = Modifier.fillMaxSize())
          }
        }
      }
    }
  }
}

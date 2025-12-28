/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

// CameraAccess Sample App - Main Activity
//
// This is the main entry point for the CameraAccess sample application that demonstrates how to use
// the Meta Wearables Device Access Toolkit (DAT) to:
// - Initialize the DAT SDK
// - Handle device permissions (Bluetooth, Internet)
// - Request camera permissions from wearable devices (Ray-Ban Meta glasses)
// - Stream video and capture photos from connected wearable devices

package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.INTERNET
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.viewModels
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.PermissionStatus
import com.meta.wearable.dat.externalsampleapps.cameraaccess.ui.CameraAccessScaffold
import com.meta.wearable.dat.externalsampleapps.cameraaccess.wearables.WearablesViewModel
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ========== GOVEE LIGHT CONFIGURATION ==========
// Get your API key from: https://developer.govee.com/
// To find your device ID and SKU, call GoveeManager.getDevices(API_KEY) 
// and check the Logcat output for your device info.
//
// The new Govee OpenAPI uses different identifiers than the old API:
// - device: The device ID (e.g., "64:09:C5:32:37:36:2D:13")
// - sku: The product model (e.g., "H605C", "H6159")
private const val API_KEY = "23a8325c-3f7a-4525-ad31-a80cd5bbe888"
private const val DEVICE_ID = "9C:C6:3C:DC:75:13:BF:40"  // Get this from GoveeManager.getDevices()
private const val SKU = "H600D"  // Get this from GoveeManager.getDevices()
// ================================================

class MainActivity : ComponentActivity() {
  companion object {
    // Required Android permissions for the DAT SDK to function properly
    val PERMISSIONS: Array<String> = arrayOf(BLUETOOTH, BLUETOOTH_CONNECT, INTERNET)
  }

  val viewModel: WearablesViewModel by viewModels()

  private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
  private val permissionMutex = Mutex()
  // Requesting wearable device permissions via the Meta AI app
  private val permissionsResultLauncher =
      registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
        val permissionStatus = result.getOrDefault(PermissionStatus.Denied)
        permissionContinuation?.resume(permissionStatus)
        permissionContinuation = null
      }

  // Convenience method to make a permission request in a sequential manner
  // Uses a Mutex to ensure requests are processed one at a time, preventing race conditions
  suspend fun requestWearablesPermission(permission: Permission): PermissionStatus {
    return permissionMutex.withLock {
      suspendCancellableCoroutine { continuation ->
        permissionContinuation = continuation
        continuation.invokeOnCancellation { permissionContinuation = null }
        permissionsResultLauncher.launch(permission)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // First, ensure the app has necessary Android permissions
    checkPermissions {
      // Initialize the DAT SDK once the permissions are granted
      // This is REQUIRED before using any Wearables APIs
      Wearables.initialize(this)

      // Start observing Wearables state after SDK is initialized
      viewModel.startMonitoring()
    }

    setContent {
      CameraAccessScaffold(
          viewModel = viewModel,
          onRequestWearablesPermission = ::requestWearablesPermission,
          onTestLight = ::testGoveeLight,
          onSetBrightness = ::setGoveeBrightness,
          goveeApiKey = API_KEY,
          goveeDeviceId = DEVICE_ID,
          goveeSku = SKU,
      )
    }
  }

  fun checkPermissions(onPermissionsGranted: () -> Unit) {
    registerForActivityResult(RequestMultiplePermissions()) { permissionsResult ->
          val granted = permissionsResult.entries.all { it.value }
          if (granted) {
            onPermissionsGranted()
          } else {
            viewModel.setRecentError(
                "Allow All Permissions (Bluetooth, Bluetooth Connect, Internet)"
            )
          }
        }
        .launch(PERMISSIONS)
  }

  /**
   * Test the Govee light by turning it on, waiting 2 seconds, then turning it off.
   * Also fetches device list first to help debug device ID issues.
   */
  suspend fun testGoveeLight() {
    // First, fetch devices to verify credentials and log available devices
    // Check Logcat with tag "GoveeManager" to see your device IDs and SKUs
    val devices = GoveeManager.getDevices(API_KEY)
    if (devices.isEmpty()) {
      val error = GoveeManager.lastError ?: "No devices found or API error"
      viewModel.setRecentError("Failed to get devices: $error")
      // Continue anyway to try the configured device
    } else {
      android.util.Log.i("MainActivity", "Found ${devices.size} Govee device(s):")
      devices.forEach { device ->
        android.util.Log.i("MainActivity", "  Name: ${device.deviceName}")
        android.util.Log.i("MainActivity", "  Device ID: ${device.device}")
        android.util.Log.i("MainActivity", "  SKU: ${device.sku}")
        android.util.Log.i("MainActivity", "  ---")
      }
    }

    // Turn the light ON
    val onSuccess = GoveeManager.toggleLight(API_KEY, DEVICE_ID, SKU, turnOn = true)
    if (!onSuccess) {
      val error = GoveeManager.lastError ?: "Unknown error"
      viewModel.setRecentError("Failed to turn light ON: $error")
      return
    }

    // Wait 2 seconds
    delay(2000)

    // Turn the light OFF
    val offSuccess = GoveeManager.toggleLight(API_KEY, DEVICE_ID, SKU, turnOn = false)
    if (!offSuccess) {
      val error = GoveeManager.lastError ?: "Unknown error"
      viewModel.setRecentError("Failed to turn light OFF: $error")
    }
  }

  /**
   * Set the Govee light brightness.
   * @param brightness Brightness level from 0 to 100
   */
  suspend fun setGoveeBrightness(brightness: Int) {
    android.util.Log.d("MainActivity", "Setting brightness to $brightness%")
    
    val success = GoveeManager.setBrightness(API_KEY, DEVICE_ID, SKU, brightness)
    if (!success) {
      val error = GoveeManager.lastError ?: "Unknown error"
      viewModel.setRecentError("Failed to set brightness: $error")
    }
  }
}

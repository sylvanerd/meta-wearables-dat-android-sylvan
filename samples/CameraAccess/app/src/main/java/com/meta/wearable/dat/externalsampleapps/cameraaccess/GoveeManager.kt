/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.wearable.dat.externalsampleapps.cameraaccess

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Data class representing a Govee device capability.
 */
data class GoveeCapability(
    val type: String,      // e.g., "devices.capabilities.on_off", "devices.capabilities.range"
    val instance: String,  // e.g., "powerSwitch", "brightness"
    val parameters: String // Raw JSON string of parameters for reference
)

/**
 * Data class representing a Govee device.
 */
data class GoveeDevice(
    val device: String,  // Device ID used for control commands
    val sku: String,     // Product model/SKU
    val deviceName: String,
    val type: String,
    val capabilities: List<GoveeCapability> = emptyList()
)

/**
 * Singleton manager for controlling Govee Smart Lights via their API.
 * 
 * Uses the new Govee OpenAPI (v1) documented at:
 * https://developer.govee.com/reference/control-you-devices
 */
object GoveeManager {
    private const val TAG = "GoveeManager"
    
    // New Govee OpenAPI endpoints
    private const val GOVEE_API_BASE = "https://openapi.api.govee.com/router/api/v1"
    private const val GOVEE_DEVICES_URL = "$GOVEE_API_BASE/user/devices"
    private const val GOVEE_CONTROL_URL = "$GOVEE_API_BASE/device/control"
    
    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Holds the last error message for UI display
    var lastError: String? = null
        private set

    /**
     * Fetches the list of devices registered to your Govee account.
     * Use this to get the correct device ID and SKU for control commands.
     *
     * @param apiKey Your Govee API key
     * @return List of GoveeDevice objects, or empty list on failure
     */
    suspend fun getDevices(apiKey: String): List<GoveeDevice> = withContext(Dispatchers.IO) {
        try {
            lastError = null
            
            val request = Request.Builder()
                .url(GOVEE_DEVICES_URL)
                .addHeader("Govee-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            Log.d(TAG, "Fetching devices from: $GOVEE_DEVICES_URL")

            val response = client.newCall(request).execute()
            response.use { resp ->
                val responseBody = resp.body?.string()
                Log.d(TAG, "GetDevices Response code: ${resp.code}")
                Log.d(TAG, "GetDevices Response body: $responseBody")

                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}: $responseBody"
                    Log.e(TAG, "GetDevices failed: $lastError")
                    return@withContext emptyList()
                }

                responseBody?.let { body ->
                    try {
                        val json = JSONObject(body)
                        val code = json.optInt("code", -1)
                        
                        if (code != 200) {
                            val message = json.optString("message", "Unknown error")
                            lastError = "Govee Error $code: $message"
                            Log.e(TAG, lastError!!)
                            return@withContext emptyList()
                        }

                        val devices = mutableListOf<GoveeDevice>()
                        val dataArray = json.optJSONArray("data") ?: JSONArray()
                        
                        for (i in 0 until dataArray.length()) {
                            val deviceObj = dataArray.getJSONObject(i)
                            
                            // Parse capabilities
                            val capabilitiesList = mutableListOf<GoveeCapability>()
                            val capabilitiesArray = deviceObj.optJSONArray("capabilities") ?: JSONArray()
                            for (j in 0 until capabilitiesArray.length()) {
                                val capObj = capabilitiesArray.getJSONObject(j)
                                capabilitiesList.add(
                                    GoveeCapability(
                                        type = capObj.optString("type", ""),
                                        instance = capObj.optString("instance", ""),
                                        parameters = capObj.optJSONObject("parameters")?.toString() ?: "{}"
                                    )
                                )
                            }
                            
                            devices.add(
                                GoveeDevice(
                                    device = deviceObj.optString("device", ""),
                                    sku = deviceObj.optString("sku", ""),
                                    deviceName = deviceObj.optString("deviceName", "Unknown"),
                                    type = deviceObj.optString("type", ""),
                                    capabilities = capabilitiesList
                                )
                            )
                        }
                        
                        Log.d(TAG, "Found ${devices.size} devices")
                        devices.forEach { dev ->
                            Log.d(TAG, "========================================")
                            Log.d(TAG, "Device: ${dev.deviceName}")
                            Log.d(TAG, "  ID: ${dev.device}")
                            Log.d(TAG, "  SKU: ${dev.sku}")
                            Log.d(TAG, "  Type: ${dev.type}")
                            Log.d(TAG, "  Capabilities (${dev.capabilities.size}):")
                            dev.capabilities.forEach { cap ->
                                Log.d(TAG, "    - ${cap.type} / ${cap.instance}")
                                // Log full parameters for brightness to see the expected format
                                if (cap.instance == "brightness") {
                                    Log.d(TAG, "      BRIGHTNESS PARAMS: ${cap.parameters}")
                                }
                            }
                        }
                        
                        return@withContext devices
                    } catch (e: Exception) {
                        lastError = "Parse error: ${e.message}"
                        Log.e(TAG, "Could not parse devices response", e)
                    }
                }

                emptyList()
            }
        } catch (e: Exception) {
            lastError = "Exception: ${e.message}"
            Log.e(TAG, "getDevices failed", e)
            emptyList()
        }
    }

    /**
     * Toggles a Govee light on or off using the new OpenAPI format.
     *
     * @param apiKey Your Govee API key
     * @param deviceId The device ID (get this from getDevices(), e.g., "64:09:C5:32:37:36:2D:13")
     * @param sku The product SKU/model (get this from getDevices(), e.g., "H605C")
     * @param turnOn True to turn the light on, false to turn it off
     * @return True if the request was successful, false otherwise
     */
    suspend fun toggleLight(
        apiKey: String,
        deviceId: String,
        sku: String,
        turnOn: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            lastError = null
            
            // New OpenAPI request body format
            // See: https://developer.govee.com/reference/control-you-devices
            val jsonBody = JSONObject().apply {
                put("requestId", UUID.randomUUID().toString())
                put("payload", JSONObject().apply {
                    put("sku", sku)
                    put("device", deviceId)
                    put("capability", JSONObject().apply {
                        put("type", "devices.capabilities.on_off")
                        put("instance", "powerSwitch")
                        put("value", if (turnOn) 1 else 0)  // 1 = on, 0 = off
                    })
                })
            }

            Log.d(TAG, "Request URL: $GOVEE_CONTROL_URL")
            Log.d(TAG, "Request body: ${jsonBody.toString(2)}")

            val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(GOVEE_CONTROL_URL)
                .addHeader("Govee-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)  // New API uses POST, not PUT
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val responseBody = resp.body?.string()
                Log.d(TAG, "Response code: ${resp.code}")
                Log.d(TAG, "Response body: $responseBody")
                
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}: $responseBody"
                    Log.e(TAG, "API call failed: $lastError")
                    return@withContext false
                }
                
                // Check if the response indicates success
                responseBody?.let {
                    try {
                        val json = JSONObject(it)
                        val code = json.optInt("code", -1)
                        val message = json.optString("message", "")
                        
                        if (code != 200) {
                            lastError = "Govee Error $code: $message"
                            Log.e(TAG, lastError!!)
                            return@withContext false
                        }
                        
                        Log.d(TAG, "Successfully turned light ${if (turnOn) "ON" else "OFF"}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse response as JSON: $it")
                    }
                }
                
                true
            }
        } catch (e: Exception) {
            lastError = "Exception: ${e.message}"
            Log.e(TAG, "toggleLight failed", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Sets the brightness of a Govee light.
     *
     * @param apiKey Your Govee API key
     * @param deviceId The device ID
     * @param sku The product SKU/model
     * @param brightness Brightness level (0-100)
     * @return True if successful, false otherwise
     */
    suspend fun setBrightness(
        apiKey: String,
        deviceId: String,
        sku: String,
        brightness: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            lastError = null
            // Govee brightness range is 1-100, not 0-100
            val clampedBrightness = brightness.coerceIn(1, 100)
            
            val jsonBody = JSONObject().apply {
                put("requestId", UUID.randomUUID().toString())
                put("payload", JSONObject().apply {
                    put("sku", sku)
                    put("device", deviceId)
                    put("capability", JSONObject().apply {
                        put("type", "devices.capabilities.range")
                        put("instance", "brightness")
                        put("value", clampedBrightness)
                    })
                })
            }

            Log.d(TAG, "Setting brightness to $clampedBrightness")
            Log.d(TAG, "Request body: ${jsonBody.toString(2)}")

            val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(GOVEE_CONTROL_URL)
                .addHeader("Govee-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val responseBody = resp.body?.string()
                Log.d(TAG, "Response code: ${resp.code}")
                Log.d(TAG, "Response body: $responseBody")
                
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}: $responseBody"
                    Log.e(TAG, "setBrightness failed: $lastError")
                    return@withContext false
                }
                
                responseBody?.let {
                    try {
                        val json = JSONObject(it)
                        val code = json.optInt("code", -1)
                        val msg = json.optString("msg", json.optString("message", ""))
                        if (code != 200) {
                            lastError = "Govee Error $code: $msg"
                            Log.e(TAG, "setBrightness failed: $lastError")
                            Log.e(TAG, "Full response: $it")
                            return@withContext false
                        }
                        Log.d(TAG, "Brightness set successfully!")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse response: $it")
                    }
                }
                
                true
            }
        } catch (e: Exception) {
            lastError = "Exception: ${e.message}"
            Log.e(TAG, "setBrightness failed", e)
            false
        }
    }

    /**
     * Sets the color of a Govee light using RGB values.
     *
     * @param apiKey Your Govee API key
     * @param deviceId The device ID
     * @param sku The product SKU/model
     * @param red Red component (0-255)
     * @param green Green component (0-255)
     * @param blue Blue component (0-255)
     * @return True if successful, false otherwise
     */
    suspend fun setColor(
        apiKey: String,
        deviceId: String,
        sku: String,
        red: Int,
        green: Int,
        blue: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            lastError = null
            
            // Convert RGB to single integer value (0-16777215)
            val r = red.coerceIn(0, 255)
            val g = green.coerceIn(0, 255)
            val b = blue.coerceIn(0, 255)
            val rgbValue = (r shl 16) or (g shl 8) or b
            
            val jsonBody = JSONObject().apply {
                put("requestId", UUID.randomUUID().toString())
                put("payload", JSONObject().apply {
                    put("sku", sku)
                    put("device", deviceId)
                    put("capability", JSONObject().apply {
                        put("type", "devices.capabilities.color_setting")
                        put("instance", "colorRgb")
                        put("value", rgbValue)
                    })
                })
            }

            Log.d(TAG, "Setting color to RGB($r, $g, $b) = $rgbValue")
            Log.d(TAG, "Request body: ${jsonBody.toString(2)}")

            val requestBody = jsonBody.toString().toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(GOVEE_CONTROL_URL)
                .addHeader("Govee-API-Key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val responseBody = resp.body?.string()
                Log.d(TAG, "Response code: ${resp.code}")
                Log.d(TAG, "Response body: $responseBody")
                
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}: $responseBody"
                    Log.e(TAG, "setColor failed: $lastError")
                    return@withContext false
                }
                
                responseBody?.let {
                    try {
                        val json = JSONObject(it)
                        val code = json.optInt("code", -1)
                        if (code != 200) {
                            lastError = "Govee Error $code: ${json.optString("message", "")}"
                            Log.e(TAG, lastError!!)
                            return@withContext false
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not parse response: $it")
                    }
                }
                
                true
            }
        } catch (e: Exception) {
            lastError = "Exception: ${e.message}"
            Log.e(TAG, "setColor failed", e)
            false
        }
    }
}

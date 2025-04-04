package com.bleproject.bletester

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.util.isNotEmpty
import java.util.*
import java.util.Locale
import androidx.core.util.size

class MainActivity : AppCompatActivity() {

    // Extension function to make text bold
    private fun String.bold(): SpannableString {
        val spannable = SpannableString(this)
        spannable.setSpan(
            StyleSpan(Typeface.BOLD),
            0,
            this.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }

    private var scanPeriod: Long = 10000 // Default 10 seconds
    private lateinit var scanPeriodEditText: EditText

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListView: ListView
    private lateinit var scanButton: Button
    private lateinit var extendedOnlyCheckBox: CheckBox
    private lateinit var deviceAdapter: BleDeviceAdapter
    private val deviceList = ArrayList<BleDevice>()
    private val allDeviceList = ArrayList<BleDevice>()
    private val deviceMap = HashMap<String, BleDevice>()
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())

    // Activity result launcher for Bluetooth enable request
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            checkPermissionsAndStartScan()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to scan for devices", Toast.LENGTH_SHORT).show()
        }
    }

    // Activity result launcher for permissions
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            startScan()
        } else {
            Toast.makeText(this, "Required permissions not granted, cannot scan for devices", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        deviceListView = findViewById(R.id.deviceListView)
        scanButton = findViewById(R.id.scanButton)
        extendedOnlyCheckBox = findViewById(R.id.extendedOnlyCheckBox)
        scanPeriodEditText = findViewById(R.id.scanPeriodEditText)

        // Remove default dividers
        deviceListView.divider = null
        deviceListView.dividerHeight = 0

        // Initialize adapter
        deviceAdapter = BleDeviceAdapter(this, deviceList)
        deviceListView.adapter = deviceAdapter

        // Set up checkbox listener
        extendedOnlyCheckBox.setOnCheckedChangeListener { _, isChecked ->
            updateDeviceList()
        }

        // Initialize Bluetooth adapter
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Set up scan button
        scanButton.setOnClickListener {
            // Hide keyboard
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.hideSoftInputFromWindow(currentFocus?.windowToken, 0)

            if (!scanning) {
                checkPermissionsAndStartScan()
            } else {
                stopScan()
            }
        }
    }

    private fun checkPermissionsAndStartScan() {
        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    "Bluetooth connect permission not granted",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        // Required permissions for all Android versions
        val requiredPermissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(missingPermissions)
            return
        }

        // All permissions granted, start scan
        startScan()
    }

    private fun hasRequiredScanPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startScan() {
        // Verify permissions again before starting scan
        if (!hasRequiredScanPermissions()) {
            Toast.makeText(
                this,
                "Required scan permissions not granted",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Get scan period from EditText (in seconds)
        try {
            val inputPeriodSeconds = scanPeriodEditText.text.toString().toInt()
            // Validate input (minimum 1 second, maximum 5 minutes)
            val validatedSeconds = when {
                inputPeriodSeconds < 1 -> 1
                inputPeriodSeconds > 300 -> 300
                else -> inputPeriodSeconds
            }

            // Convert seconds to milliseconds
            scanPeriod = validatedSeconds * 1000L

            // Update EditText if the value was adjusted
            if (validatedSeconds != inputPeriodSeconds) {
                scanPeriodEditText.setText(validatedSeconds.toString())
                Toast.makeText(
                    this,
                    "Scan period adjusted to valid range (1-300 seconds)",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: NumberFormatException) {
            // Invalid input, use default
            scanPeriod = 10000  // 10 seconds in milliseconds
            scanPeriodEditText.setText("10")
            Toast.makeText(
                this,
                "Invalid input. Using default scan period (10 seconds)",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Clear previous results
        deviceList.clear()
        allDeviceList.clear()
        deviceMap.clear()
        deviceAdapter.notifyDataSetChanged()

        // Reset packet counters (though devices are cleared anyway, this is for clarity)

        scanning = true
        scanButton.text = "Stop Scan"

        // Start scan for specified period
        handler.postDelayed({
            stopScan()
        }, scanPeriod)

        try {
            // Configure scan settings for extended advertising
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setLegacy(false) // Set to false to scan for extended advertising packets
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build()

            // Create scan filters if you want to scan for specific devices (totally optional my guy)
            val scanFilters = ArrayList<ScanFilter>()
            // Example filter: scanFilters.add(ScanFilter.Builder().setDeviceName("YourDeviceName").build())

            // Start the scan with extended advertising settings
            bluetoothAdapter.bluetoothLeScanner?.startScan(
                scanFilters,
                scanSettings,
                extendedScanCallback
            ) ?: run {
                Toast.makeText(
                    this,
                    "Bluetooth LE Scanner not available",
                    Toast.LENGTH_SHORT
                ).show()
                scanning = false
                scanButton.text = "Scan for Devices"
            }
        } catch (_: SecurityException) {
            // Handle permission rejection
            Toast.makeText(
                this,
                "Cannot scan: Missing required permissions",
                Toast.LENGTH_SHORT
            ).show()
            scanning = false
            scanButton.text = "Scan for Devices"
        } catch (exception: Exception) {
            // Handle other potential exceptions
            Toast.makeText(
                this,
                "Error starting scan: ${exception.message}",
                Toast.LENGTH_SHORT
            ).show()
            scanning = false
            scanButton.text = "Scan for Devices"
        }
    }

    private fun stopScan() {
        if (scanning) {
            scanning = false
            scanButton.text = "Scan for Devices"

            // Check permissions before stopping scan
            if (!hasRequiredScanPermissions()) {
                return
            }

            try {
                bluetoothAdapter.bluetoothLeScanner?.stopScan(extendedScanCallback)
            } catch (_: SecurityException) {
                // Handle permission rejection
                Toast.makeText(
                    this,
                    "Cannot stop scan: Missing required permissions",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (exception: Exception) {
                // Handle other potential exceptions
                Toast.makeText(
                    this,
                    "Error stopping scan: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Function to convert byte array to hex string for display
    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }

    // Function to extract manufacturer data from scan record
    private fun extractManufacturerData(scanResult: ScanResult): String {
        val scanRecord = scanResult.scanRecord ?: return "None"

        val manufacturerSpecificData = scanRecord.manufacturerSpecificData
        if (manufacturerSpecificData != null && manufacturerSpecificData.isNotEmpty()) {
            val builder = StringBuilder()

            for (i in 0 until manufacturerSpecificData.size) {
                val manufacturerId = manufacturerSpecificData.keyAt(i)
                val data = manufacturerSpecificData.get(manufacturerId)

                if (data != null) {
                    builder.append("ID: 0x${manufacturerId.toString(16).uppercase(Locale.ROOT)} ")
                    builder.append("Data: ${data.toHexString()}")

                    // Try to parse as UTF-8 text if data is long enough
                    if (data.size > 2) {
                        try {
                            val text = String(data, Charsets.UTF_8).trim()
                            // Only include if it contains printable characters
                            if (text.isNotEmpty() && text.all { it.code >= 32 && it.code < 127 }) {
                                builder.append("\n")
                                builder.append("Text (UTF-8): ".bold())
                                builder.append(text)
                            }
                        } catch (e: Exception) {
                            // Ignore conversion errors
                        }
                    }
                }
            }

            return if (builder.isNotEmpty()) builder.toString() else "None"
        }

        return "None"
    }

    // Function to extract the complete raw advertising payload
    private fun extractRawPayload(scanResult: ScanResult): String {
        val scanRecord = scanResult.scanRecord ?: return "None"
        val recordBytes = scanRecord.bytes ?: return "None"

        return "0x" + recordBytes.toHexString()
    }

    // Enhanced scan callback for extended advertising
    private val extendedScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device
            val deviceAddress = device.address

            // Add permission check for getting device name
            val deviceName = if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                device.name ?: "Unknown Device"
            } else {
                "Unknown Device (Permission Required)"
            }

            val rssi = result.rssi
            val isExtended = result.isLegacy.not()
            val manufacturerData = extractManufacturerData(result)
            val rawPayload = extractRawPayload(result)

            val bleDevice = BleDevice(
                name = "$deviceName ($deviceAddress)",
                address = deviceAddress,
                rssi = rssi,
                isExtended = isExtended,
                manufacturerData = manufacturerData,
                rawPayload = rawPayload
            )

            runOnUiThread {
                if (!deviceMap.containsKey(deviceAddress)) {
                    // New device found
                    deviceMap[deviceAddress] = bleDevice
                    allDeviceList.add(bleDevice)
                } else {
                    // Update existing device
                    val index = allDeviceList.indexOf(deviceMap[deviceAddress])
                    if (index >= 0) {
                        // Increment packet count for existing device
                        val currentCount = deviceMap[deviceAddress]?.packetCount ?: 0
                        bleDevice.packetCount = currentCount + 1

                        // Update device in collections
                        deviceMap[deviceAddress] = bleDevice
                        allDeviceList[index] = bleDevice
                    }
                }
                updateDeviceList()
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            runOnUiThread {
                scanning = false
                scanButton.text = "Scan for Devices"
                val errorMessage = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Extended advertising not supported on this device"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    else -> "Error code: $errorCode"
                }
                Toast.makeText(this@MainActivity, "Scan failed: $errorMessage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Function to update the device list based on filter settings
    private fun updateDeviceList() {
        deviceList.clear()

        if (extendedOnlyCheckBox.isChecked) {
            // Only add extended advertising devices
            deviceList.addAll(allDeviceList.filter { it.isExtended })
        } else {
            // Add all devices
            deviceList.addAll(allDeviceList)
        }

        deviceAdapter.notifyDataSetChanged()
    }

    override fun onPause() {
        super.onPause()
        stopScan()
    }
}
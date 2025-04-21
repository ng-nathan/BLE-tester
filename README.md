# BLE Tester

Scans nearby BLE devices (including extended advertisements). Displays RSSI, raw payload, and manufacturer data. Toggle to filter extended packets. Set custom scan duration.

## BLE Device Info Displayed

- **Device Name**: From the complete or shortened local name in the advertising payload
- **MAC Address**: Unique Bluetooth address of the device
- **RSSI**: Signal strength in dBm when the advertisement was received
- **TX Power**: Transmit power level (if included in payload)
- **Advertising Interval**: Shown if present in extended advertising (not always available)
- **Service UUIDs**: Lists all advertised services (16-bit, 32-bit, or 128-bit)
- **Manufacturer Data**: Raw bytes labeled with company-specific identifier
- **Parsed Advertising Payload**: Human-readable breakdown of all advertising data fields (type, hex value, and decoded text if applicable)

## Requirements

- Android 12+

- Bluetooth Low Energy Extended Advertising support (_no need if test BLE legacy_)

## How It Works

### Permission

- Added permission via AndroidManifest first
- `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`
- Asked via `ActivityResultContracts.RequestMultiplePermissions()` inside `checkPermissionsAndStartScan()`

```kotlin
// Permission check before enabling Bluetooth
if (ActivityCompat.checkSelfPermission(
    this,
    Manifest.permission.BLUETOOTH_CONNECT
) != PackageManager.PERMISSION_GRANTED
)

// Permissions requested before scanning
val requiredPermissions = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

// Permission result handling
private val requestPermissionsLauncher = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
)
```

### BLE Scanning

- Initiated by clicking "Scan for Devices"
- Uses `bluetoothAdapter.bluetoothLeScanner.startScan(...)` with `scanSettings`
- Stops after N seconds via `Handler.postDelayed { stopScan() }`
- Scan results handled in `extendedScanCallback`

```kotlin
// Start scan
bluetoothAdapter.bluetoothLeScanner?.startScan(
    scanFilters,
    scanSettings,
    extendedScanCallback
)

// Stop scan
bluetoothAdapter.bluetoothLeScanner?.stopScan(extendedScanCallback)

// Scan settings (default: only extended advertising)
val scanSettings = ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .setLegacy(false)
    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
    .setPhy(ScanSettings.PHY_LE_ALL_SUPPORTED)
    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
    .build()

// Scan callback handling
private val extendedScanCallback = object : ScanCallback() { ... }
```

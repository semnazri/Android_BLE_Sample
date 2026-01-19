# Android BLE Sample

[![Kotlin](https://img.shields.io/badge/Kotlin-2.x.x-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-API%2021+-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Tests](https://img.shields.io/badge/Tests-31%20Passing-success.svg)](src/test)

A production-ready Bluetooth Low Energy (BLE) library for Android demonstrating communication with BLE devices. Built with modern Kotlin, Coroutines, and comprehensive test coverage.

---

## 🚀 Features

- ✅ **BLE Device Scanning** - Discover nearby BLE peripherals
- ✅ **Connect/Disconnect** - Manage GATT connections with proper lifecycle
- ✅ **Read Characteristic** - Read data from BLE characteristics
- ✅ **Write Characteristic** - Write data to BLE characteristics
- ✅ **Enable Notifications** - Subscribe to characteristic updates
- ✅ **Permission Handling** - Android 12+ and legacy permission support
- ✅ **State Management** - Reactive state using Kotlin Flow
- ✅ **Comprehensive Unit Tests** - 31 tests with MockK & Robolectric
- ✅ **API Level Compatibility** - Supports both legacy and modern Android APIs

---

## 📋 Requirements

- **Android Studio**: Arctic Fox or newer
- **Kotlin**: 2.x.x
- **Min SDK**: API 21 (Android 5.0)
- **Target SDK**: API 34 (Android 14)
- **Gradle**: 8.0+

---

## 🔧 Installation

### 1. Add Dependencies

Add to your `build.gradle.kts`:

```kotlin
android {
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
}
```

### 2. Add Permissions

Add to `AndroidManifest.xml`:

```xml
<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Android 11 and below -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true" />
```

### 3. Copy BleManager

Copy `BleManager.kt` to your project:

```
app/src/main/java/your/package/
└── BleManager.kt
```

---

## 📱 Quick Start

### Basic Usage

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var bleManager: BleManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        bleManager = BleManager(this)
        
        // 1. Check permissions
        if (!bleManager.checkPermissions()) {
            requestBlePermissions()
            return
        }
        
        // 2. Observe connection state
        lifecycleScope.launch {
            bleManager.connectionState.collect { state ->
                when (state) {
                    is BleManager.ConnectionState.Connected -> {
                        Log.d("BLE", "Device connected!")
                    }
                    is BleManager.ConnectionState.Disconnected -> {
                        Log.d("BLE", "Device disconnected")
                    }
                    is BleManager.ConnectionState.Error -> {
                        Log.e("BLE", "Error: ${state.message}")
                    }
                    else -> {}
                }
            }
        }
        
        // 3. Observe scan results
        lifecycleScope.launch {
            bleManager.scanResults.collect { devices ->
                devices.forEach { result ->
                    Log.d("BLE", "Found: ${result.device.name} (${result.device.address})")
                }
            }
        }
        
        // 4. Start scanning
        bleManager.startScan()
    }
    
    private fun requestBlePermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        requestPermissions(permissions, 1001)
    }
    
    override fun onDestroy() {
        bleManager.stopScan()
        bleManager.disconnect()
        super.onDestroy()
    }
}
```

---

## 📚 API Documentation

### BleManager Class

Main class for managing all BLE operations.

#### Methods

##### `checkPermissions(): Boolean`

Check if all required BLE permissions are granted.

```kotlin
val hasPermissions = bleManager.checkPermissions()
if (!hasPermissions) {
    // Request permissions
}
```

##### `startScan()`

Start scanning for nearby BLE devices.

```kotlin
bleManager.startScan()

lifecycleScope.launch {
    bleManager.scanResults.collect { results ->
        // Update UI with scan results
    }
}
```

##### `stopScan()`

Stop scanning for BLE devices.

```kotlin
bleManager.stopScan()
```

##### `connect(device: BluetoothDevice)`

Connect to a specific BLE device.

```kotlin
bleManager.connect(bluetoothDevice)

lifecycleScope.launch {
    bleManager.connectionState.collect { state ->
        when (state) {
            is BleManager.ConnectionState.Connected -> {
                // Handle connected
            }
            is BleManager.ConnectionState.Error -> {
                Log.e("BLE", state.message)
            }
            else -> {}
        }
    }
}
```

##### `disconnect()`

Disconnect from the connected BLE device.

```kotlin
bleManager.disconnect()
```

##### `readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): Boolean`

Read data from a BLE characteristic.

```kotlin
val success = bleManager.readCharacteristic(
    serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
    characteristicUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
)

if (success) {
    lifecycleScope.launch {
        bleManager.receivedData.collect { data ->
            data?.let {
                Log.d("BLE", "Received: ${it.joinToString()}")
            }
        }
    }
}
```

##### `writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray): Boolean`

Write data to a BLE characteristic.

- **API 33+**: Uses new method with status code
- **Legacy API**: Uses deprecated method with boolean return

```kotlin
val data = byteArrayOf(0x01, 0x02, 0x03)
val success = bleManager.writeCharacteristic(
    serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
    characteristicUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb"),
    data = data
)

if (success) {
    Log.d("BLE", "Write successful")
}
```

##### `enableNotification(serviceUuid: UUID, characteristicUuid: UUID): Boolean`

Enable notifications for a BLE characteristic.

- **API 33+**: Uses new method with status code
- **Legacy API**: Uses deprecated method with boolean return

```kotlin
val success = bleManager.enableNotification(
    serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
    characteristicUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
)

// Receive notifications
lifecycleScope.launch {
    bleManager.receivedData.collect { data ->
        data?.let {
            Log.d("BLE", "Notification: ${it.joinToString()}")
        }
    }
}
```

#### StateFlow Properties

##### `scanResults: StateFlow<List<ScanResult>>`

Emits list of BLE devices found during scanning.

##### `connectionState: StateFlow<ConnectionState>`

Emits current connection state:
- `ConnectionState.Disconnected` - Not connected
- `ConnectionState.Connecting` - Connecting in progress
- `ConnectionState.Connected` - Successfully connected
- `ConnectionState.Error(message)` - Error occurred

##### `receivedData: StateFlow<ByteArray?>`

Emits data received from read operations or notifications.

---

## 🏗️ Project Structure

```
Android_BLE_Sample/
├── app/src/main/java/id/sam/androidblesample/
│   ├── BleManager.kt              # Main BLE manager class
│   └── MainActivity.kt            # Example activity
├── app/src/test/java/id/sam/androidblesample/
│   └── BleManagerTest.kt          # Comprehensive unit tests
└── build.gradle.kts
```

---

## 🧪 Testing

The project includes comprehensive unit tests using MockK and Robolectric.

### Run Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew testDebugUnitTestCoverage

# View coverage report
open app/build/reports/coverage/test/debug/index.html
```

### Test Coverage

**31 passing tests** covering:

- ✅ Permission checking (granted/denied)
- ✅ BLE scan operations (start/stop/callbacks)
- ✅ Connection lifecycle (connect/disconnect/state changes)
- ✅ GATT callback handling (all callbacks tested)
- ✅ Read characteristic (success/failure cases)
- ✅ Write characteristic (legacy API & API 33+)
- ✅ Enable notification (legacy API & API 33+)
- ✅ SecurityException handling
- ✅ State flow management
- ✅ Edge cases (multiple connects, duplicate devices, etc.)

### Test Structure

```
BleManagerTest.kt
├── Permission Tests (2 tests)
├── Scan Tests (5 tests)
├── Connection Tests (3 tests)
├── GATT Callback Tests (6 tests)
├── Read/Write Characteristic Tests (3 tests)
├── Notification Tests (2 tests)
├── Flow State Tests (3 tests)
└── Edge Case Tests (2 tests)
```

---

## 🔑 Key Implementation Details

### API Level Compatibility

The code handles differences between legacy and modern Android APIs:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    // API 33+ (Android 13+) - Use new method
    val statusCode = bluetoothGatt?.writeCharacteristic(
        characteristic, data, WRITE_TYPE_DEFAULT
    )
    statusCode == BluetoothStatusCodes.SUCCESS
} else {
    // Legacy API - Use deprecated method
    characteristic.value = data
    bluetoothGatt?.writeCharacteristic(characteristic) ?: false
}
```

### Exception Handling

All BLE operations handle `SecurityException` for permission issues:

```kotlin
try {
    bluetoothGatt?.readCharacteristic(characteristic)
} catch (e: SecurityException) {
    _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
    false
}
```

### State Management

Uses Kotlin Flow for reactive state management:

```kotlin
private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
```

### CCCD (Client Characteristic Configuration Descriptor)

The hardcoded UUID `00002902-0000-1000-8000-00805f9b34fb` is the **Bluetooth SIG standard** CCCD UUID used by all BLE devices for enabling notifications/indications.

---

## 🐛 Troubleshooting

### Issue: `SecurityException: Permission denied`

**Solution**: Ensure permissions are granted in Android settings or request at runtime for Android 6+.

```kotlin
if (!bleManager.checkPermissions()) {
    requestPermissions(/* permissions array */, REQUEST_CODE)
}
```

### Issue: Cannot connect to device

**Solutions**:
- Ensure device is powered on and in range
- Check if permissions are granted
- Verify service and characteristic UUIDs are correct
- Try disconnecting and reconnecting

### Issue: Write characteristic returns false

**Solutions**:
- Ensure characteristic has write property
- Check if device is connected
- Verify data size doesn't exceed MTU (default 20 bytes)
- Check for SecurityException in logs

### Issue: Notifications not received

**Solutions**:
- Ensure `enableNotification()` succeeds
- Verify CCCD UUID is correct
- Check if characteristic supports notify or indicate property
- Confirm device is sending notifications

---

## 📖 Usage Examples

### Example 1: Heart Rate Monitor

```kotlin
val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
val HR_MEASUREMENT_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")

// Connect and enable notifications
bleManager.connect(heartRateDevice)

lifecycleScope.launch {
    bleManager.connectionState.collect { state ->
        if (state is BleManager.ConnectionState.Connected) {
            bleManager.enableNotification(HR_SERVICE_UUID, HR_MEASUREMENT_UUID)
        }
    }
}

// Receive heart rate data
lifecycleScope.launch {
    bleManager.receivedData.collect { data ->
        data?.let {
            val heartRate = it[1].toInt() and 0xFF
            Log.d("BLE", "Heart Rate: $heartRate bpm")
        }
    }
}
```

### Example 2: Custom Command

```kotlin
val CUSTOM_SERVICE = UUID.fromString("12345678-1234-1234-1234-123456789abc")
val COMMAND_CHAR = UUID.fromString("12345678-1234-1234-1234-123456789abd")

// Send custom command
val command = byteArrayOf(0x01, 0x0A, 0xFF)
val success = bleManager.writeCharacteristic(
    CUSTOM_SERVICE,
    COMMAND_CHAR,
    command
)

if (success) {
    Log.d("BLE", "Command sent successfully")
}
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Sam Nazri**
- GitHub: [@semnazri](https://github.com/semnazri)

---

## 🙏 Acknowledgments

- Kotlin Coroutines for reactive programming
- MockK for comprehensive mocking in tests
- Robolectric for Android unit testing
- Android BLE API documentation

---

## 📝 Changelog

### Version 1.0.0 (January 2026)
- ✨ Initial release
- ✅ BLE scan, connect, read, write, notifications
- ✅ Android 12+ permission support
- ✅ Comprehensive test coverage (31 tests)
- ✅ API level compatibility (API 21 - 34)

---

**Last Updated**: January 2026  
**Version**: 1.0.0  
**Build Status**: ✅ All Tests Passing
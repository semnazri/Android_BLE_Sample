package id.sam.androidblesample.core

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _receivedData = MutableStateFlow<ByteArray?>(null)
    val receivedData: StateFlow<ByteArray?> = _receivedData.asStateFlow()

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    // Scan Callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val currentResults = _scanResults.value.toMutableList()
            if (!currentResults.any { it.device.address == result.device.address }) {
                currentResults.add(result)
                _scanResults.value = currentResults
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Error("Scan failed: $errorCode")
        }
    }

    // GATT Callback
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connected
                    if (checkPermissions()) {
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            _connectionState.value =
                                ConnectionState.Error("Permission denied: ${e.message}")
                        }
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    bluetoothGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Services discovered, siap untuk read/write

                // Optional: Log semua services yang ditemukan
                gatt.services?.forEach { service ->
                    android.util.Log.d("BleManager", "Service UUID: ${service.uuid}")
                    service.characteristics?.forEach { characteristic ->
                        android.util.Log.d(
                            "BleManager",
                            "  Characteristic UUID: ${characteristic.uuid}"
                        )
                        android.util.Log.d(
                            "BleManager",
                            "    Properties: ${characteristic.properties}"
                        )
                    }
                }

                // Optional: Auto-enable notification untuk characteristic tertentu
                // Uncomment dan sesuaikan dengan UUID device lu:
                // val serviceUuid = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
                // val charUuid = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
                // enableNotification(serviceUuid, charUuid)

                // Update state bahwa device ready
                _connectionState.value = ConnectionState.Connected
            } else {
                _connectionState.value = ConnectionState.Error("Service discovery failed: $status")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _receivedData.value = value
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            _receivedData.value = value
        }
    }

    /**
     * Cek permission BLE
     */
    fun checkPermissions(): Boolean {
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

        return permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Mulai scan BLE devices
     */
    fun startScan() {
        if (!checkPermissions()) {
            _connectionState.value = ConnectionState.Error("Permission not granted")
            return
        }

        try {
            _scanResults.value = emptyList()
            bleScanner?.startScan(scanCallback)
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
        }
    }

    /**
     * Stop scan BLE devices
     */
    fun stopScan() {
        if (!checkPermissions()) return

        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
        }
    }

    /**
     * Connect ke BLE device
     */
    fun connect(device: BluetoothDevice) {
        if (!checkPermissions()) {
            _connectionState.value = ConnectionState.Error("Permission not granted")
            return
        }

        try {
            _connectionState.value = ConnectionState.Connecting
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
        }
    }

    /**
     * Disconnect dari BLE device
     */
    fun disconnect() {
        if (!checkPermissions()) return

        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
        }
        bluetoothGatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Read characteristic dari BLE device
     */
    fun readCharacteristic(serviceUuid: UUID, characteristicUuid: UUID): Boolean {
        if (!checkPermissions()) return false

        val service = bluetoothGatt?.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

        return try {
            bluetoothGatt?.readCharacteristic(characteristic) ?: false
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
            false
        }
    }

    /**
     * Write data ke characteristic
     */
    fun writeCharacteristic(serviceUuid: UUID, characteristicUuid: UUID, data: ByteArray): Boolean {
        if (!checkPermissions()) return false

        val service = bluetoothGatt?.getService(serviceUuid) ?: return false
        val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusCode = bluetoothGatt?.writeCharacteristic(
                    characteristic,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ) ?: return false
                statusCode == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = data
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(characteristic) ?: false
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
            false
        }
    }

    /**
     * Enable notification untuk characteristic
     */
    fun enableNotification(serviceUuid: UUID, characteristicUuid: UUID): Boolean {
        if (!checkPermissions()) return false

        return try {
            val service = bluetoothGatt?.getService(serviceUuid) ?: return false
            val characteristic = service.getCharacteristic(characteristicUuid) ?: return false

            bluetoothGatt?.setCharacteristicNotification(characteristic, true) ?: return false

            // Enable notification di descriptor
            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            ) ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val statusCode = bluetoothGatt?.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) ?: return false
                statusCode == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeDescriptor(descriptor) ?: false
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Permission denied: ${e.message}")
            false
        }
    }
}
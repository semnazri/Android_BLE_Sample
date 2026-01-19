package id.sam.androidblesample

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import id.sam.androidblesample.core.BleManager
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BleManagerTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bleScanner: BluetoothLeScanner
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var bluetoothGatt: BluetoothGatt
    private lateinit var bleManager: BleManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk(relaxed = true)
        bleScanner = mockk(relaxed = true)
        bluetoothDevice = mockk(relaxed = true)
        bluetoothGatt = mockk(relaxed = true)

        mockkStatic(android.os.Process::class)
        every { android.os.Process.myPid() } returns 1000

        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.bluetoothLeScanner } returns bleScanner

        mockkStatic(Build.VERSION::class)

        // Mock checkSelfPermission di instance context
        every {
            context.checkSelfPermission(any())
        } returns PackageManager.PERMISSION_GRANTED

        bleManager = BleManager(context)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ============================================
    // PERMISSION TESTS
    // ============================================


    @Test
    fun `test permissions granted - should return true`() {
        // Context sudah di-mock di setup dengan PERMISSION_GRANTED
        val result = bleManager.checkPermissions()
        assertTrue("Should return true when all permissions granted", result)
    }

    @Test
    fun `test permissions denied - should return false`() {
        // Override mock untuk test ini
        every {
            context.checkSelfPermission(any())
        } returns PackageManager.PERMISSION_DENIED

        val result = bleManager.checkPermissions()
        assertFalse("Should return false when permissions denied", result)
    }

    // ============================================
    // SCAN TESTS
    // ============================================

    @Test
    fun `test startScan with permission - should start scanning`() {
        // Given
        every { bleScanner.startScan(any()) } just Runs

        // When
        bleManager.startScan()

        // Then
        verify(exactly = 1) { bleScanner.startScan(any()) }
        assertEquals("Scan results should be empty initially", 0, bleManager.scanResults.value.size)
    }

    @Test
    fun `test startScan without permission - should set error state`() = runTest {
        // Given
        every {
            context.checkSelfPermission(any())
        } returns PackageManager.PERMISSION_DENIED

        // When
        bleManager.startScan()

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be error state", state is BleManager.ConnectionState.Error)
        assertEquals("Permission not granted", (state as BleManager.ConnectionState.Error).message)
        verify(exactly = 0) { bleScanner.startScan(any()) }
    }

    @Test
    fun `test stopScan - should stop scanning`() {
        // Given
        every { bleScanner.stopScan(any<ScanCallback>()) } just Runs

        // When
        bleManager.stopScan()

        // Then
        verify(exactly = 1) { bleScanner.stopScan(any<ScanCallback>()) }
    }

    @Test
    fun `test scan callback onScanResult - should add device to results`() = runTest {
        // Given
        val scanCallbackSlot = slot<ScanCallback>()
        every { bleScanner.startScan(capture(scanCallbackSlot)) } just Runs

        val scanResult = mockk<ScanResult>(relaxed = true)
        every { scanResult.device } returns bluetoothDevice
        every { bluetoothDevice.address } returns "AA:BB:CC:DD:EE:FF"
        every { scanResult.rssi } returns -50

        // When
        bleManager.startScan()
        scanCallbackSlot.captured.onScanResult(0, scanResult)

        // Then
        val results = bleManager.scanResults.value
        assertEquals("Should have 1 scan result", 1, results.size)
        assertEquals("AA:BB:CC:DD:EE:FF", results[0].device.address)
    }

    @Test
    fun `test scan callback onScanResult - should not add duplicate devices`() = runTest {
        // Given
        val scanCallbackSlot = slot<ScanCallback>()
        every { bleScanner.startScan(capture(scanCallbackSlot)) } just Runs

        val scanResult = mockk<ScanResult>(relaxed = true)
        every { scanResult.device } returns bluetoothDevice
        every { bluetoothDevice.address } returns "AA:BB:CC:DD:EE:FF"

        // When
        bleManager.startScan()
        scanCallbackSlot.captured.onScanResult(0, scanResult)
        scanCallbackSlot.captured.onScanResult(0, scanResult) // Same device

        // Then
        assertEquals("Should have only 1 unique device", 1, bleManager.scanResults.value.size)
    }

    @Test
    fun `test scan callback onScanFailed - should set error state`() = runTest {
        // Given
        val scanCallbackSlot = slot<ScanCallback>()
        every { bleScanner.startScan(capture(scanCallbackSlot)) } just Runs

        // When
        bleManager.startScan()
        scanCallbackSlot.captured.onScanFailed(1)

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be error state", state is BleManager.ConnectionState.Error)
        assertTrue((state as BleManager.ConnectionState.Error).message.contains("Scan failed"))
    }

    // ============================================
    // CONNECTION TESTS
    // ============================================

    @Test
    fun `test connect with permission - should update state to Connecting`() = runTest {
        // Given
        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt

        // When
        bleManager.connect(bluetoothDevice)

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be connecting state", state is BleManager.ConnectionState.Connecting)
        verify(exactly = 1) { bluetoothDevice.connectGatt(any(), false, any()) }
    }

    @Test
    fun `test connect without permission - should set error state`() = runTest {
        // Given
        every {
            context.checkSelfPermission(any())
        } returns PackageManager.PERMISSION_DENIED

        // When
        bleManager.connect(bluetoothDevice)

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be error state", state is BleManager.ConnectionState.Error)
        verify(exactly = 0) { bluetoothDevice.connectGatt(any(), any(), any()) }
    }

    @Test
    fun `test disconnect - should close GATT and update state`() = runTest {
        // Given
        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.disconnect() } just Runs
        every { bluetoothGatt.close() } just Runs

        bleManager.connect(bluetoothDevice)

        // When
        bleManager.disconnect()

        // Then
        verify(exactly = 1) { bluetoothGatt.disconnect() }
        verify(exactly = 1) { bluetoothGatt.close() }
        val state = bleManager.connectionState.value
        assertTrue("Should be disconnected state", state is BleManager.ConnectionState.Disconnected)
    }

    @Test
    fun `test disconnect without active connection - should not crash`() {
        // When
        bleManager.disconnect()

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be disconnected state", state is BleManager.ConnectionState.Disconnected)
    }

    // ============================================
    // GATT CALLBACK TESTS
    // ============================================

    @Test
    fun `test GATT callback onConnectionStateChange - Connected`() = runTest {
        // Given
        val gattCallbackSlot = slot<BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(gattCallbackSlot)
            )
        } returns bluetoothGatt
        every { bluetoothGatt.discoverServices() } returns true

        bleManager.connect(bluetoothDevice)

        // When
        gattCallbackSlot.captured.onConnectionStateChange(
            bluetoothGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_CONNECTED
        )

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be connected state", state is BleManager.ConnectionState.Connected)
        verify(exactly = 1) { bluetoothGatt.discoverServices() }
    }

    @Test
    fun `test GATT callback onConnectionStateChange - Disconnected`() = runTest {
        // Given
        val gattCallbackSlot = slot<BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(gattCallbackSlot)
            )
        } returns bluetoothGatt

        bleManager.connect(bluetoothDevice)

        // When
        gattCallbackSlot.captured.onConnectionStateChange(
            bluetoothGatt,
            BluetoothGatt.GATT_SUCCESS,
            BluetoothProfile.STATE_DISCONNECTED
        )

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be disconnected state", state is BleManager.ConnectionState.Disconnected)
    }

    @Test
    fun `test GATT callback onServicesDiscovered - Success`() = runTest {
        // Given
        val gattCallbackSlot = slot<BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(gattCallbackSlot)
            )
        } returns bluetoothGatt
        every { bluetoothGatt.services } returns emptyList()

        bleManager.connect(bluetoothDevice)

        // When
        gattCallbackSlot.captured.onServicesDiscovered(bluetoothGatt, BluetoothGatt.GATT_SUCCESS)

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be connected state", state is BleManager.ConnectionState.Connected)
    }

    @Test
    fun `test GATT callback onServicesDiscovered - Failed`() = runTest {
        // Given
        val gattCallbackSlot = slot<BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(gattCallbackSlot)
            )
        } returns bluetoothGatt

        bleManager.connect(bluetoothDevice)

        // When
        gattCallbackSlot.captured.onServicesDiscovered(bluetoothGatt, BluetoothGatt.GATT_FAILURE)

        // Then
        val state = bleManager.connectionState.value
        assertTrue("Should be error state", state is BleManager.ConnectionState.Error)
    }

    @Test
    fun `test GATT callback onCharacteristicRead - Success`() = runTest {
        // Given
        val gattCallbackSlot = slot<BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(gattCallbackSlot)
            )
        } returns bluetoothGatt

        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val expectedData = byteArrayOf(0x01, 0x02, 0x03)

        bleManager.connect(bluetoothDevice)

        // When
        gattCallbackSlot.captured.onCharacteristicRead(
            bluetoothGatt,
            characteristic,
            expectedData,
            BluetoothGatt.GATT_SUCCESS
        )

        // Then
        val receivedData = bleManager.receivedData.value
        assertNotNull("Should have received data", receivedData)
        assertArrayEquals("Data should match", expectedData, receivedData)
    }

    @Test
    fun `test GATT callback onCharacteristicChanged - Notification`() = runTest {
        // Given
        val gattCallbackSlot = slot<BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(gattCallbackSlot)
            )
        } returns bluetoothGatt

        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)
        val expectedData = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())

        bleManager.connect(bluetoothDevice)

        // When
        gattCallbackSlot.captured.onCharacteristicChanged(
            bluetoothGatt,
            characteristic,
            expectedData
        )

        // Then
        val receivedData = bleManager.receivedData.value
        assertNotNull("Should have received data", receivedData)
        assertArrayEquals("Data should match", expectedData, receivedData)
    }

    // ============================================
    // READ/WRITE CHARACTERISTIC TESTS
    // ============================================

    @Test
    fun `test readCharacteristic - Success`() {
        // Given
        val serviceUuid = UUID.randomUUID()
        val charUuid = UUID.randomUUID()
        val service = mockk<BluetoothGattService>(relaxed = true)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.getService(serviceUuid) } returns service
        every { service.getCharacteristic(charUuid) } returns characteristic
        every { bluetoothGatt.readCharacteristic(characteristic) } returns true

        bleManager.connect(bluetoothDevice)

        // When
        val result = bleManager.readCharacteristic(serviceUuid, charUuid)

        // Then
        assertTrue("Read should succeed", result)
        verify(exactly = 1) { bluetoothGatt.readCharacteristic(characteristic) }
    }

    @Test
    fun `test readCharacteristic - Service not found`() {
        // Given
        val serviceUuid = UUID.randomUUID()
        val charUuid = UUID.randomUUID()

        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.getService(serviceUuid) } returns null

        bleManager.connect(bluetoothDevice)

        // When
        val result = bleManager.readCharacteristic(serviceUuid, charUuid)

        // Then
        assertFalse("Read should fail when service not found", result)
    }

    @Test
    fun `test readCharacteristic - Characteristic not found`() {
        // Given
        val serviceUuid = UUID.randomUUID()
        val charUuid = UUID.randomUUID()
        val service = mockk<BluetoothGattService>(relaxed = true)

        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.getService(serviceUuid) } returns service
        every { service.getCharacteristic(charUuid) } returns null

        bleManager.connect(bluetoothDevice)

        // When
        val result = bleManager.readCharacteristic(serviceUuid, charUuid)

        // Then
        assertFalse("Read should fail when characteristic not found", result)
    }

    // ============================================
    // NOTIFICATION TESTS
    // ============================================

    @Test
    fun `test enableNotification - Descriptor not found`() {
        // Given
        val serviceUuid = UUID.randomUUID()
        val charUuid = UUID.randomUUID()
        val descriptorUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        val service = mockk<BluetoothGattService>(relaxed = true)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.getService(serviceUuid) } returns service
        every { service.getCharacteristic(charUuid) } returns characteristic
        every { characteristic.getDescriptor(descriptorUuid) } returns null
        every { bluetoothGatt.setCharacteristicNotification(characteristic, true) } returns true

        bleManager.connect(bluetoothDevice)

        // When
        val result = bleManager.enableNotification(serviceUuid, charUuid)

        // Then
        assertFalse("Should fail when descriptor not found", result)
    }

    // ============================================
    // FLOW STATE TESTS
    // ============================================

    @Test
    fun `test initial connectionState is Disconnected`() = runTest {
        // Then
        val state = bleManager.connectionState.value
        assertTrue(
            "Initial state should be Disconnected",
            state is BleManager.ConnectionState.Disconnected
        )
    }

    @Test
    fun `test initial scanResults is empty`() = runTest {
        // Then
        assertEquals(
            "Initial scan results should be empty",
            0, bleManager.scanResults.value.size
        )
    }

    @Test
    fun `test initial receivedData is null`() = runTest {
        // Then
        assertNull(
            "Initial received data should be null",
            bleManager.receivedData.value
        )
    }

    // ============================================
    // EDGE CASE TESTS
    // ============================================

    @Test
    fun `test multiple connect disconnect cycles`() {
        // Given
        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.disconnect() } just Runs
        every { bluetoothGatt.close() } just Runs

        // When
        repeat(3) {
            bleManager.connect(bluetoothDevice)
            bleManager.disconnect()
        }

        // Then
        val state = bleManager.connectionState.value
        assertTrue(
            "Should end in disconnected state",
            state is BleManager.ConnectionState.Disconnected
        )
    }

    @Test
    fun `test scan multiple devices`() = runTest {
        // Given
        val scanCallbackSlot = slot<ScanCallback>()
        every { bleScanner.startScan(capture(scanCallbackSlot)) } just Runs

        val devices = listOf("AA:BB:CC:DD:EE:FF", "11:22:33:44:55:66", "FF:EE:DD:CC:BB:AA")

        bleManager.startScan()

        // When
        devices.forEachIndexed { index, address ->
            val scanResult = mockk<ScanResult>(relaxed = true)
            val device = mockk<BluetoothDevice>(relaxed = true)
            every { scanResult.device } returns device
            every { device.address } returns address
            every { scanResult.rssi } returns -50 - index * 10

            scanCallbackSlot.captured.onScanResult(0, scanResult)
        }

        // Then
        assertEquals("Should have 3 devices", 3, bleManager.scanResults.value.size)
    }

    @Test
    fun `test readCharacteristic - SecurityException thrown`() {
        // Given
        val serviceUuid = UUID.randomUUID()
        val charUuid = UUID.randomUUID()
        val service = mockk<BluetoothGattService>(relaxed = true)
        val characteristic = mockk<BluetoothGattCharacteristic>(relaxed = true)

        every { bluetoothDevice.connectGatt(any(), any(), any()) } returns bluetoothGatt
        every { bluetoothGatt.getService(serviceUuid) } returns service
        every { service.getCharacteristic(charUuid) } returns characteristic
        every { bluetoothGatt.readCharacteristic(characteristic) } throws SecurityException("Permission denied")

        bleManager.connect(bluetoothDevice)

        // When
        val result = bleManager.readCharacteristic(serviceUuid, charUuid)

        // Then
        assertFalse("Should return false on SecurityException", result)
        val state = bleManager.connectionState.value
        assertTrue("Should be error state", state is BleManager.ConnectionState.Error)
    }
}
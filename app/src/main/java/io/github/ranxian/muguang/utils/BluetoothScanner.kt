package io.github.ranxian.muguang.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import io.github.ranxian.muguang.model.NearbyDevice

/**
 * @param onScanTimeout 扫描结束回调，参数是**是否正常扫完**。
 *        正常扫完但一台都没扫到也是 true——那是个有效结果（"附近没有设备"）；
 *        只有当扫描本身失败（如蓝牙被关掉）时才传 false，
 *        调用方据此决定要不要更新小组件的「附近设备」快照。
 */
class BluetoothScanner(
    context: Context,
    private val onDeviceFound: (NearbyDevice) -> Unit,
    private val onScanTimeout: ((ok: Boolean) -> Unit)? = null
) {
    // 没有蓝牙硬件的设备上 getSystemService 可能返回 null，`as BluetoothManager`
    // 会直接抛异常。蓝牙是可选能力（见清单里的 bluetooth_le uses-feature），
    // 这种情况下应当退化成「扫不到设备」，而不是让 App 起不来。
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner = adapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var callbackDelivered = false
    private var timeoutRunnable: Runnable? = null

    private fun complete(ok: Boolean) {
        if (callbackDelivered) return
        callbackDelivered = true
        onScanTimeout?.invoke(ok)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = device.name ?: return
            if (deviceName.startsWith("KLCXKJ", ignoreCase = true)) {
                onDeviceFound(
                    NearbyDevice(
                        name = deviceName,
                        mac = device.address,
                        rssi = result.rssi
                    )
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            timeoutRunnable?.let(handler::removeCallbacks)
            timeoutRunnable = null
            complete(false)
        }
    }

    /** 默认扫描时长。热水器广播间隔很短，5 秒足够扫全，再久只是让用户干等 */
    @SuppressLint("MissingPermission")
    fun startScan(timeoutMillis: Long = 5_000) {
        if (isScanning) return
        val le = scanner
        if (le == null) {
            // 没有 BLE 适配器 / 蓝牙未开启时**不能静默返回**：
            // 调用方只靠 onScanTimeout 复位「扫描中」，没有这个回调，
            // 界面会永远停在「扫描中…」，连「附近没有设备」都不会显示。
            // 上一轮的 callbackDelivered 可能还是 true，必须先清掉。
            callbackDelivered = false
            isScanning = false
            AppLogger.w("蓝牙扫描不可用：没有可用的 BLE 扫描器")
            complete(false)
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        callbackDelivered = false
        isScanning = true
        // 硬件过滤器精确匹配可能漏掉设备，靠回调里的软件过滤兜底
        le.startScan(null, settings, scanCallback)

        // 失败后若超时任务仍存活，上层会先收到失败、再把空列表当有效快照；
        // 保存同一个 Runnable 才能在失败或手动停止时精确撤销它。
        timeoutRunnable = Runnable {
            if (!isScanning) return@Runnable
            stopScanInternal()
            complete(true)
        }.also { handler.postDelayed(it, timeoutMillis) }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        stopScanInternal()
        // 手动停止不代表扫描成功，且要屏蔽随后到达的失败回调。
        callbackDelivered = true
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        timeoutRunnable?.let(handler::removeCallbacks)
        timeoutRunnable = null
        if (!isScanning) return
        isScanning = false
        scanner?.stopScan(scanCallback)
    }
}

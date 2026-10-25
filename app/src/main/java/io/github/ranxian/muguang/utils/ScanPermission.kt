package io.github.ranxian.muguang.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 蓝牙扫描所需权限。
 *
 * 为什么会有「定位权限」这件事：
 * BLE 扫描结果（周围设备的 MAC + 信号强度）能被用来推算物理位置，所以
 * Android 11 及以下把蓝牙发现和定位权限绑死——**不管 App 是否真的定位，
 * 只要扫描就必须有 ACCESS_FINE_LOCATION**，没有绕过的办法。
 *
 * Android 12 起系统提供了替代方案：给 BLUETOOTH_SCAN 声明
 * `neverForLocation`（见 AndroidManifest.xml），表示"我不用扫描结果定位"，
 * 之后只需要「附近的设备」权限，定位权限可以彻底不申请。
 *
 * 所以这里的策略是：**按系统版本决定要哪一组**，而不是一律都要。
 */
object ScanPermission {

    /** 当前系统版本下，蓝牙扫描真正需要的权限 */
    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

    /** 是否已经拿到全部必需权限 */
    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** 申请失败时的提示文案（区分是蓝牙被拒还是定位被拒，便于用户理解） */
    fun deniedMessage(): String = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        "未授予「附近的设备」权限，无法扫描附近设备"
    } else {
        "未授予定位权限，无法扫描附近设备（Android 11 及以下系统强制要求）"
    }
}

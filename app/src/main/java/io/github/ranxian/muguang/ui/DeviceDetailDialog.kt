package io.github.ranxian.muguang.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.utils.MoneyFormat

@Composable
fun DeviceDetailDialog(
    device: DeviceInfo?,
    isActive: Boolean = false,
    isOwner: Boolean = true,
    isBoundWasher: Boolean = false,
    /** 没绑过这台、但两个绑定名额都满了：绑定按钮换成满员说明 */
    bindFull: Boolean = false,
    /** 这台是「我的热水器」（绑定寝室后自动检测/手动设定） */
    isBoundHeater: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onBindWasher: (() -> Unit)? = null,
    onUnbindWasher: (() -> Unit)? = null,
    onBindHeater: (() -> Unit)? = null,
    onUnbindHeater: (() -> Unit)? = null
) {
    if (device == null) return

    val emoji = device.typeEmoji
    val type = when {
        device.isDrinkingWater -> if (device.isHotWater) "饮水（热水）" else "饮水（冷水）"
        device.isWasher -> "洗衣机"
        device.typeName == "洗手台" -> "洗漱"
        else -> "沐浴"
    }
    val location = device.displayName
    val startText = when {
        device.isDrinkingWater -> "开始接水"
        device.isLaundry -> "选择模式"
        else -> "开始使用"
    }
    // 蓝牙表：App 内**已支持**，走 BleShowerController。
    // 只是要多提醒一句「手机是网关、别走远」——这是这类设备固有的限制，
    // 用户不知道的话很容易开了水然后就关不上了。
    val isBle = device.needsBluetoothControl && !device.isLaundry

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 矢量图标（ic_device_*），不再画 emoji——厂商字形不可控、还自带颜色。
                    // 这里没有设备类型色的圆底，tint 必须显式给：DeviceGlyph 默认白色，
                    // 白图标压在浅色对话框上等于看不见，压成 ink 与标题同色系
                    DeviceGlyph(
                        emoji,
                        emojiSize = 22.sp,
                        iconSize = 24.dp,
                        tint = Design.palette.ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(type, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Text(location, fontSize = 15.sp, color = Design.palette.inkMuted, fontWeight = FontWeight.Medium)
            }
        },
        text = {
            Column {
                DetailRow("类型", device.typeName)
                DetailRow("SN 码", device.snCode)
                DetailRow("MAC 地址", device.macAddress)
                DetailRow("预扣金额", MoneyFormat.withSymbol(device.withholdMoney))
                DetailRow("状态", if (isActive) "使用中" else "空闲")
                if (device.isLaundry) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "洗衣机需要先选择洗涤模式和时长。模式与价格由服务端下发，" +
                            "启动后可查看洗涤、漂洗、脱水等实时阶段。",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                    // 绑定只记住设备，不要求现在下单；绑定后首页常驻「我的洗衣机」
                    // 卡片（最多两台），别人正在洗也能看到阶段与剩余时间
                    Spacer(Modifier.height(2.dp))
                    when {
                        isBoundWasher -> TextButton(onClick = { onUnbindWasher?.invoke() }) {
                            Text("已绑定为我的洗衣机 · 解除绑定", fontSize = 13.sp)
                        }
                        bindFull -> Text(
                            // 满员不弹新窗、也不放禁用态按钮——一句说明 + 引导去解绑，
                            // 比「能点但点了报错」少一步挫败
                            "已绑定 2 台洗衣机 · 解绑一台后可再绑定",
                            fontSize = 13.sp,
                            color = AppColors.TextSecondary
                        )
                        onBindWasher != null -> TextButton(onClick = { onBindWasher() }) {
                            Text("绑定为我的洗衣机", fontSize = 13.sp)
                        }
                    }
                }
                if (device.isShowerHeater) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "绑定寝室后，扫描到同寝室的这台热水器会自动记为「我的热水器」，" +
                            "首页常驻显示空闲/使用状态（状态只看服务端，没有剩余秒就不猜）。",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    when {
                        isBoundHeater -> TextButton(onClick = { onUnbindHeater?.invoke() }) {
                            Text("已检测为我的热水器 · 解除绑定", fontSize = 13.sp)
                        }
                        onBindHeater != null -> TextButton(onClick = { onBindHeater() }) {
                            Text("设为我的热水器", fontSize = 13.sp)
                        }
                    }
                }
                if (isBle) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "此设备通过手机蓝牙直连控制：开启后请留在设备旁，" +
                            "走远会断开连接、无法关闭。",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }
        },
        confirmButton = {
            val canUse = !isActive || isOwner
            val txt = when {
                isActive && !isOwner -> "他人使用中"
                isActive && device.isWasher -> "查看进行中"
                isActive -> "恢复使用"
                else -> startText
            }
            Button(onClick = { if (canUse) { onConfirm(); onDismiss() } },
                enabled = canUse,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canUse) AppColors.Accent else AppColors.TextSecondary
                )) {
                Text(txt)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

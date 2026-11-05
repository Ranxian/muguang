package io.github.ranxian.muguang.utils

import android.content.Context
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*

class MqttManager(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit,
    private val onConnected: (() -> Unit)? = null
) {
    private var mqttClient: MqttAndroidClient? = null
    private val serverUri = "tcp://47.107.37.60:1883"
    private var isConnected = false

    val connected: Boolean get() = isConnected

    fun connect(phone: String) {
        if (isConnected) {
            onConnected?.invoke()
            return
        }

        // 旧实例必须先释放：paho 的 MqttAndroidClient 会向 app context 注册
        // 内部广播接收器，直接覆盖引用 = 每次断网重连泄漏一个实例
        mqttClient?.let { old ->
            // isConnected 是平台类型，别直接判空再传 null listener：
            // disconnect() 无参重载 + close()，把 paho 内部广播接收器摘干净
            runCatching { old.disconnect() }
            runCatching { old.close() }
        }
        mqttClient = null

        val clientId = MqttClient.generateClientId()
        mqttClient = MqttAndroidClient(context, serverUri, clientId)

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = false
            isCleanSession = true
            connectionTimeout = 5
            keepAliveInterval = 10
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    isConnected = true
                    subscribe("app_downRate_$phone")
                    subscribe("app_shutdownOrder_$phone")
                    subscribe("app_uploadData_$phone")
                    onConnected?.invoke()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    isConnected = false
                    onConnected?.invoke() // still proceed — HTTP polling fallback
                }
            })

            mqttClient?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) { isConnected = false }
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.let { onMessageReceived(String(it.payload)) }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
        } catch (e: MqttException) {
            isConnected = false
            onConnected?.invoke()
        }
    }

    private fun subscribe(topic: String) {
        try { mqttClient?.subscribe(topic, 0) } catch (_: MqttException) {}
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
            isConnected = false
        } catch (_: Exception) {}
    }
}

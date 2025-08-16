// CryptaContextualMessagesManager.kt
package com.crypta.app.contextual

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.work.*
import kotlinx.coroutines.*
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.*

// مدير الرسائل السياقية الذكية
class CryptaContextualMessagesManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("crypta_contextual", Context.MODE_PRIVATE)
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val workManager: WorkManager = WorkManager.getInstance(context)
    
    private val secureRandom = SecureRandom()
    private var currentLocation: Location? = null
    private var isLocationTracking = false
    
    companion object {
        private const val CONTEXTUAL_MESSAGES_DIR = "contextual_messages"
        private const val LOCATION_UPDATE_INTERVAL = 30000L // 30 ثانية
        private const val MIN_DISTANCE_CHANGE = 50f // 50 متر
        
        // أنواع الشروط السياقية
        private const val CONDITION_TIME = "time"
        private const val CONDITION_LOCATION_EXIT = "location_exit"
        private const val CONDITION_LOCATION_ENTER = "location_enter"
        private const val CONDITION_CALENDAR_EVENT = "calendar_event"
        private const val CONDITION_WIFI_NETWORK = "wifi_network"
        private const val CONDITION_BLUETOOTH_DEVICE = "bluetooth_device"
        private const val CONDITION_BATTERY_LEVEL = "battery_level"
        private const val CONDITION_SCREEN_OFF = "screen_off"
    }
    
    init {
        setupContextualMonitoring()
        cleanupExpiredMessages()
    }
    
    // إعداد مراقبة السياق
    private fun setupContextualMonitoring() {
        try {
            // بدء مراقبة الموقع
            startLocationMonitoring()
            
            // تسجيل مستقبلات الأحداث
            registerContextualBroadcastReceivers()
            
            // جدولة فحص الرسائل السياقية
            scheduleContextualChecks()
            
            Log.d("ContextualManager", "تم إعداد مراقبة السياق")
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة السياق: ${e.message}")
        }
    }
    
    // إنشاء رسالة سياقية
    fun createContextualMessage(
        content: String,
        recipientId: String,
        conditions: List<ContextualCondition>
    ): String {
        return try {
            val messageId = UUID.randomUUID().toString()
            val timestamp = System.currentTimeMillis()
            
            // تشفير المحتوى
            val encryptionKey = generateMessageKey(messageId)
            val encryptedContent = encryptMessage(content, encryptionKey)
            
            // إنشاء الرسالة السياقية
            val contextualMessage = ContextualMessage(
                id = messageId,
                content = encryptedContent,
                senderId = "current_user", // يمكن تخصيصه
                recipientId = recipientId,
                timestamp = timestamp,
                conditions = conditions,
                isRead = false,
                isExpired = false,
                encryptionKey = Base64.encodeToString(encryptionKey, Base64.NO_WRAP)
            )
            
            // حفظ الرسالة
            saveContextualMessage(contextualMessage)
            
            // تفعيل مراقبة الشروط
            activateConditionMonitoring(contextualMessage)
            
            Log.d("ContextualManager", "تم إنشاء رسالة سياقية: $messageId")
            messageId
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إنشاء الرسالة السياقية: ${e.message}")
            ""
        }
    }
    
    // تفعيل مراقبة الشروط
    private fun activateConditionMonitoring(message: ContextualMessage) {
        message.conditions.forEach { condition ->
            when (condition.type) {
                ContextualConditionType.TIME -> {
                    scheduleTimeBasedDestruction(message, condition)
                }
                ContextualConditionType.LOCATION_EXIT -> {
                    setupLocationExitMonitoring(message, condition)
                }
                ContextualConditionType.LOCATION_ENTER -> {
                    setupLocationEnterMonitoring(message, condition)
                }
                ContextualConditionType.CALENDAR_EVENT -> {
                    setupCalendarEventMonitoring(message, condition)
                }
                ContextualConditionType.WIFI_NETWORK -> {
                    setupWiFiNetworkMonitoring(message, condition)
                }
                ContextualConditionType.BLUETOOTH_DEVICE -> {
                    setupBluetoothMonitoring(message, condition)
                }
                ContextualConditionType.BATTERY_LEVEL -> {
                    setupBatteryLevelMonitoring(message, condition)
                }
                ContextualConditionType.SCREEN_STATE -> {
                    setupScreenStateMonitoring(message, condition)
                }
            }
        }
    }
    
    // جدولة الحذف المبني على الوقت
    private fun scheduleTimeBasedDestruction(message: ContextualMessage, condition: ContextualCondition) {
        try {
            val destructionTime = when (condition.timeType) {
                TimeType.ABSOLUTE -> condition.targetTime
                TimeType.RELATIVE -> System.currentTimeMillis() + condition.duration
                TimeType.DAILY -> getNextDailyTime(condition.targetTime)
                TimeType.WEEKLY -> getNextWeeklyTime(condition.targetTime, condition.dayOfWeek)
            }
            
            val intent = Intent(context, MessageDestructionReceiver::class.java).apply {
                putExtra("message_id", message.id)
                putExtra("condition_id", condition.id)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                message.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                destructionTime,
                pendingIntent
            )
            
            Log.d("ContextualManager", "تم جدولة حذف الرسالة في: ${Date(destructionTime)}")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في جدولة الحذف الزمني: ${e.message}")
        }
    }
    
    // إعداد مراقبة مغادرة الموقع
    private fun setupLocationExitMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        if (!hasLocationPermission()) return
        
        try {
            val workData = workDataOf(
                "message_id" to message.id,
                "condition_id" to condition.id,
                "target_latitude" to condition.targetLatitude,
                "target_longitude" to condition.targetLongitude,
                "radius" to condition.radiusMeters
            )
            
            val locationWork = OneTimeWorkRequestBuilder<LocationExitWorker>()
                .setInputData(workData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            workManager.enqueue(locationWork)
            
            Log.d("ContextualManager", "تم إعداد مراقبة مغادرة الموقع للرسالة: ${message.id}")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة الموقع: ${e.message}")
        }
    }
    
    // إعداد مراقبة دخول الموقع
    private fun setupLocationEnterMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        if (!hasLocationPermission()) return
        
        try {
            val workData = workDataOf(
                "message_id" to message.id,
                "condition_id" to condition.id,
                "target_latitude" to condition.targetLatitude,
                "target_longitude" to condition.targetLongitude,
                "radius" to condition.radiusMeters
            )
            
            val locationWork = OneTimeWorkRequestBuilder<LocationEnterWorker>()
                .setInputData(workData)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            
            workManager.enqueue(locationWork)
            
            Log.d("ContextualManager", "تم إعداد مراقبة دخول الموقع للرسالة: ${message.id}")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة دخول الموقع: ${e.message}")
        }
    }
    
    // بدء مراقبة الموقع
    private fun startLocationMonitoring() {
        if (!hasLocationPermission() || isLocationTracking) return
        
        try {
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    currentLocation = location
                    checkLocationBasedConditions(location)
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_UPDATE_INTERVAL,
                MIN_DISTANCE_CHANGE,
                locationListener
            )
            
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                LOCATION_UPDATE_INTERVAL,
                MIN_DISTANCE_CHANGE,
                locationListener
            )
            
            isLocationTracking = true
            Log.d("ContextualManager", "تم بدء مراقبة الموقع")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في بدء مراقبة الموقع: ${e.message}")
        }
    }
    
    // فحص الشروط المبنية على الموقع
    private fun checkLocationBasedConditions(location: Location) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val activeMessages = getActiveContextualMessages()
                
                for (message in activeMessages) {
                    for (condition in message.conditions) {
                        when (condition.type) {
                            ContextualConditionType.LOCATION_EXIT -> {
                                checkLocationExitCondition(message, condition, location)
                            }
                            ContextualConditionType.LOCATION_ENTER -> {
                                checkLocationEnterCondition(message, condition, location)
                            }
                            else -> {} // تجاهل الأنواع الأخرى هنا
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ContextualManager", "خطأ في فحص شروط الموقع: ${e.message}")
            }
        }
    }
    
    // فحص شرط مغادرة الموقع
    private suspend fun checkLocationExitCondition(
        message: ContextualMessage,
        condition: ContextualCondition,
        currentLocation: Location
    ) {
        val targetLocation = Location("target").apply {
            latitude = condition.targetLatitude
            longitude = condition.targetLongitude
        }
        
        val distance = currentLocation.distanceTo(targetLocation)
        
        if (distance > condition.radiusMeters) {
            // المستخدم خارج المنطقة المحددة
            Log.d("ContextualManager", "تم الخروج من المنطقة المحددة - حذف الرسالة: ${message.id}")
            destroyContextualMessage(message.id, "تم الخروج من الموقع المحدد")
        }
    }
    
    // فحص شرط دخول الموقع
    private suspend fun checkLocationEnterCondition(
        message: ContextualMessage,
        condition: ContextualCondition,
        currentLocation: Location
    ) {
        val targetLocation = Location("target").apply {
            latitude = condition.targetLatitude
            longitude = condition.targetLongitude
        }
        
        val distance = currentLocation.distanceTo(targetLocation)
        
        if (distance <= condition.radiusMeters) {
            // المستخدم داخل المنطقة المحددة
            Log.d("ContextualManager", "تم الدخول للمنطقة المحددة - حذف الرسالة: ${message.id}")
            destroyContextualMessage(message.id, "تم الدخول للموقع المحدد")
        }
    }
    
    // إعداد مراقبة أحداث التقويم
    private fun setupCalendarEventMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        try {
            val workData = workDataOf(
                "message_id" to message.id,
                "condition_id" to condition.id,
                "event_title" to condition.calendarEventTitle,
                "check_type" to condition.calendarCheckType.name
            )
            
            val calendarWork = PeriodicWorkRequestBuilder<CalendarEventWorker>(15, TimeUnit.MINUTES)
                .setInputData(workData)
                .build()
            
            workManager.enqueue(calendarWork)
            
            Log.d("ContextualManager", "تم إعداد مراقبة أحداث التقويم")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة التقويم: ${e.message}")
        }
    }
    
    // إعداد مراقبة شبكة WiFi
    private fun setupWiFiNetworkMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        try {
            val workData = workDataOf(
                "message_id" to message.id,
                "condition_id" to condition.id,
                "wifi_ssid" to condition.wifiSSID,
                "action_type" to condition.wifiActionType.name
            )
            
            val wifiWork = PeriodicWorkRequestBuilder<WiFiMonitorWorker>(5, TimeUnit.MINUTES)
                .setInputData(workData)
                .build()
            
            workManager.enqueue(wifiWork)
            
            Log.d("ContextualManager", "تم إعداد مراقبة WiFi")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة WiFi: ${e.message}")
        }
    }
    
    // إعداد مراقبة Bluetooth
    private fun setupBluetoothMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        try {
            val workData = workDataOf(
                "message_id" to message.id,
                "condition_id" to condition.id,
                "device_address" to condition.bluetoothDeviceAddress,
                "action_type" to condition.bluetoothActionType.name
            )
            
            val bluetoothWork = PeriodicWorkRequestBuilder<BluetoothMonitorWorker>(2, TimeUnit.MINUTES)
                .setInputData(workData)
                .build()
            
            workManager.enqueue(bluetoothWork)
            
            Log.d("ContextualManager", "تم إعداد مراقبة Bluetooth")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة Bluetooth: ${e.message}")
        }
    }
    
    // إعداد مراقبة مستوى البطارية
    private fun setupBatteryLevelMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        try {
            val workData = workDataOf(
                "message_id" to message.id,
                "condition_id" to condition.id,
                "battery_threshold" to condition.batteryThreshold,
                "comparison_type" to condition.batteryComparisonType.name
            )
            
            val batteryWork = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(10, TimeUnit.MINUTES)
                .setInputData(workData)
                .build()
            
            workManager.enqueue(batteryWork)
            
            Log.d("ContextualManager", "تم إعداد مراقبة البطارية")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة البطارية: ${e.message}")
        }
    }
    
    // إعداد مراقبة حالة الشاشة
    private fun setupScreenStateMonitoring(message: ContextualMessage, condition: ContextualCondition) {
        try {
            context.registerReceiver(
                ScreenStateReceiver(message.id, condition.id),
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
            )
            
            Log.d("ContextualManager", "تم إعداد مراقبة حالة الشاشة")
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إعداد مراقبة الشاشة: ${e.message}")
        }
    }
    
    // حذف الرسالة السياقية
    suspend fun destroyContextualMessage(messageId: String, reason: String) {
        withContext(Dispatchers.IO) {
            try {
                val messageFile = File(context.filesDir, "$CONTEXTUAL_MESSAGES_DIR/$messageId.crypta")
                if (messageFile.exists()) {
                    // حذف آمن - الكتابة العشوائية فوق الملف
                    secureDeleteFile(messageFile)
                    
                    // تسجيل عملية الحذف
                    logMessageDestruction(messageId, reason)
                    
                    // إلغاء جميع المراقبات المرتبطة
                    cancelConditionMonitoring(messageId)
                    
                    Log.d("ContextualManager", "تم حذف الرسالة السياقية: $messageId - السبب: $reason")
                }
            } catch (e: Exception) {
                Log.e("ContextualManager", "خطأ في حذف الرسالة: ${e.message}")
            }
        }
    }
    
    // حذف آمن للملف
    private fun secureDeleteFile(file: File) {
        try {
            val fileSize = file.length()
            val randomData = ByteArray(fileSize.toInt())
            
            // الكتابة العشوائية 3 مرات
            repeat(3) {
                secureRandom.nextBytes(randomData)
                file.writeBytes(randomData)
            }
            
            // حذف الملف
            file.delete()
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في الحذف الآمن: ${e.message}")
            file.delete() // حذف عادي في حالة الفشل
        }
    }
    
    // تسجيل عملية الحذف
    private fun logMessageDestruction(messageId: String, reason: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] تم حذف الرسالة: $messageId - السبب: $reason\n"
            
            val logFile = File(context.filesDir, "contextual_destruction_log.txt")
            logFile.appendText(logEntry)
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في تسجيل الحذف: ${e.message}")
        }
    }
    
    // إلغاء مراقبة الشروط
    private fun cancelConditionMonitoring(messageId: String) {
        try {
            // إلغاء الإنذارات
            val intent = Intent(context, MessageDestructionReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                messageId.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
            
            // إلغاء مهام WorkManager
            workManager.cancelAllWorkByTag(messageId)
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في إلغاء المراقبة: ${e.message}")
        }
    }
    
    // الحصول على الرسائل السياقية النشطة
    private fun getActiveContextualMessages(): List<ContextualMessage> {
        return try {
            val messagesDir = File(context.filesDir, CONTEXTUAL_MESSAGES_DIR)
            if (!messagesDir.exists()) return emptyList()
            
            val messages = mutableListOf<ContextualMessage>()
            
            messagesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".crypta")) {
                    try {
                        val message = loadContextualMessage(file.nameWithoutExtension)
                        if (message != null && !message.isExpired) {
                            messages.add(message)
                        }
                    } catch (e: Exception) {
                        Log.e("ContextualManager", "خطأ في قراءة رسالة: ${file.name}")
                    }
                }
            }
            
            messages
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في تحميل الرسائل النشطة: ${e.message}")
            emptyList()
        }
    }
    
    // حفظ الرسالة السياقية
    private fun saveContextualMessage(message: ContextualMessage) {
        try {
            val messagesDir = File(context.filesDir, CONTEXTUAL_MESSAGES_DIR)
            if (!messagesDir.exists()) {
                messagesDir.mkdirs()
            }
            
            val messageFile = File(messagesDir, "${message.id}.crypta")
            val json = contextualMessageToJson(message)
            messageFile.writeText(json)
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في حفظ الرسالة: ${e.message}")
        }
    }
    
    // تحميل الرسالة السياقية
    private fun loadContextualMessage(messageId: String): ContextualMessage? {
        return try {
            val messageFile = File(context.filesDir, "$CONTEXTUAL_MESSAGES_DIR/$messageId.crypta")
            if (!messageFile.exists()) return null
            
            val json = messageFile.readText()
            parseContextualMessage(json)
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في تحميل الرسالة: ${e.message}")
            null
        }
    }
    
    // تشفير الرسالة
    private fun encryptMessage(message: String, key: ByteArray): String {
        return try {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            
            val keySpec = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val encryptedData = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في تشفير الرسالة: ${e.message}")
            message
        }
    }
    
    // فك تشفير الرسالة
    fun decryptMessage(encryptedMessage: String, key: ByteArray): String {
        return try {
            val combined = Base64.decode(encryptedMessage, Base64.NO_WRAP)
            
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            
            val encryptedData = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, encryptedData, 0, encryptedData.size)
            
            val keySpec = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            val decryptedData = cipher.doFinal(encryptedData)
            String(decryptedData, StandardCharsets.UTF_8)
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في فك التشفير: ${e.message}")
            "[رسالة مشفرة - لا يمكن فك التشفير]"
        }
    }
    
    // توليد مفتاح للرسالة
    private fun generateMessageKey(messageId: String): ByteArray {
        return try {
            val info = "CryptaContextualMessage-$messageId-${System.currentTimeMillis()}".toByteArray()
            val ikm = ByteArray(32)
            secureRandom.nextBytes(ikm)
            
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(ikm, null, info))
            
            val key = ByteArray(32)
            hkdf.generateBytes(key, 0, 32)
            key
            
        } catch (e: Exception) {
            Log.e("ContextualManager", "خطأ في توليد المفتاح: ${e.message}")
            "CryptaDefaultKey123456789012345678".toByteArray()
        }
    }
    
    // فحص الأذونات
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // ت
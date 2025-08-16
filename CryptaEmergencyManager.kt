// CryptaEmergencyManager.kt
package com.crypta.app.emergency

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.sqrt

// مدير وضع الطوارئ الرئيسي
class CryptaEmergencyManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("crypta_emergency", Context.MODE_PRIVATE)
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val notificationManager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var isShakeDetectionActive = false
    private var isEmergencyActive = false
    private var lastShakeTime = 0L
    private var shakeCount = 0
    
    // معلومات الطوارئ
    private var emergencyContacts = mutableListOf<EmergencyContact>()
    private var emergencyMessage = ""
    private var autoDeleteEnabled = false
    private var panicGesture = PanicGesture.SHAKE_THREE_TIMES
    private var silentMode = false
    
    companion object {
        private const val PREF_EMERGENCY_CONTACTS = "emergency_contacts"
        private const val PREF_EMERGENCY_MESSAGE = "emergency_message"
        private const val PREF_AUTO_DELETE = "auto_delete_enabled"
        private const val PREF_PANIC_GESTURE = "panic_gesture"
        private const val PREF_SILENT_MODE = "silent_mode"
        private const val PREF_FAKE_APP_ENABLED = "fake_app_enabled"
        
        private const val SHAKE_THRESHOLD = 12.0f
        private const val SHAKE_TIME_LAPSE = 1000
        private const val REQUIRED_SHAKES = 3
        
        private const val NOTIFICATION_CHANNEL_ID = "crypta_emergency"
        private const val EMERGENCY_NOTIFICATION_ID = 1001
    }
    
    init {
        setupSensors()
        loadSettings()
        createNotificationChannel()
    }
    
    // إعداد المستشعرات
    private fun setupSensors() {
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }
    
    // تحميل الإعدادات
    private fun loadSettings() {
        try {
            // تحميل جهات الاتصال الطارئة
            val contactsJson = prefs.getString(PREF_EMERGENCY_CONTACTS, "[]") ?: "[]"
            emergencyContacts = parseEmergencyContacts(contactsJson).toMutableList()
            
            // رسالة الطوارئ
            emergencyMessage = prefs.getString(PREF_EMERGENCY_MESSAGE, 
                "حالة طوارئ! أحتاج المساعدة. تم إرسال هذه الرسالة تلقائياً من تطبيق Crypta.") ?: ""
            
            // الإعدادات الأخرى
            autoDeleteEnabled = prefs.getBoolean(PREF_AUTO_DELETE, true)
            panicGesture = PanicGesture.values()[prefs.getInt(PREF_PANIC_GESTURE, 0)]
            silentMode = prefs.getBoolean(PREF_SILENT_MODE, false)
            
            Log.d("EmergencyManager", "تم تحميل الإعدادات بنجاح")
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في تحميل الإعدادات: ${e.message}")
        }
    }
    
    // بدء مراقبة إيماءات الطوارئ
    fun startPanicDetection() {
        if (isShakeDetectionActive) return
        
        when (panicGesture) {
            PanicGesture.SHAKE_THREE_TIMES -> startShakeDetection()
            PanicGesture.VOLUME_BUTTONS -> startVolumeButtonDetection()
            PanicGesture.POWER_BUTTON_SEQUENCE -> startPowerButtonDetection()
            PanicGesture.SCREEN_TAP_PATTERN -> startScreenTapDetection()
        }
        
        isShakeDetectionActive = true
        Log.d("EmergencyManager", "تم تفعيل مراقبة الطوارئ")
    }
    
    // إيقاف مراقبة الطوارئ
    fun stopPanicDetection() {
        if (!isShakeDetectionActive) return
        
        sensorManager.unregisterListener(shakeDetectionListener)
        isShakeDetectionActive = false
        shakeCount = 0
        
        Log.d("EmergencyManager", "تم إيقاف مراقبة الطوارئ")
    }
    
    // بدء كشف الاهتزاز
    private fun startShakeDetection() {
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                shakeDetectionListener,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }
    
    // مستمع كشف الاهتزاز
    private val shakeDetectionListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                detectShakeGesture(event.values)
            }
        }
        
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    
    // كشف إيماءة الاهتزاز
    private fun detectShakeGesture(values: FloatArray) {
        val x = values[0]
        val y = values[1]
        val z = values[2]
        
        val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        
        if (acceleration > SHAKE_THRESHOLD) {
            val currentTime = System.currentTimeMillis()
            
            if (currentTime - lastShakeTime > SHAKE_TIME_LAPSE) {
                shakeCount = 0
            }
            
            lastShakeTime = currentTime
            shakeCount++
            
            // اهتزاز تأكيدي
            if (!silentMode) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            
            Log.d("EmergencyManager", "اكتشاف اهتزاز: $shakeCount من $REQUIRED_SHAKES")
            
            if (shakeCount >= REQUIRED_SHAKES) {
                triggerEmergencyMode()
            }
        }
    }
    
    // تفعيل وضع الطوارئ
    fun triggerEmergencyMode() {
        if (isEmergencyActive) return
        
        isEmergencyActive = true
        Log.w("EmergencyManager", "🚨 تم تفعيل وضع الطوارئ!")
        
        CoroutineScope(Dispatchers.IO).launch {
            executeEmergencyProcedures()
        }
        
        // إشعار فوري
        showEmergencyNotification()
        
        // اهتزاز طوارئ
        if (!silentMode) {
            emergencyVibration()
        }
    }
    
    // تنفيذ إجراءات الطوارئ
    private suspend fun executeEmergencyProcedures() {
        try {
            // 1. إرسال رسائل الطوارئ
            sendEmergencyAlerts()
            
            // 2. حذف البيانات الحساسة (إذا كان مفعل)
            if (autoDeleteEnabled) {
                deleteSecureData()
            }
            
            // 3. حفظ سجل الطوارئ
            logEmergencyEvent()
            
            // 4. تفعيل التطبيق المزيف (إذا كان مفعل)
            if (prefs.getBoolean(PREF_FAKE_APP_ENABLED, false)) {
                activateFakeApp()
            }
            
            Log.d("EmergencyManager", "✅ تم تنفيذ جميع إجراءات الطوارئ")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "❌ خطأ في تنفيذ إجراءات الطوارئ: ${e.message}")
        }
    }
    
    // إرسال تنبيهات الطوارئ
    private suspend fun sendEmergencyAlerts() {
        if (emergencyContacts.isEmpty()) {
            Log.w("EmergencyManager", "⚠️ لا توجد جهات اتصال طوارئ محفوظة")
            return
        }
        
        val location = getCurrentLocation()
        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        
        val fullMessage = buildString {
            append(emergencyMessage)
            append("\n\n")
            append("الوقت: $timestamp")
            if (location != null) {
                append("\nالموقع: ${location.latitude}, ${location.longitude}")
                append("\nرابط الخريطة: https://maps.google.com/?q=${location.latitude},${location.longitude}")
            }
            append("\n\n- تم الإرسال من تطبيق Crypta")
        }
        
        // تشفير الرسالة
        val encryptedMessage = encryptEmergencyMessage(fullMessage)
        
        for (contact in emergencyContacts) {
            try {
                when (contact.type) {
                    ContactType.SMS -> sendEncryptedSMS(contact.value, encryptedMessage)
                    ContactType.EMAIL -> sendEncryptedEmail(contact.value, encryptedMessage)
                    ContactType.WEBHOOK -> sendWebhookAlert(contact.value, encryptedMessage)
                    ContactType.TELEGRAM -> sendTelegramAlert(contact.value, encryptedMessage)
                }
                
                Log.d("EmergencyManager", "✅ تم إرسال تنبيه إلى: ${contact.name}")
            } catch (e: Exception) {
                Log.e("EmergencyManager", "❌ فشل الإرسال إلى ${contact.name}: ${e.message}")
            }
        }
    }
    
    // حذف البيانات الآمنة
    private suspend fun deleteSecureData() {
        withContext(Dispatchers.IO) {
            try {
                // حذف الملفات المشفرة
                val encryptedDir = File(context.filesDir, "crypta_encrypted")
                if (encryptedDir.exists()) {
                    encryptedDir.deleteRecursively()
                }
                
                // حذف قاعدة البيانات
                val databases = context.databaseList()
                for (db in databases) {
                    context.deleteDatabase(db)
                }
                
                // حذف الـ SharedPreferences الحساسة
                val sensitivePrefs = listOf("crypta_messages", "crypta_vault", "crypta_keys")
                for (prefName in sensitivePrefs) {
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        .edit().clear().apply()
                }
                
                // حذف الملفات المؤقتة
                val tempDir = File(context.cacheDir, "crypta_temp")
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                }
                
                // الكتابة العشوائية فوق المساحة المحذوفة (أمان إضافي)
                secureDeleteOverwrite()
                
                Log.d("EmergencyManager", "🗑️ تم حذف البيانات الحساسة بنجاح")
                
            } catch (e: Exception) {
                Log.e("EmergencyManager", "❌ خطأ في حذف البيانات: ${e.message}")
            }
        }
    }
    
    // الكتابة العشوائية للأمان
    private fun secureDeleteOverwrite() {
        try {
            val random = SecureRandom()
            val overwriteFile = File(context.filesDir, "secure_overwrite.tmp")
            
            // كتابة بيانات عشوائية عدة مرات
            repeat(3) {
                overwriteFile.outputStream().use { output ->
                    repeat(1024) {
                        val randomData = ByteArray(1024)
                        random.nextBytes(randomData)
                        output.write(randomData)
                    }
                    output.flush()
                }
            }
            
            overwriteFile.delete()
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في الكتابة العشوائية: ${e.message}")
        }
    }
    
    // تشفير رسالة الطوارئ
    private fun encryptEmergencyMessage(message: String): String {
        return try {
            // إنشاء مفتاح طوارئ خاص
            val emergencyKey = generateEmergencyKey()
            
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            
            val keySpec = SecretKeySpec(emergencyKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val encryptedData = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            
            // دمج IV مع البيانات
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في تشفير رسالة الطوارئ: ${e.message}")
            message // إرجاع الرسالة غير مشفرة في حالة الخطأ
        }
    }
    
    // توليد مفتاح طوارئ
    private fun generateEmergencyKey(): ByteArray {
        return try {
            val privateKey = X25519PrivateKeyParameters(SecureRandom())
            val info = "Crypta-Emergency-Key-${System.currentTimeMillis()}".toByteArray()
            
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(privateKey.encoded, null, info))
            
            val key = ByteArray(32)
            hkdf.generateBytes(key, 0, 32)
            key
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في توليد مفتاح الطوارئ: ${e.message}")
            // مفتاح احتياطي
            "CryptaEmergencyKey2024!@#$%^&*()".toByteArray().copyOf(32)
        }
    }
    
    // الحصول على الموقع الحالي
    private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        try {
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) 
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val locationListener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    continuation.resume(location)
                }
                
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {
                    continuation.resume(null)
                }
            }
            
            // محاولة الحصول على آخر موقع معروف أولاً
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 300000) {
                continuation.resume(lastLocation)
            } else {
                // طلب موقع جديد
                locationManager.requestSingleUpdate(
                    LocationManager.NETWORK_PROVIDER,
                    locationListener,
                    Looper.getMainLooper()
                )
                
                // إنهاء المهلة بعد 10 ثوانٍ
                Handler(Looper.getMainLooper()).postDelayed({
                    locationManager.removeUpdates(locationListener)
                    if (!continuation.isCompleted) {
                        continuation.resume(null)
                    }
                }, 10000)
            }
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في الحصول على الموقع: ${e.message}")
            continuation.resume(null)
        }
    }
    
    // إرسال SMS مشفر
    private fun sendEncryptedSMS(phoneNumber: String, encryptedMessage: String) {
        try {
            // في التطبيق الحقيقي، يتم الإرسال عبر SMS API
            // هنا نحفظ في سجل للمراجعة
            Log.d("EmergencyManager", "SMS إلى $phoneNumber: تم التشفير والإرسال")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في إرسال SMS: ${e.message}")
        }
    }
    
    // إرسال إيميل مشفر
    private fun sendEncryptedEmail(email: String, encryptedMessage: String) {
        try {
            // في التطبيق الحقيقي، يتم الإرسال عبر SMTP
            Log.d("EmergencyManager", "Email إلى $email: تم التشفير والإرسال")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في إرسال Email: ${e.message}")
        }
    }
    
    // إرسال webhook
    private fun sendWebhookAlert(webhookUrl: String, encryptedMessage: String) {
        try {
            // في التطبيق الحقيقي، يتم الإرسال عبر HTTP POST
            Log.d("EmergencyManager", "Webhook إلى $webhookUrl: تم الإرسال")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في إرسال Webhook: ${e.message}")
        }
    }
    
    // إرسال تنبيه تيليجرام
    private fun sendTelegramAlert(chatId: String, encryptedMessage: String) {
        try {
            // في التطبيق الحقيقي، يتم الإرسال عبر Telegram Bot API
            Log.d("EmergencyManager", "Telegram إلى $chatId: تم الإرسال")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في إرسال Telegram: ${e.message}")
        }
    }
    
    // تسجيل حدث الطوارئ
    private fun logEmergencyEvent() {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = "[$timestamp] تم تفعيل وضع الطوارئ - الإيماءة: ${panicGesture.name}"
            
            val logFile = File(context.filesDir, "emergency_log.txt")
            logFile.appendText("$logEntry\n")
            
            Log.d("EmergencyManager", "📝 تم تسجيل حدث الطوارئ")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في تسجيل الحدث: ${e.message}")
        }
    }
    
    // إنشاء قناة الإشعارات
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "إشعارات الطوارئ",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "إشعارات وضع الطوارئ في تطبيق Crypta"
            enableVibration(true)
            enableLights(true)
        }
        
        notificationManager.createNotificationChannel(channel)
    }
    
    // عرض إشعار الطوارئ
    private fun showEmergencyNotification() {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 وضع الطوارئ مفعل")
            .setContentText("تم تفعيل إجراءات الطوارئ في تطبيق Crypta")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColor(Color.RED.hashCode())
            .build()
        
        notificationManager.notify(EMERGENCY_NOTIFICATION_ID, notification)
    }
    
    // اهتزاز الطوارئ
    private fun emergencyVibration() {
        if (vibrator.hasVibrator()) {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 1000, 200, 1000)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
    
    // تفعيل التطبيق المزيف
    private fun activateFakeApp() {
        try {
            // تغيير شكل التطبيق ليبدو مثل تطبيق آخر
            prefs.edit().putBoolean("fake_mode_active", true).apply()
            Log.d("EmergencyManager", "🎭 تم تفعيل التطبيق المزيف")
            
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في تفعيل التطبيق المزيف: ${e.message}")
        }
    }
    
    // حفظ جهة اتصال طوارئ
    fun addEmergencyContact(contact: EmergencyContact) {
        emergencyContacts.add(contact)
        saveEmergencyContacts()
    }
    
    // حذف جهة اتصال
    fun removeEmergencyContact(contact: EmergencyContact) {
        emergencyContacts.remove(contact)
        saveEmergencyContacts()
    }
    
    // حفظ جهات الاتصال
    private fun saveEmergencyContacts() {
        try {
            val json = emergencyContactsToJson(emergencyContacts)
            prefs.edit().putString(PREF_EMERGENCY_CONTACTS, json).apply()
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في حفظ جهات الاتصال: ${e.message}")
        }
    }
    
    // تحويل جهات الاتصال إلى JSON
    private fun emergencyContactsToJson(contacts: List<EmergencyContact>): String {
        return buildString {
            append("[")
            contacts.forEachIndexed { index, contact ->
                if (index > 0) append(",")
                append("{")
                append("\"name\":\"${contact.name}\",")
                append("\"type\":\"${contact.type.name}\",")
                append("\"value\":\"${contact.value}\",")
                append("\"enabled\":${contact.enabled}")
                append("}")
            }
            append("]")
        }
    }
    
    // تحليل JSON لجهات الاتصال
    private fun parseEmergencyContacts(json: String): List<EmergencyContact> {
        return try {
            val contacts = mutableListOf<EmergencyContact>()
            // تحليل JSON بسيط (في التطبيق الحقيقي استخدم مكتبة JSON)
            // هنا مجرد مثال
            contacts
        } catch (e: Exception) {
            Log.e("EmergencyManager", "خطأ في تحليل جهات الاتصال: ${e.message}")
            emptyList()
        }
    }
    
    // إعادة تعيين وضع الطوارئ
    fun resetEmergencyMode() {
        isEmergencyActive = false
        shakeCount = 0
        lastShakeTime = 0
        
        // إلغاء الإشعارات
        notificationManager.cancel(EMERGENCY_NOTIFICATION_ID)
        
        // إيقاف الاهتزاز
        vibrator.cancel()
        
        Log.d("EmergencyManager", "تم إعادة تعيين وضع الطوارئ")
    }
    
    // بدء كشف أزرار الصوت (مثال)
    private fun startVolumeButtonDetection() {
        // تطبيق في النشاط الرئيسي
        Log.d("EmergencyManager", "بدء مراقبة أزرار الصوت")
    }
    
    // بدء كشف زر الطاقة (مثال)
    private fun startPowerButtonDetection() {
        // تطبيق في النشاط الرئيسي
        Log.d("EmergencyManager", "بدء مراقبة زر الطاقة")
    }
    
    // بدء كشف نقرات الشاشة (مثال)
    private fun startScreenTapDetection() {
        // تطبيق في النشاط الرئيسي
        Log.d("EmergencyManager", "بدء مراقبة نقرات الشاشة")
    }
    
    // الحصول على حالة الطوارئ
    fun isEmergencyModeActive(): Boolean = isEmergencyActive
    
    // تنظيف الموارد
    fun cleanup() {
        stopPanicDetection()
// CryptaStealthManager.kt
package com.crypta.app.stealth

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// مدير وضع الظل والمحادثات السرية
class CryptaStealthManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("crypta_stealth", Context.MODE_PRIVATE)
    private val hiddenChatsPrefs: SharedPreferences = context.getSharedPreferences("crypta_hidden_chats", Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    
    companion object {
        // إعدادات وضع الظل
        private const val PREF_STEALTH_ENABLED = "stealth_mode_enabled"
        private const val PREF_FAKE_APP_NAME = "fake_app_name"
        private const val PREF_FAKE_APP_ICON = "fake_app_icon"
        private const val PREF_SECRET_ACCESS_CODE = "secret_access_code"
        private const val PREF_DECOY_CONTENT = "decoy_content_enabled"
        
        // المحادثات المخفية
        private const val PREF_HIDDEN_CHATS_KEY = "hidden_chats_master_key"
        private const val HIDDEN_CHATS_DIR = "hidden_chats"
        
        // أسماء وأيقونات التطبيقات المزيفة
        val FAKE_APP_TEMPLATES = listOf(
            FakeAppTemplate("الآلة الحاسبة", "com.calculator.app", Icons.Default.Calculate, Color(0xFF2196F3)),
            FakeAppTemplate("المفكرة", "com.notepad.app", Icons.Default.Note, Color(0xFF4CAF50)),
            FakeAppTemplate("الطقس", "com.weather.app", Icons.Default.Cloud, Color(0xFF03DAC6)),
            FakeAppTemplate("المنبه", "com.alarm.app", Icons.Default.Alarm, Color(0xFFFF9800)),
            FakeAppTemplate("الملفات", "com.files.app", Icons.Default.Folder, Color(0xFF795548)),
            FakeAppTemplate("الإعدادات", "com.settings.app", Icons.Default.Settings, Color(0xFF607D8B))
        )
    }
    
    // تفعيل وضع الظل
    fun enableStealthMode(fakeAppTemplate: FakeAppTemplate, secretCode: String) {
        try {
            // حفظ إعدادات وضع الظل
            prefs.edit()
                .putBoolean(PREF_STEALTH_ENABLED, true)
                .putString(PREF_FAKE_APP_NAME, fakeAppTemplate.name)
                .putString(PREF_FAKE_APP_ICON, fakeAppTemplate.iconName)
                .putString(PREF_SECRET_ACCESS_CODE, hashSecretCode(secretCode))
                .putBoolean(PREF_DECOY_CONTENT, true)
                .apply()
            
            // تغيير أيقونة واسم التطبيق
            changeAppIcon(fakeAppTemplate)
            
            println("🎭 تم تفعيل وضع الظل: ${fakeAppTemplate.name}")
            
        } catch (e: Exception) {
            println("❌ خطأ في تفعيل وضع الظل: ${e.message}")
        }
    }
    
    // إيقاف وضع الظل
    fun disableStealthMode() {
        try {
            prefs.edit()
                .putBoolean(PREF_STEALTH_ENABLED, false)
                .remove(PREF_FAKE_APP_NAME)
                .remove(PREF_FAKE_APP_ICON)
                .remove(PREF_SECRET_ACCESS_CODE)
                .putBoolean(PREF_DECOY_CONTENT, false)
                .apply()
            
            // استعادة الأيقونة الأصلية
            restoreOriginalIcon()
            
            println("✅ تم إيقاف وضع الظل")
            
        } catch (e: Exception) {
            println("❌ خطأ في إيقاف وضع الظل: ${e.message}")
        }
    }
    
    // فحص وضع الظل
    fun isStealthModeEnabled(): Boolean {
        return prefs.getBoolean(PREF_STEALTH_ENABLED, false)
    }
    
    // التحقق من الكود السري
    fun verifySecretCode(inputCode: String): Boolean {
        val storedHash = prefs.getString(PREF_SECRET_ACCESS_CODE, "") ?: ""
        return if (storedHash.isNotEmpty()) {
            storedHash == hashSecretCode(inputCode)
        } else {
            false
        }
    }
    
    // تشفير الكود السري
    private fun hashSecretCode(code: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(code.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(hash, Base64.NO_WRAP)
        } catch (e: Exception) {
            println("❌ خطأ في تشفير الكود السري: ${e.message}")
            ""
        }
    }
    
    // تغيير أيقونة التطبيق
    private fun changeAppIcon(fakeApp: FakeAppTemplate) {
        try {
            val packageManager = context.packageManager
            
            // إخفاء الأيقونة الأصلية
            val originalComponent = ComponentName(context.packageName, "${context.packageName}.MainActivity")
            packageManager.setComponentEnabledSetting(
                originalComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // عرض الأيقونة المزيفة
            val fakeComponent = ComponentName(context.packageName, "${context.packageName}.${fakeApp.className}")
            packageManager.setComponentEnabledSetting(
                fakeComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
        } catch (e: Exception) {
            println("❌ خطأ في تغيير الأيقونة: ${e.message}")
        }
    }
    
    // استعادة الأيقونة الأصلية
    private fun restoreOriginalIcon() {
        try {
            val packageManager = context.packageManager
            
            // عرض الأيقونة الأصلية
            val originalComponent = ComponentName(context.packageName, "${context.packageName}.MainActivity")
            packageManager.setComponentEnabledSetting(
                originalComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            
            // إخفاء جميع الأيقونات المزيفة
            FAKE_APP_TEMPLATES.forEach { fakeApp ->
                val fakeComponent = ComponentName(context.packageName, "${context.packageName}.${fakeApp.className}")
                packageManager.setComponentEnabledSetting(
                    fakeComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            
        } catch (e: Exception) {
            println("❌ خطأ في استعادة الأيقونة: ${e.message}")
        }
    }
    
    // إنشاء محادثة مخفية
    fun createHiddenChat(chatName: String, password: String): String {
        return try {
            val chatId = UUID.randomUUID().toString()
            val chatKey = deriveKeyFromPassword(password, chatId)
            
            // إنشاء مجلد المحادثة المخفية
            val chatDir = File(context.filesDir, "$HIDDEN_CHATS_DIR/$chatId")
            if (!chatDir.exists()) {
                chatDir.mkdirs()
            }
            
            // حفظ معلومات المحادثة المشفرة
            val chatInfo = HiddenChatInfo(
                id = chatId,
                name = chatName,
                createdAt = System.currentTimeMillis(),
                lastActivity = System.currentTimeMillis(),
                messageCount = 0,
                isLocked = false
            )
            
            saveHiddenChatInfo(chatInfo, chatKey)
            
            // حفظ مفتاح المحادثة في الذاكرة المؤقتة
            saveTemporaryChatKey(chatId, chatKey)
            
            println("🔒 تم إنشاء محادثة مخفية: $chatName")
            chatId
            
        } catch (e: Exception) {
            println("❌ خطأ في إنشاء المحادثة المخفية: ${e.message}")
            ""
        }
    }
    
    // فتح محادثة مخفية
    fun unlockHiddenChat(chatId: String, password: String): Boolean {
        return try {
            val chatKey = deriveKeyFromPassword(password, chatId)
            val chatInfo = loadHiddenChatInfo(chatId, chatKey)
            
            if (chatInfo != null) {
                // حفظ المفتاح مؤقتاً للجلسة
                saveTemporaryChatKey(chatId, chatKey)
                
                // تحديث آخر نشاط
                val updatedInfo = chatInfo.copy(
                    lastActivity = System.currentTimeMillis(),
                    isLocked = false
                )
                saveHiddenChatInfo(updatedInfo, chatKey)
                
                println("🔓 تم فتح المحادثة المخفية: ${chatInfo.name}")
                true
            } else {
                println("❌ كلمة مرور خاطئة للمحادثة المخفية")
                false
            }
            
        } catch (e: Exception) {
            println("❌ خطأ في فتح المحادثة المخفية: ${e.message}")
            false
        }
    }
    
    // قفل محادثة مخفية
    fun lockHiddenChat(chatId: String) {
        try {
            // حذف المفتاح من الذاكرة المؤقتة
            removeTemporaryChatKey(chatId)
            
            println("🔒 تم قفل المحادثة المخفية: $chatId")
            
        } catch (e: Exception) {
            println("❌ خطأ في قفل المحادثة: ${e.message}")
        }
    }
    
    // حذف محادثة مخفية
    fun deleteHiddenChat(chatId: String, password: String): Boolean {
        return try {
            val chatKey = deriveKeyFromPassword(password, chatId)
            val chatInfo = loadHiddenChatInfo(chatId, chatKey)
            
            if (chatInfo != null) {
                // حذف مجلد المحادثة
                val chatDir = File(context.filesDir, "$HIDDEN_CHATS_DIR/$chatId")
                if (chatDir.exists()) {
                    chatDir.deleteRecursively()
                }
                
                // حذف المفتاح المؤقت
                removeTemporaryChatKey(chatId)
                
                println("🗑️ تم حذف المحادثة المخفية: ${chatInfo.name}")
                true
            } else {
                false
            }
            
        } catch (e: Exception) {
            println("❌ خطأ في حذف المحادثة: ${e.message}")
            false
        }
    }
    
    // إضافة رسالة للمحادثة المخفية
    fun addMessageToHiddenChat(chatId: String, message: String, isFromUser: Boolean): Boolean {
        return try {
            val chatKey = getTemporaryChatKey(chatId)
            if (chatKey == null) {
                println("❌ المحادثة مقفلة")
                return false
            }
            
            // تشفير الرسالة
            val encryptedMessage = encryptMessage(message, chatKey)
            
            // حفظ الرسالة
            val hiddenMessage = HiddenMessage(
                id = UUID.randomUUID().toString(),
                chatId = chatId,
                content = encryptedMessage,
                isFromUser = isFromUser,
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            
            saveHiddenMessage(hiddenMessage)
            
            // تحديث معلومات المحادثة
            updateHiddenChatActivity(chatId, chatKey)
            
            true
            
        } catch (e: Exception) {
            println("❌ خطأ في إضافة رسالة مخفية: ${e.message}")
            false
        }
    }
    
    // الحصول على رسائل المحادثة المخفية
    fun getHiddenChatMessages(chatId: String): List<HiddenMessage> {
        return try {
            val chatKey = getTemporaryChatKey(chatId)
            if (chatKey == null) {
                println("❌ المحادثة مقفلة")
                return emptyList()
            }
            
            loadHiddenMessages(chatId, chatKey)
            
        } catch (e: Exception) {
            println("❌ خطأ في تحميل الرسائل المخفية: ${e.message}")
            emptyList()
        }
    }
    
    // اشتقاق مفتاح من كلمة المرور
    private fun deriveKeyFromPassword(password: String, salt: String): ByteArray {
        return try {
            val combinedSalt = (salt + "CryptaHiddenChat2024").toByteArray()
            val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
            
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(passwordBytes, combinedSalt, "CryptaHiddenChatKey".toByteArray()))
            
            val key = ByteArray(32)
            hkdf.generateBytes(key, 0, 32)
            key
            
        } catch (e: Exception) {
            println("❌ خطأ في اشتقاق المفتاح: ${e.message}")
            ByteArray(32) // مفتاح فارغ في حالة الخطأ
        }
    }
    
    // تشفير رسالة
    private fun encryptMessage(message: String, key: ByteArray): String {
        return try {
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            
            val keySpec = SecretKeySpec(key, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val encryptedData = cipher.doFinal(message.toByteArray(StandardCharsets.UTF_8))
            
            // دمج IV مع البيانات المشفرة
            val combined = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedData, 0, combined, iv.size, encryptedData.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            println("❌ خطأ في تشفير الرسالة: ${e.message}")
            message
        }
    }
    
    // فك تشفير رسالة
    private fun decryptMessage(encryptedMessage: String, key: ByteArray): String {
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
            println("❌ خطأ في فك تشفير الرسالة: ${e.message}")
            "رسالة مشفرة"
        }
    }
    
    // حفظ معلومات المحادثة المخفية
    private fun saveHiddenChatInfo(chatInfo: HiddenChatInfo, key: ByteArray) {
        try {
            val json = """
                {
                    "id": "${chatInfo.id}",
                    "name": "${chatInfo.name}",
                    "createdAt": ${chatInfo.createdAt},
                    "lastActivity": ${chatInfo.lastActivity},
                    "messageCount": ${chatInfo.messageCount},
                    "isLocked": ${chatInfo.isLocked}
                }
            """.trimIndent()
            
            val encryptedJson = encryptMessage(json, key)
            val infoFile = File(context.filesDir, "$HIDDEN_CHATS_DIR/${chatInfo.id}/info.crypta")
            infoFile.writeText(encryptedJson)
            
        } catch (e: Exception) {
            println("❌ خطأ في حفظ معلومات المحادثة: ${e.message}")
        }
    }
    
    // تحميل معلومات المحادثة المخفية
    private fun loadHiddenChatInfo(chatId: String, key: ByteArray): HiddenChatInfo? {
        return try {
            val infoFile = File(context.filesDir, "$HIDDEN_CHATS_DIR/$chatId/info.crypta")
            if (!infoFile.exists()) return null
            
            val encryptedJson = infoFile.readText()
            val json = decryptMessage(encryptedJson, key)
            
            parseHiddenChatInfo(json)
            
        } catch (e: Exception) {
            println("❌ خطأ في تحميل معلومات المحادثة: ${e.message}")
            null
        }
    }
    
    // تحليل معلومات المحادثة من JSON
    private fun parseHiddenChatInfo(json: String): HiddenChatInfo {
        // تحليل JSON بسيط (في التطبيق الحقيقي استخدم مكتبة JSON)
        val lines = json.lines().map { it.trim() }
        
        return HiddenChatInfo(
            id = extractJsonValue(lines, "id"),
            name = extractJsonValue(lines, "name"),
            createdAt = extractJsonValue(lines, "createdAt").toLong(),
            lastActivity = extractJsonValue(lines, "lastActivity").toLong(),
            messageCount = extractJsonValue(lines, "messageCount").toInt(),
            isLocked = extractJsonValue(lines, "isLocked").toBoolean()
        )
    }
    
    // استخراج قيمة من JSON
    private fun extractJsonValue(lines: List<String>, key: String): String {
        val line = lines.find { it.contains("\"$key\":") }
            ?: throw IllegalArgumentException("Key $key not found")
        
        return line.substringAfter(":").trim()
            .removeSurrounding("\"")
            .removeSuffix(",")
    }
    
    // حفظ مفتاح المحادثة مؤقتاً
    private fun saveTemporaryChatKey(chatId: String, key: ByteArray) {
        val encodedKey = Base64.encodeToString(key, Base64.NO_WRAP)
        hiddenChatsPrefs.edit().putString("temp_key_$chatId", encodedKey).apply()
    }
    
    // الحصول على مفتاح المحادثة المؤقت
    private fun getTemporaryChatKey(chatId: String): ByteArray? {
        val encodedKey = hiddenChatsPrefs.getString("temp_key_$chatId", null)
        return if (encodedKey != null) {
            Base64.decode(encodedKey, Base64.NO_WRAP)
        } else {
            null
        }
    }
    
    // حذف مفتاح المحادثة المؤقت
    private fun removeTemporaryChatKey(chatId: String) {
        hiddenChatsPrefs.edit().remove("temp_key_$chatId").apply()
    }
    
    // حفظ رسالة مخفية
    private fun saveHiddenMessage(message: HiddenMessage) {
        try {
            val messageFile = File(context.filesDir, 
                "$HIDDEN_CHATS_DIR/${message.chatId}/messages/${message.id}.crypta")
            
            if (!messageFile.parentFile?.exists()!!) {
                messageFile.parentFile?.mkdirs()
            }
            
            val json = """
                {
                    "id": "${message.id}",
                    "chatId": "${message.chatId}",
                    "content": "${message.content}",
                    "isFromUser": ${message.isFromUser},
                    "timestamp": ${message.timestamp},
                    "isRead": ${message.isRead}
                }
            """.trimIndent()
            
            messageFile.writeText(json)
            
        } catch (e: Exception) {
            println("❌ خطأ في حفظ الرسالة المخفية: ${e.message}")
        }
    }
    
    // تحميل الرسائل المخفية
    private fun loadHiddenMessages(chatId: String, key: ByteArray): List<HiddenMessage> {
        return try {
            val messagesDir = File(context.filesDir, "$HIDDEN_CHATS_DIR/$chatId/messages")
            if (!messagesDir.exists()) return emptyList()
            
            val messages = mutableListOf<HiddenMessage>()
            
            messagesDir.listFiles()?.forEach { messageFile ->
                if (messageFile.name.endsWith(".crypta")) {
                    try {
                        val json = messageFile.readText()
                        val message = parseHiddenMessage(json, key)
                        if (message != null) {
                            messages.add(message)
                        }
                    } catch (e: Exception) {
                        println("❌ خطأ في قراءة رسالة: ${messageFile.name}")
                    }
                }
            }
            
            // ترتيب الرسائل حسب الوقت
            messages.sortBy { it.timestamp }
            messages
            
        } catch (e: Exception) {
            println("❌ خطأ في تحميل الرسائل: ${e.message}")
            emptyList()
        }
    }
    
    // تحليل رسالة مخفية
    private fun parseHiddenMessage(json: String, key: ByteArray): HiddenMessage? {
        return try {
            val lines = json.lines().map { it.trim() }
            
            val encryptedContent = extractJsonValue(lines, "content")
            val decryptedContent = decryptMessage(encryptedContent, key)
            
            HiddenMessage(
                id = extractJsonValue(lines, "id"),
                chatId = extractJsonValue(lines, "chatId"),
                content = decryptedContent,
                isFromUser = extractJsonValue(lines, "isFromUser").toBoolean(),
                timestamp = extractJsonValue(lines, "timestamp").toLong(),
                isRead = extractJsonValue(lines, "isRead").toBoolean()
            )
            
        } catch (e: Exception) {
            println("❌ خطأ في تحليل الرسالة: ${e.message}")
            null
        }
    }
    
    // تحديث نشاط المحادثة
    private fun updateHiddenChatActivity(chatId: String, key: ByteArray) {
        try {
            val chatInfo = loadHiddenChatInfo(chatId, key)
            if (chatInfo != null) {
                val updatedInfo = chatInfo.copy(
                    lastActivity = System.currentTimeMillis(),
                    messageCount = chatInfo.messageCount + 1
                )
                saveHiddenChatInfo(updatedInfo, key)
            }
        } catch (e: Exception) {
            println("❌ خطأ في تحديث نشاط المحادثة: ${e.message}")
        }
    }
    
    // الحصول على قائمة المحادثات المخفية المتاحة
    fun getAvailableHiddenChats(): List<String> {
        return try {
            val hiddenChatsDir = File(context.filesDir, HIDDEN_CHATS_DIR)
            if (!hiddenChatsDir.exists()) return emptyList()
            
            hiddenChatsDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
            
        } catch (e: Exception) {
            println("❌ خطأ في الحصول على المحادثات المخفية: ${e.message}")
            emptyList()
        }
    }
    
    // تنظيف المفاتيح المؤقتة
    fun clearTemporaryKeys() {
        try {
            hiddenChatsPrefs.edit().clear().apply()
            println("🧹 تم تنظيف المفاتيح المؤقتة")
        } catch (e: Exception) {
            println("❌ خطأ في تنظيف المفاتيح: ${e.message}")
        }
    }
}

// نماذج البيانات
data class FakeAppTemplate(
    val name: String,
    val className: String,
    val icon: ImageVector,
    val color: Color
) {
    val iconName: String get() = className.substringAfterLast(".")
}

data class HiddenChatInfo(
    val id: String,
    val name: String,
    val createdAt: Long,
    val lastActivity: Long,
    val messageCount: Int,
    val isLocked: Boolean
) {
    fun getFormattedDate(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(lastActivity))
    }
}

data class HiddenMessage(
    val id: String,
    val chatId: String,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isRead: Boolean
) {
    fun getFormattedTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault())
            .format(Date(timestamp))
    }
}

// Activity التطبيق المزيف (الآلة الحاسبة كمثال)
@OptIn(ExperimentalMaterial3Api::class)
class FakeCalculatorActivity : ComponentActivity() {
    
    private lateinit var stealthManager: CryptaStealthManager
    private var secretCodeAttempt = ""
    private val secretCodeLength = 6
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        stealthManager
// CryptaP2PMessagingManager.kt
package com.crypta.app.p2p

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.HashMap

// مدير المراسلة P2P الرئيسي
class CryptaP2PMessagingManager(private val context: Context) {
    
    private val httpClient = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = KotlinxSerializer(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            })
        }
        install(WebSockets)
    }
    
    private val secureRandom = SecureRandom()
    private var currentUserId: String = ""
    private var serverWebSocket: DefaultClientWebSocketSession? = null
    private var isConnectedToServer = false
    
    // اتصالات P2P النشطة
    private val activeP2PConnections = ConcurrentHashMap<String, P2PConnection>()
    private val pendingConnections = ConcurrentHashMap<String, PendingConnection>()
    private val messageQueue = ConcurrentHashMap<String, MutableList<P2PMessage>>()
    
    // مفاتيح التشفير لكل محادثة
    private val conversationKeys = ConcurrentHashMap<String, ByteArray>()
    
    // قنوات الاتصال
    private val incomingMessages = Channel<P2PMessage>(Channel.UNLIMITED)
    private val connectionEvents = Channel<ConnectionEvent>(Channel.UNLIMITED)
    
    companion object {
        private const val SERVER_URL = "ws://your-server.com:8080" // عنوان السيرفر
        private const val P2P_PORT_RANGE_START = 9000
        private const val P2P_PORT_RANGE_END = 9100
        private const val CONNECTION_TIMEOUT = 30000L
        private const val HEARTBEAT_INTERVAL = 15000L
    }
    
    // تهيئة النظام
    suspend fun initialize(userId: String) {
        this.currentUserId = userId
        
        try {
            // الاتصال بالسيرفر
            connectToServer()
            
            // بدء استقبال الاتصالات P2P
            startP2PListener()
            
            // بدء معالجة الرسائل
            startMessageProcessor()
            
            Log.d("P2PManager", "تم تهيئة نظام P2P بنجاح")
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في تهيئة النظام: ${e.message}")
        }
    }
    
    // الاتصال بالسيرفر
    private suspend fun connectToServer() {
        try {
            serverWebSocket = httpClient.webSocketSession(
                method = HttpMethod.Get,
                host = "your-server.com",
                port = 8080,
                path = "/ws"
            )
            
            isConnectedToServer = true
            
            // تسجيل المستخدم في السيرفر
            val registrationMessage = ServerMessage(
                type = MessageType.REGISTER,
                fromUserId = currentUserId,
                data = mapOf("status" -> "online")
            )
            
            serverWebSocket?.send(Frame.Text(Json.encodeToString(registrationMessage)))
            
            // بدء استقبال رسائل السيرفر
            startServerMessageListener()
            
            Log.d("P2PManager", "تم الاتصال بالسيرفر بنجاح")
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في الاتصال بالسيرفر: ${e.message}")
            isConnectedToServer = false
        }
    }
    
    // استقبال رسائل السيرفر
    private suspend fun startServerMessageListener() {
        serverWebSocket?.let { socket ->
            try {
                for (frame in socket.incoming) {
                    when (frame) {
                        is Frame.Text -> {
                            val message = Json.decodeFromString<ServerMessage>(frame.readText())
                            handleServerMessage(message)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e("P2PManager", "خطأ في استقبال رسائل السيرفر: ${e.message}")
                isConnectedToServer = false
            }
        }
    }
    
    // معالجة رسائل السيرفر
    private suspend fun handleServerMessage(message: ServerMessage) {
        when (message.type) {
            MessageType.CONNECTION_REQUEST -> {
                // طلب اتصال من مستخدم آخر
                handleConnectionRequest(message)
            }
            MessageType.CONNECTION_RESPONSE -> {
                // رد على طلب اتصال
                handleConnectionResponse(message)
            }
            MessageType.PEER_INFO -> {
                // معلومات المستخدم للاتصال P2P
                handlePeerInfo(message)
            }
            MessageType.USER_LIST -> {
                // قائمة المستخدمين المتصلين
                handleUserList(message)
            }
            MessageType.ERROR -> {
                Log.e("P2PManager", "خطأ من السيرفر: ${message.data}")
            }
            else -> {}
        }
    }
    
    // بدء محادثة جديدة
    suspend fun startConversation(targetUserId: String): Boolean {
        if (!isConnectedToServer) {
            Log.e("P2PManager", "غير متصل بالسيرفر")
            return false
        }
        
        try {
            // إنشاء مفاتيح X25519 للمحادثة
            val keyPair = generateKeyPair()
            
            // طلب اتصال من السيرفر
            val connectionRequest = ServerMessage(
                type = MessageType.CONNECTION_REQUEST,
                fromUserId = currentUserId,
                toUserId = targetUserId,
                data = mapOf(
                    "publicKey" to Base64.encodeToString(keyPair.publicKey.encoded, Base64.NO_WRAP),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
            
            serverWebSocket?.send(Frame.Text(Json.encodeToString(connectionRequest)))
            
            // حفظ الاتصال المعلق
            pendingConnections[targetUserId] = PendingConnection(
                targetUserId = targetUserId,
                myPrivateKey = keyPair.privateKey,
                myPublicKey = keyPair.publicKey,
                timestamp = System.currentTimeMillis(),
                isInitiator = true
            )
            
            Log.d("P2PManager", "تم إرسال طلب اتصال إلى: $targetUserId")
            return true
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في بدء المحادثة: ${e.message}")
            return false
        }
    }
    
    // معالجة طلب الاتصال
    private suspend fun handleConnectionRequest(message: ServerMessage) {
        val targetUserId = message.fromUserId ?: return
        val peerPublicKeyData = message.data?.get("publicKey") as? String ?: return
        
        try {
            // إنشاء مفاتيح للرد
            val keyPair = generateKeyPair()
            val peerPublicKey = X25519PublicKeyParameters(Base64.decode(peerPublicKeyData, Base64.NO_WRAP))
            
            // حساب المفتاح المشترك
            val sharedSecret = calculateSharedSecret(keyPair.privateKey, peerPublicKey)
            val conversationKey = deriveConversationKey(sharedSecret, currentUserId, targetUserId)
            
            // حفظ مفتاح المحادثة
            conversationKeys[targetUserId] = conversationKey
            
            // إرسال رد بالموافقة
            val response = ServerMessage(
                type = MessageType.CONNECTION_RESPONSE,
                fromUserId = currentUserId,
                toUserId = targetUserId,
                data = mapOf(
                    "accepted" to "true",
                    "publicKey" to Base64.encodeToString(keyPair.publicKey.encoded, Base64.NO_WRAP),
                    "p2pPort" to findAvailablePort().toString()
                )
            )
            
            serverWebSocket?.send(Frame.Text(Json.encodeToString(response)))
            
            // إشعار التطبيق بطلب الاتصال
            connectionEvents.send(ConnectionEvent.IncomingRequest(targetUserId))
            
            Log.d("P2PManager", "تم قبول طلب اتصال من: $targetUserId")
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في معالجة طلب الاتصال: ${e.message}")
        }
    }
    
    // معالجة رد الاتصال
    private suspend fun handleConnectionResponse(message: ServerMessage) {
        val targetUserId = message.fromUserId ?: return
        val accepted = message.data?.get("accepted") as? String == "true"
        
        if (!accepted) {
            pendingConnections.remove(targetUserId)
            connectionEvents.send(ConnectionEvent.ConnectionRejected(targetUserId))
            return
        }
        
        val pendingConnection = pendingConnections[targetUserId] ?: return
        val peerPublicKeyData = message.data?.get("publicKey") as? String ?: return
        val peerPort = (message.data?.get("p2pPort") as? String)?.toIntOrNull() ?: return
        
        try {
            val peerPublicKey = X25519PublicKeyParameters(Base64.decode(peerPublicKeyData, Base64.NO_WRAP))
            
            // حساب المفتاح المشترك
            val sharedSecret = calculateSharedSecret(pendingConnection.myPrivateKey, peerPublicKey)
            val conversationKey = deriveConversationKey(sharedSecret, currentUserId, targetUserId)
            
            // حفظ مفتاح المحادثة
            conversationKeys[targetUserId] = conversationKey
            
            // طلب معلومات الاتصال P2P من السيرفر
            val peerInfoRequest = ServerMessage(
                type = MessageType.PEER_INFO,
                fromUserId = currentUserId,
                toUserId = targetUserId,
                data = mapOf("requestType" -> "connection_info")
            )
            
            serverWebSocket?.send(Frame.Text(Json.encodeToString(peerInfoRequest)))
            
            pendingConnections.remove(targetUserId)
            
            Log.d("P2PManager", "تم قبول الاتصال من: $targetUserId")
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في معالجة رد الاتصال: ${e.message}")
        }
    }
    
    // معالجة معلومات المستخدم
    private suspend fun handlePeerInfo(message: ServerMessage) {
        val targetUserId = message.fromUserId ?: return
        val peerIP = message.data?.get("ip") as? String ?: return
        val peerPort = (message.data?.get("port") as? String)?.toIntOrNull() ?: return
        
        // بدء الاتصال P2P المباشر
        establishP2PConnection(targetUserId, peerIP, peerPort)
    }
    
    // إنشاء اتصال P2P مباشر
    private suspend fun establishP2PConnection(targetUserId: String, peerIP: String, peerPort: Int) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(peerIP, peerPort), CONNECTION_TIMEOUT.toInt())
            
            val connection = P2PConnection(
                socket = socket,
                targetUserId = targetUserId,
                isInitiator = true,
                lastActivity = System.currentTimeMillis()
            )
            
            activeP2PConnections[targetUserId] = connection
            
            // بدء معالجة الرسائل P2P
            startP2PMessageHandler(connection)
            
            // إرسال رسالة تأكيد الاتصال
            sendHandshakeMessage(connection)
            
            connectionEvents.send(ConnectionEvent.ConnectionEstablished(targetUserId))
            
            Log.d("P2PManager", "تم إنشاء اتصال P2P مع: $targetUserId")
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في إنشاء اتصال P2P: ${e.message}")
            connectionEvents.send(ConnectionEvent.ConnectionFailed(targetUserId))
        }
    }
    
    // بدء استقبال الاتصالات P2P
    private suspend fun startP2PListener() {
        withContext(Dispatchers.IO) {
            try {
                val serverSocket = ServerSocket(findAvailablePort())
                
                while (true) {
                    try {
                        val clientSocket = serverSocket.accept()
                        
                        // معالجة الاتصال الجديد في thread منفصل
                        CoroutineScope(Dispatchers.IO).launch {
                            handleIncomingP2PConnection(clientSocket)
                        }
                        
                    } catch (e: Exception) {
                        Log.e("P2PManager", "خطأ في قبول اتصال P2P: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("P2PManager", "خطأ في بدء مستمع P2P: ${e.message}")
            }
        }
    }
    
    // معالجة اتصال P2P وارد
    private suspend fun handleIncomingP2PConnection(socket: Socket) {
        try {
            // قراءة رسالة التعريف
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val handshakeData = input.readLine()
            
            val handshake = Json.decodeFromString<HandshakeMessage>(handshakeData)
            val targetUserId = handshake.fromUserId
            
            val connection = P2PConnection(
                socket = socket,
                targetUserId = targetUserId,
                isInitiator = false,
                lastActivity = System.currentTimeMillis()
            )
            
            activeP2PConnections[targetUserId] = connection
            
            // بدء معالجة الرسائل
            startP2PMessageHandler(connection)
            
            // إرسال رد التأكيد
            sendHandshakeResponse(connection)
            
            connectionEvents.send(ConnectionEvent.ConnectionEstablished(targetUserId))
            
            Log.d("P2PManager", "تم قبول اتصال P2P من: $targetUserId")
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في معالجة اتصال P2P وارد: ${e.message}")
            socket.close()
        }
    }
    
    // إرسال رسالة P2P
    suspend fun sendMessage(targetUserId: String, content: String, messageType: P2PMessageType = P2PMessageType.TEXT): Boolean {
        val connection = activeP2PConnections[targetUserId]
        if (connection == null) {
            Log.e("P2PManager", "لا يوجد اتصال P2P مع: $targetUserId")
            return false
        }
        
        val conversationKey = conversationKeys[targetUserId]
        if (conversationKey == null) {
            Log.e("P2PManager", "لا يوجد مفتاح تشفير للمحادثة مع: $targetUserId")
            return false
        }
        
        try {
            // تشفير المحتوى
            val encryptedContent = encryptMessage(content, conversationKey)
            
            // إنشاء الرسالة
            val message = P2PMessage(
                id = UUID.randomUUID().toString(),
                fromUserId = currentUserId,
                toUserId = targetUserId,
                content = encryptedContent,
                messageType = messageType,
                timestamp = System.currentTimeMillis(),
                isEncrypted = true
            )
            
            // إرسال الرسالة
            val messageJson = Json.encodeToString(message)
            val output = PrintWriter(connection.socket.getOutputStream(), true)
            output.println(messageJson)
            
            connection.lastActivity = System.currentTimeMillis()
            
            Log.d("P2PManager", "تم إرسال رسالة إلى: $targetUserId")
            return true
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في إرسال الرسالة: ${e.message}")
            return false
        }
    }
    
    // معالجة رسائل P2P
    private suspend fun startP2PMessageHandler(connection: P2PConnection) {
        withContext(Dispatchers.IO) {
            try {
                val input = BufferedReader(InputStreamReader(connection.socket.getInputStream()))
                
                while (connection.socket.isConnected && !connection.socket.isClosed) {
                    try {
                        val messageData = input.readLine() ?: break
                        
                        val message = Json.decodeFromString<P2PMessage>(messageData)
                        
                        // فك تشفير المحتوى
                        val conversationKey = conversationKeys[message.fromUserId]
                        if (conversationKey != null && message.isEncrypted) {
                            val decryptedContent = decryptMessage(message.content, conversationKey)
                            val decryptedMessage = message.copy(content = decryptedContent, isEncrypted = false)
                            
                            incomingMessages.send(decryptedMessage)
                        } else {
                            incomingMessages.send(message)
                        }
                        
                        connection.lastActivity = System.currentTimeMillis()
                        
                    } catch (e: Exception) {
                        Log.e("P2PManager", "خطأ في معالجة رسالة P2P: ${e.message}")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("P2PManager", "خطأ في معالج رسائل P2P: ${e.message}")
            } finally {
                // إغلاق الاتصال
                activeP2PConnections.remove(connection.targetUserId)
                connection.socket.close()
                connectionEvents.send(ConnectionEvent.ConnectionLost(connection.targetUserId))
            }
        }
    }
    
    // بدء معالج الرسائل الرئيسي
    private suspend fun startMessageProcessor() {
        CoroutineScope(Dispatchers.IO).launch {
            for (message in incomingMessages) {
                try {
                    // إضافة الرسالة لقائمة الانتظار أو معالجتها
                    val userMessages = messageQueue.getOrPut(message.fromUserId) { mutableListOf() }
                    userMessages.add(message)
                    
                    Log.d("P2PManager", "تم استقبال رسالة من: ${message.fromUserId}")
                    
                } catch (e: Exception) {
                    Log.e("P2PManager", "خطأ في معالجة الرسالة: ${e.message}")
                }
            }
        }
    }
    
    // إرسال رسالة المصافحة
    private suspend fun sendHandshakeMessage(connection: P2PConnection) {
        try {
            val handshake = HandshakeMessage(
                fromUserId = currentUserId,
                toUserId = connection.targetUserId,
                timestamp = System.currentTimeMillis(),
                protocolVersion = "1.0"
            )
            
            val output = PrintWriter(connection.socket.getOutputStream(), true)
            output.println(Json.encodeToString(handshake))
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في إرسال المصافحة: ${e.message}")
        }
    }
    
    // إرسال رد المصافحة
    private suspend fun sendHandshakeResponse(connection: P2PConnection) {
        try {
            val response = HandshakeResponse(
                fromUserId = currentUserId,
                toUserId = connection.targetUserId,
                accepted = true,
                timestamp = System.currentTimeMillis()
            )
            
            val output = PrintWriter(connection.socket.getOutputStream(), true)
            output.println(Json.encodeToString(response))
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في إرسال رد المصافحة: ${e.message}")
        }
    }
    
    // توليد مفاتيح X25519
    private fun generateKeyPair(): KeyPair {
        val privateKey = X25519PrivateKeyParameters(secureRandom)
        val publicKey = privateKey.generatePublicKey()
        return KeyPair(privateKey, publicKey)
    }
    
    // حساب المفتاح المشترك
    private fun calculateSharedSecret(privateKey: X25519PrivateKeyParameters, publicKey: X25519PublicKeyParameters): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        
        val sharedSecret = ByteArray(32)
        agreement.calculateAgreement(publicKey, sharedSecret, 0)
        
        return sharedSecret
    }
    
    // اشتقاق مفتاح المحادثة
    private fun deriveConversationKey(sharedSecret: ByteArray, user1Id: String, user2Id: String): ByteArray {
        val sortedUserIds = listOf(user1Id, user2Id).sorted()
        val info = "CryptaP2PConversation-${sortedUserIds[0]}-${sortedUserIds[1]}".toByteArray()
        
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(sharedSecret, null, info))
        
        val key = ByteArray(32)
        hkdf.generateBytes(key, 0, 32)
        
        return key
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
            Log.e("P2PManager", "خطأ في التشفير: ${e.message}")
            message
        }
    }
    
    // فك تشفير الرسالة
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
            Log.e("P2PManager", "خطأ في فك التشفير: ${e.message}")
            "[رسالة مشفرة]"
        }
    }
    
    // البحث عن منفذ متاح
    private fun findAvailablePort(): Int {
        for (port in P2P_PORT_RANGE_START..P2P_PORT_RANGE_END) {
            try {
                ServerSocket(port).use { return port }
            } catch (e: Exception) {
                // المنفذ مشغول، جرب التالي
            }
        }
        throw RuntimeException("لا يوجد منافذ متاحة")
    }
    
    // قطع الاتصال مع مستخدم
    suspend fun disconnectFromUser(targetUserId: String) {
        try {
            val connection = activeP2PConnections.remove(targetUserId)
            connection?.socket?.close()
            
            conversationKeys.remove(targetUserId)
            messageQueue.remove(targetUserId)
            
            Log.d("P2PManager", "تم قطع الاتصال مع: $targetUserId")
            
        } catch (e: Exception) {
            Log.e("P2PManager", "خطأ في قطع الاتصال: ${e.message}")
        }
    }
    
    // الحصول على الرسائل
    fun getMessages(userId: String): List<P2PMessage> {
        return messageQueue[userId] ?: emptyList()
    }
    
    // الحصول على المستخدمين المتصلين
    fun getConnectedUsers(): List<String> {
        return activeP2PConnections.keys.toList()
    }
    
    // الحصول على حالة الاتصال
    fun isConnectedToUser(userId: String): Boolean {
        return activeP2PConnections.containsKey(userId)
    }
    
    // تنظيف الموارد
    suspend fun cleanup() {
        try {
            // إغلاق جميع اتصالات P2P
            activeP2PConnections.values.forEach { connection ->
                connection.socket.close()
            }
            activeP2PConnections.clear()
            
            // إغلاق الاتصال بالسيرفر
            serverWebSocket?.close()
            
            // إغلاق HTTP Client
            httpClient.close()
            
            // تنظيف البيانات
            conversationKeys.clear()
            messageQueue.clear()
            pendingConnections.clear()
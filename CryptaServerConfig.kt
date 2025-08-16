// CryptaServerConfig.kt
package com.crypta.app.network

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
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
import java.util.concurrent.atomic.AtomicBoolean

// إعدادات السيرفر
object CryptaServerConfig {
    
    // عنوان السيرفر - غير هذا حسب عنوان سيرفرك
    const val SERVER_HOST = "your-server.com"  // أو IP address مثل "192.168.1.100"
    const val SERVER_PORT = 8080
    const val SERVER_WS_PATH = "/ws"
    
    // للتطوير المحلي
    const val LOCAL_SERVER_HOST = "10.0.2.2"  // للمحاكي Android
    // const val LOCAL_SERVER_HOST = "192.168.1.xxx"  // للجهاز الحقيقي
    
    // URLs كاملة
    val SERVER_WS_URL = "ws://$SERVER_HOST:$SERVER_PORT$SERVER_WS_PATH"
    val LOCAL_WS_URL = "ws://$LOCAL_SERVER_HOST:$SERVER_PORT$SERVER_WS_PATH"
    
    // إعدادات الاتصال
    const val CONNECTION_TIMEOUT = 30000L
    const val HEARTBEAT_INTERVAL = 15000L
    const val RECONNECT_DELAY = 5000L
    const val MAX_RECONNECT_ATTEMPTS = 5
}

// مدير الاتصال بالسيرفر
class CryptaServerConnection(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("crypta_server", Context.MODE_PRIVATE)
    private var httpClient: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var currentUserId: String = ""
    private var isConnected = AtomicBoolean(false)
    private var reconnectAttempts = 0
    
    // قنوات التواصل
    private val messageChannel = Channel<ServerMessage>(Channel.UNLIMITED)
    private val connectionEvents = Channel<ConnectionStatus>(Channel.UNLIMITED)
    
    // إعداد HTTP Client
    private fun setupHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            
            install(WebSockets) {
                pingInterval = CryptaServerConfig.HEARTBEAT_INTERVAL
                maxFrameSize = Long.MAX_VALUE
            }
            
            install(HttpTimeout) {
                requestTimeoutMillis = CryptaServerConfig.CONNECTION_TIMEOUT
                connectTimeoutMillis = CryptaServerConfig.CONNECTION_TIMEOUT
                socketTimeoutMillis = CryptaServerConfig.CONNECTION_TIMEOUT
            }
            
            // إعداد Headers مخصصة
            install(DefaultRequest) {
                header("User-Agent", "Crypta-Android-Client/1.0")
                header("X-Client-Type", "mobile")
            }
        }
    }
    
    // بدء الاتصال
    suspend fun connect(userId: String): Boolean {
        this.currentUserId = userId
        
        if (!isNetworkAvailable()) {
            Log.e("ServerConnection", "لا يوجد اتصال بالإنترنت")
            connectionEvents.send(ConnectionStatus.NoInternet)
            return false
        }
        
        return withContext(Dispatchers.IO) {
            try {
                httpClient = setupHttpClient()
                
                val serverUrl = if (isLocalDevelopment()) {
                    CryptaServerConfig.LOCAL_WS_URL
                } else {
                    CryptaServerConfig.SERVER_WS_URL
                }
                
                Log.d("ServerConnection", "محاولة الاتصال بـ: $serverUrl")
                
                webSocketSession = httpClient?.webSocketSession(
                    method = HttpMethod.Get,
                    host = if (isLocalDevelopment()) CryptaServerConfig.LOCAL_SERVER_HOST else CryptaServerConfig.SERVER_HOST,
                    port = CryptaServerConfig.SERVER_PORT,
                    path = CryptaServerConfig.SERVER_WS_PATH
                ) {
                    // Headers إضافية
                    headers {
                        append("X-User-ID", userId)
                        append("X-Client-Version", "1.0")
                        append("X-Platform", "Android")
                    }
                }
                
                if (webSocketSession != null) {
                    isConnected.set(true)
                    reconnectAttempts = 0
                    
                    // بدء معالجة الرسائل
                    startMessageListener()
                    
                    // بدء heartbeat
                    startHeartbeat()
                    
                    // تسجيل المستخدم
                    registerUser(userId)
                    
                    connectionEvents.send(ConnectionStatus.Connected)
                    
                    Log.d("ServerConnection", "✅ تم الاتصال بالسيرفر بنجاح")
                    true
                } else {
                    Log.e("ServerConnection", "❌ فشل في إنشاء WebSocket session")
                    false
                }
                
            } catch (e: Exception) {
                Log.e("ServerConnection", "❌ خطأ في الاتصال: ${e.message}")
                connectionEvents.send(ConnectionStatus.Failed(e.message ?: "خطأ غير معروف"))
                
                // محاولة إعادة الاتصال
                scheduleReconnect()
                false
            }
        }
    }
    
    // فحص إذا كان التطوير محلي
    private fun isLocalDevelopment(): Boolean {
        return prefs.getBoolean("use_local_server", true) // للتطوير
    }
    
    // فحص الاتصال بالإنترنت
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
               capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
    
    // تسجيل المستخدم في السيرفر
    private suspend fun registerUser(userId: String) {
        try {
            val registrationMessage = ServerMessage(
                type = MessageType.REGISTER,
                fromUserId = userId,
                toUserId = null,
                data = mapOf(
                    "platform" to "Android",
                    "version" to "1.0",
                    "capabilities" to listOf("p2p", "file_transfer", "voice_call"),
                    "timestamp" to System.currentTimeMillis().toString()
                )
            )
            
            sendMessage(registrationMessage)
            Log.d("ServerConnection", "تم تسجيل المستخدم: $userId")
            
        } catch (e: Exception) {
            Log.e("ServerConnection", "خطأ في تسجيل المستخدم: ${e.message}")
        }
    }
    
    // بدء معالج الرسائل
    private suspend fun startMessageListener() {
        webSocketSession?.let { session ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    for (frame in session.incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val messageText = frame.readText()
                                Log.d("ServerConnection", "رسالة واردة: $messageText")
                                
                                try {
                                    val message = Json.decodeFromString<ServerMessage>(messageText)
                                    messageChannel.send(message)
                                } catch (e: Exception) {
                                    Log.e("ServerConnection", "خطأ في تحليل الرسالة: ${e.message}")
                                }
                            }
                            is Frame.Close -> {
                                Log.w("ServerConnection", "تم إغلاق الاتصال من السيرفر")
                                handleDisconnection()
                                break
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ServerConnection", "خطأ في معالج الرسائل: ${e.message}")
                    handleDisconnection()
                }
            }
        }
    }
    
    // بدء نبضات الحياة
    private suspend fun startHeartbeat() {
        CoroutineScope(Dispatchers.IO).launch {
            while (isConnected.get()) {
                try {
                    delay(CryptaServerConfig.HEARTBEAT_INTERVAL)
                    
                    val heartbeat = ServerMessage(
                        type = MessageType.HEARTBEAT,
                        fromUserId = currentUserId,
                        data = mapOf("timestamp" to System.currentTimeMillis().toString())
                    )
                    
                    sendMessage(heartbeat)
                    
                } catch (e: Exception) {
                    Log.e("ServerConnection", "خطأ في heartbeat: ${e.message}")
                    break
                }
            }
        }
    }
    
    // إرسال رسالة للسيرفر
    suspend fun sendMessage(message: ServerMessage): Boolean {
        return try {
            if (!isConnected.get()) {
                Log.w("ServerConnection", "غير متصل بالسيرفر")
                return false
            }
            
            val messageJson = Json.encodeToString(message)
            webSocketSession?.send(Frame.Text(messageJson))
            
            Log.d("ServerConnection", "تم إرسال رسالة: ${message.type}")
            true
            
        } catch (e: Exception) {
            Log.e("ServerConnection", "خطأ في إرسال الرسالة: ${e.message}")
            false
        }
    }
    
    // معالجة انقطاع الاتصال
    private suspend fun handleDisconnection() {
        isConnected.set(false)
        connectionEvents.send(ConnectionStatus.Disconnected)
        
        // محاولة إعادة الاتصال
        scheduleReconnect()
    }
    
    // جدولة إعادة الاتصال
    private suspend fun scheduleReconnect() {
        if (reconnectAttempts < CryptaServerConfig.MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++
            
            Log.d("ServerConnection", "محاولة إعادة الاتصال #$reconnectAttempts")
            connectionEvents.send(ConnectionStatus.Reconnecting(reconnectAttempts))
            
            delay(CryptaServerConfig.RECONNECT_DELAY * reconnectAttempts)
            
            if (connect(currentUserId)) {
                Log.d("ServerConnection", "✅ تمت إعادة الاتصال بنجاح")
            } else {
                scheduleReconnect()
            }
        } else {
            Log.e("ServerConnection", "❌ فشل في إعادة الاتصال بعد $reconnectAttempts محاولات")
            connectionEvents.send(ConnectionStatus.PermanentFailure)
        }
    }
    
    // استقبال الرسائل
    suspend fun receiveMessage(): ServerMessage {
        return messageChannel.receive()
    }
    
    // استقبال أحداث الاتصال
    suspend fun receiveConnectionEvent(): ConnectionStatus {
        return connectionEvents.receive()
    }
    
    // فحص حالة الاتصال
    fun isConnectedToServer(): Boolean {
        return isConnected.get()
    }
    
    // قطع الاتصال
    suspend fun disconnect() {
        try {
            isConnected.set(false)
            
            // إرسال رسالة إلغاء التسجيل
            val unregisterMessage = ServerMessage(
                type = MessageType.UNREGISTER,
                fromUserId = currentUserId,
                data = mapOf("reason" to "user_disconnect")
            )
            sendMessage(unregisterMessage)
            
            // إغلاق الاتصالات
            webSocketSession?.close()
            httpClient?.close()
            
            connectionEvents.send(ConnectionStatus.Disconnected)
            Log.d("ServerConnection", "تم قطع الاتصال بالسيرفر")
            
        } catch (e: Exception) {
            Log.e("ServerConnection", "خطأ في قطع الاتصال: ${e.message}")
        }
    }
    
    // تغيير السيرفر (للتطوير/الإنتاج)
    fun switchToProductionServer() {
        prefs.edit().putBoolean("use_local_server", false).apply()
    }
    
    fun switchToLocalServer() {
        prefs.edit().putBoolean("use_local_server", true).apply()
    }
    
    // إعداد عنوان سيرفر مخصص
    fun setCustomServerUrl(host: String, port: Int) {
        prefs.edit()
            .putString("custom_server_host", host)
            .putInt("custom_server_port", port)
            .apply()
    }
}

// نماذج البيانات للرسائل
@Serializable
data class ServerMessage(
    val type: MessageType,
    val fromUserId: String? = null,
    val toUserId: String? = null,
    val data: Map<String, Any>? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class MessageType {
    // رسائل التسجيل
    REGISTER,
    UNREGISTER,
    HEARTBEAT,
    
    // رسائل الاتصال
    CONNECTION_REQUEST,
    CONNECTION_RESPONSE,
    CONNECTION_ACCEPTED,
    CONNECTION_REJECTED,
    
    // معلومات المستخدمين
    USER_LIST,
    USER_STATUS,
    PEER_INFO,
    
    // رسائل P2P
    P2P_OFFER,
    P2P_ANSWER,
    P2P_ICE_CANDIDATE,
    
    // رسائل النظام
    ERROR,
    SUCCESS,
    NOTIFICATION
}

// حالات الاتصال
sealed class ConnectionStatus {
    object Connected : ConnectionStatus()
    object Disconnected : ConnectionStatus()
    object NoInternet : ConnectionStatus()
    object PermanentFailure : ConnectionStatus()
    data class Failed(val reason: String) : ConnectionStatus()
    data class Reconnecting(val attempt: Int) : ConnectionStatus()
}

// Activity للتحكم في الاتصال بالسيرفر
class ServerConnectionActivity : ComponentActivity() {
    
    private lateinit var serverConnection: CryptaServerConnection
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        serverConnection = CryptaServerConnection(this)
        
        setContent {
            CryptaTheme {
                ServerConnectionScreen(
                    serverConnection = serverConnection,
                    onBack = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // قطع الاتصال عند إغلاق التطبيق
        CoroutineScope(Dispatchers.IO).launch {
            serverConnection.disconnect()
        }
    }
}

// واجهة إعدادات الاتصال
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectionScreen(
    serverConnection: CryptaServerConnection,
    onBack: () -> Unit
) {
    var connectionStatus by remember { mutableStateOf<ConnectionStatus>(ConnectionStatus.Disconnected) }
    var serverUrl by remember { mutableStateOf(CryptaServerConfig.SERVER_WS_URL) }
    var userId by remember { mutableStateOf("user_${Random.nextInt(1000, 9999)}") }
    var isConnecting by remember { mutableStateOf(false) }
    var useLocalServer by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    // مراقبة حالة الاتصال
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val status = serverConnection.receiveConnectionEvent()
                connectionStatus = status
                isConnecting = status is ConnectionStatus.Reconnecting
            } catch (e: Exception) {
                break
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0F),
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
            .padding(16.dp)
    ) {
        // الشريط العلوي
        TopAppBar(
            title = {
                Text(
                    text = "إعدادات السيرفر",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // حالة الاتصال
        ConnectionStatusCard(
            status = connectionStatus,
            isConnecting = isConnecting
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // إعدادات الاتصال
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "إعدادات الاتصال",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // معرف المستخدم
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    label = { Text("معرف المستخدم") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6C63FF),
                        unfocusedBorderColor = Color(0xFF2A2A2A),
                        focusedLabelColor = Color(0xFF6C63FF),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // اختيار السيرفر
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(
                        checked = useLocalServer,
                        onCheckedChange = { 
                            useLocalServer = it
                            serverUrl = if (it) {
                                CryptaServerConfig.LOCAL_WS_URL
                            } else {
                                CryptaServerConfig.SERVER_WS_URL
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF6C63FF),
                            checkedTrackColor = Color(0xFF6C63FF).copy(alpha = 0.5f)
                        )
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = if (useLocalServer) "سيرفر محلي (تطوير)" else "سيرفر الإنتاج",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // عنوان السيرفر
                Text(
                    text = "العنوان: $serverUrl",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color(0xFF2A2A2A).copy(alpha = 0.5f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // أزرار التحكم
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // زر الاتصال
            Button(
                onClick = {
                    scope.launch {
                        isConnecting = true
                        if (useLocalServer) {
                            serverConnection.switchToLocalServer()
                        } else {
                            serverConnection.switchToProductionServer()
                        }
                        serverConnection.connect(userId)
                    }
                },
                enabled = !isConnecting && connectionStatus !is ConnectionStatus.Connected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6C63FF)
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("اتصال")
            }
            
            // زر قطع الاتصال
            Button(
                onClick = {
                    scope.launch {
                        serverConnection.disconnect()
                    }
                },
                enabled = connectionStatus is ConnectionStatus.Connected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6B6B)
                )
            ) {
                Text("قطع الاتصال")
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // معلومات إضافية
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E).copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "ℹ️ معلومات الاتصال",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "• السيرفر المحلي: للتطوير والاختبار\n" +
                           "• سيرفر الإنتاج: للاستخدام الفعلي\n" +
                           "• تأكد من تشغيل السيرفر قبل المحاولة\n" +
                           "• المحادثات مشفرة E2E بعد الاتصال",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// بطاقة حالة الاتصال
@Composable
fun ConnectionStatusCard(
    status: ConnectionStatus,
    isConnecting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is ConnectionStatus.Connected -> Color(0xFF4CAF50).copy(alpha = 0.8f)
                is ConnectionStatus.Failed, is ConnectionStatus.PermanentFailure -> 
                    Color(0xFFFF6B6B).copy(alpha = 0.8f)
                is ConnectionStatus.Reconnecting -> Color(0xFFFF9800).copy(alpha = 0.8f)
                else -> Color(0xFF2A2A2A).copy(alpha = 0.8f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة الحالة
            val (icon, iconColor) = when (status) {
                is ConnectionStatus.Connected -> Icons.Default.CheckCircle to Color.White
                is ConnectionStatus.Failed, is ConnectionStatus.PermanentFailure -> 
                    Icons.Default.Error to Color.White
                is ConnectionStatus.Reconnecting -> Icons.Default.Refresh to Color.White
                is ConnectionStatus.NoInternet -> Icons.Default.WifiOff to Color.White
                else -> Icons.Default.Circle to Color.White.copy(alpha = 0.5f)
            }
            
            Icon(
                imageVector = icon,
                contentDescription =
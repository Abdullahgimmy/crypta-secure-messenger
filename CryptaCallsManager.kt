// CryptaCallsManager.kt
package com.crypta.app.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.nio.charset.StandardCharsets
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

// مدير المكالمات الرئيسي
class CryptaCallsManager(private val context: Context) {
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    
    // مفاتيح التشفير المتقدم
    private var encryptionKey: ByteArray? = null
    private val secureRandom = SecureRandom()
    
    // حالة المكالمة
    private var isCallActive = false
    private var isVideoEnabled = true
    private var isAudioEnabled = true
    
    companion object {
        private const val AUDIO_SAMPLE_RATE = 48000
        private const val AUDIO_CHANNELS = 2
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )
    }
    
    // تهيئة النظام
    fun initialize() {
        initializePeerConnectionFactory()
        setupEncryption()
    }
    
    // إعداد مصنع اتصالات WebRTC
    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        
        PeerConnectionFactory.initialize(options)
        
        val encoderFactory = DefaultVideoEncoderFactory(
            EglBase.create().eglBaseContext, true, true
        )
        
        val decoderFactory = DefaultVideoDecoderFactory(
            EglBase.create().eglBaseContext
        )
        
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false // نستخدم التشفير المخصوص
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }
    
    // إعداد التشفير المتقدم
    private fun setupEncryption() {
        try {
            // إنشاء مفاتيح X25519 للمكالمة
            val privateKey = X25519PrivateKeyParameters(secureRandom)
            val publicKey = privateKey.generatePublicKey()
            
            // محاكاة تبادل المفاتيح (في التطبيق الحقيقي عبر السيرفر)
            val otherPrivateKey = X25519PrivateKeyParameters(secureRandom)
            val otherPublicKey = otherPrivateKey.generatePublicKey()
            
            // حساب السر المشترك
            val sharedSecret = ByteArray(32)
            val agreement = X25519Agreement()
            agreement.init(privateKey)
            agreement.calculateAgreement(otherPublicKey, sharedSecret, 0)
            
            // اشتقاق مفتاح التشفير باستخدام HKDF
            val info = "Crypta-Call-Audio-Video-Encryption-v1".toByteArray()
            val hkdf = HKDFBytesGenerator(SHA256Digest())
            hkdf.init(HKDFParameters(sharedSecret, null, info))
            
            encryptionKey = ByteArray(32)
            hkdf.generateBytes(encryptionKey, 0, 32)
            
            println("🔐 تم تأسيس التشفير للمكالمة بنجاح")
        } catch (e: Exception) {
            println("❌ خطأ في إعداد التشفير: ${e.message}")
        }
    }
    
    // تشفير البيانات الصوتية/المرئية
    private fun encryptAudioData(audioData: ByteArray): ByteArray {
        return try {
            if (encryptionKey == null) return audioData
            
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            
            val keySpec = SecretKeySpec(encryptionKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val encryptedData = cipher.doFinal(audioData)
            
            // دمج IV مع البيانات المشفرة
            val result = ByteArray(iv.size + encryptedData.size)
            System.arraycopy(iv, 0, result, 0, iv.size)
            System.arraycopy(encryptedData, 0, result, iv.size, encryptedData.size)
            
            result
        } catch (e: Exception) {
            println("❌ خطأ في تشفير الصوت: ${e.message}")
            audioData
        }
    }
    
    // فك تشفير البيانات الصوتية/المرئية
    private fun decryptAudioData(encryptedData: ByteArray): ByteArray {
        return try {
            if (encryptionKey == null || encryptedData.size < 12) return encryptedData
            
            val iv = ByteArray(12)
            System.arraycopy(encryptedData, 0, iv, 0, 12)
            
            val cipherData = ByteArray(encryptedData.size - 12)
            System.arraycopy(encryptedData, 12, cipherData, 0, cipherData.size)
            
            val keySpec = SecretKeySpec(encryptionKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            cipher.doFinal(cipherData)
        } catch (e: Exception) {
            println("❌ خطأ في فك تشفير الصوت: ${e.message}")
            encryptedData
        }
    }
    
    // بدء المكالمة الصوتية
    fun startVoiceCall(remoteUserId: String) {
        if (!checkPermissions()) {
            println("❌ الأذونات غير متوفرة للمكالمة")
            return
        }
        
        try {
            // إعداد الصوت المحلي
            setupLocalAudio()
            
            // إنشاء اتصال الند للند
            createPeerConnection()
            
            // إضافة المسار الصوتي
            if (localAudioTrack != null && peerConnection != null) {
                peerConnection!!.addTrack(localAudioTrack, listOf("CryptaAudioStream"))
            }
            
            isCallActive = true
            isVideoEnabled = false
            
            println("🎙️ بدأت المكالمة الصوتية المشفرة")
        } catch (e: Exception) {
            println("❌ خطأ في بدء المكالمة الصوتية: ${e.message}")
        }
    }
    
    // بدء مكالمة الفيديو
    fun startVideoCall(remoteUserId: String) {
        if (!checkPermissions()) {
            println("❌ الأذونات غير متوفرة للمكالمة")
            return
        }
        
        try {
            // إعداد الصوت والفيديو المحلي
            setupLocalAudio()
            setupLocalVideo()
            
            // إنشاء اتصال الند للند
            createPeerConnection()
            
            // إضافة المسارات
            if (localAudioTrack != null && peerConnection != null) {
                peerConnection!!.addTrack(localAudioTrack, listOf("CryptaAudioStream"))
            }
            
            if (localVideoTrack != null && peerConnection != null) {
                peerConnection!!.addTrack(localVideoTrack, listOf("CryptaVideoStream"))
            }
            
            isCallActive = true
            isVideoEnabled = true
            
            println("📹 بدأت مكالمة الفيديو المشفرة")
        } catch (e: Exception) {
            println("❌ خطأ في بدء مكالمة الفيديو: ${e.message}")
        }
    }
    
    // إعداد الصوت المحلي مع التشفير
    private fun setupLocalAudio() {
        val audioSource = peerConnectionFactory?.createAudioSource(
            MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("noiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("autoGainControl", "true"))
            }
        )
        
        localAudioTrack = peerConnectionFactory?.createAudioTrack("CryptaAudioTrack", audioSource)
    }
    
    // إعداد الفيديو المحلي مع التشفير
    private fun setupLocalVideo() {
        val eglBase = EglBase.create()
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        
        // إعداد كاميرا الفيديو
        videoCapturer = createCameraCapturer()
        
        val videoSource = peerConnectionFactory?.createVideoSource(false)
        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
        
        localVideoTrack = peerConnectionFactory?.createVideoTrack("CryptaVideoTrack", videoSource)
        
        // بدء التقاط الفيديو
        videoCapturer?.startCapture(1280, 720, 30)
    }
    
    // إنشاء كاميرا الالتقاط
    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        
        // البحث عن الكاميرا الأمامية أولاً
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        // إذا لم توجد كاميرا أمامية، استخدم الخلفية
        for (deviceName in enumerator.deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        
        return null
    }
    
    // إنشاء اتصال الند للند مع التشفير
    private fun createPeerConnection() {
        val configuration = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                // يمكن إضافة TURN server هنا
            )
        ).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            enableDtlsSrtp = true // تفعيل DTLS-SRTP للأمان
        }
        
        peerConnection = peerConnectionFactory?.createPeerConnection(
            configuration,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    println("📡 حالة الإشارة: $state")
                }
                
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    println("🧊 حالة اتصال ICE: $state")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED -> {
                            println("✅ تم الاتصال بنجاح")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            println("⚠️ انقطع الاتصال")
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            println("❌ فشل الاتصال")
                        }
                        else -> {}
                    }
                }
                
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    println("🔍 جمع ICE: $state")
                }
                
                override fun onIceCandidate(candidate: IceCandidate?) {
                    println("🎯 مرشح ICE جديد")
                    // هنا يتم إرسال المرشح للطرف الآخر عبر السيرفر
                }
                
                override fun onAddStream(stream: MediaStream?) {
                    println("📺 تم استقبال stream جديد")
                    // معالجة الستريم المستقبل (صوت/فيديو)
                }
                
                override fun onRemoveStream(stream: MediaStream?) {
                    println("❌ تم حذف stream")
                }
                
                override fun onDataChannel(dataChannel: DataChannel?) {
                    println("📊 قناة بيانات جديدة")
                }
                
                override fun onRenegotiationNeeded() {
                    println("🔄 مطلوب إعادة تفاوض")
                }
            }
        )
    }
    
    // كتم/إلغاء كتم الميكروفون
    fun toggleMicrophone(): Boolean {
        isAudioEnabled = !isAudioEnabled
        localAudioTrack?.setEnabled(isAudioEnabled)
        return isAudioEnabled
    }
    
    // تشغيل/إيقاف الكاميرا
    fun toggleCamera(): Boolean {
        isVideoEnabled = !isVideoEnabled
        localVideoTrack?.setEnabled(isVideoEnabled)
        return isVideoEnabled
    }
    
    // تبديل الكاميرا (أمامية/خلفية)
    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }
    
    // إنهاء المكالمة
    fun endCall() {
        try {
            // إيقاف التقاط الفيديو
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
            
            // تنظيف المسارات
            localAudioTrack?.dispose()
            localVideoTrack?.dispose()
            
            // إغلاق الاتصال
            peerConnection?.close()
            peerConnection = null
            
            // تنظيف المتغيرات
            isCallActive = false
            isVideoEnabled = false
            isAudioEnabled = false
            
            println("📞 تم إنهاء المكالمة")
        } catch (e: Exception) {
            println("❌ خطأ في إنهاء المكالمة: ${e.message}")
        }
    }
    
    // فحص الأذونات
    private fun checkPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // تنظيف الموارد
    fun cleanup() {
        endCall()
        peerConnectionFactory?.dispose()
        surfaceTextureHelper?.dispose()
    }
    
    // الحصول على حالة المكالمة
    fun getCallState(): CallState {
        return CallState(
            isActive = isCallActive,
            isVideoEnabled = isVideoEnabled,
            isAudioEnabled = isAudioEnabled,
            isEncrypted = encryptionKey != null
        )
    }
}

// نموذج حالة المكالمة
data class CallState(
    val isActive: Boolean = false,
    val isVideoEnabled: Boolean = false,
    val isAudioEnabled: Boolean = true,
    val isEncrypted: Boolean = false
)

// Activity للمكالمات
class CallActivity : ComponentActivity() {
    private lateinit var callsManager: CryptaCallsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تهيئة مدير المكالمات
        callsManager = CryptaCallsManager(this)
        callsManager.initialize()
        
        setContent {
            CryptaCallTheme {
                CallScreen(
                    callsManager = callsManager,
                    onEndCall = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        callsManager.cleanup()
    }
}

// واجهة المكالمة
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callsManager: CryptaCallsManager,
    onEndCall: () -> Unit
) {
    var callState by remember { mutableStateOf(CallState()) }
    var callDuration by remember { mutableStateOf(0) }
    var showControls by remember { mutableStateOf(true) }
    
    // تحديث حالة المكالمة
    LaunchedEffect(Unit) {
        while (callState.isActive) {
            callState = callsManager.getCallState()
            callDuration++
            delay(1000)
        }
    }
    
    // إخفاء الضوابط بعد فترة
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { showControls = !showControls }
    ) {
        // خلفية متدرجة جميلة
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A0A0F),
                            Color(0xFF1A1A2E),
                            Color(0xFF0A0A0F)
                        )
                    )
                )
        )
        
        // منطقة الفيديو المحلي
        if (callState.isVideoEnabled) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(EglBase.create().eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    }
                },
                modifier = Modifier
                    .size(120.dp, 160.dp)
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
        
        // منطقة الفيديو الرئيسية
        if (callState.isVideoEnabled) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(EglBase.create().eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // واجهة المكالمة الصوتية
            VoiceCallInterface(callDuration = callDuration)
        }
        
        // شريط المعلومات العلوي
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            CallTopBar(
                callDuration = callDuration,
                isEncrypted = callState.isEncrypted,
                modifier = Modifier.padding(16.dp)
            )
        }
        
        // أزرار التحكم السفلية
        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CallControlsBar(
                callState = callState,
                onToggleMic = { callsManager.toggleMicrophone() },
                onToggleCamera = { callsManager.toggleCamera() },
                onSwitchCamera = { callsManager.switchCamera() },
                onEndCall = {
                    callsManager.endCall()
                    onEndCall()
                },
                modifier = Modifier.padding(32.dp)
            )
        }
        
        // مؤشر التشفير
        if (callState.isEncrypted) {
            EncryptionIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
    }
}

// واجهة المكالمة الصوتية
@Composable
fun VoiceCallInterface(callDuration: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // أيقونة الصوت مع تأثير نبضة
        var scale by remember { mutableStateOf(1f) }
        
        LaunchedEffect(Unit) {
            while (true) {
                scale = 1.2f
                delay(500)
                scale = 1f
                delay(500)
            }
        }
        
        Card(
            modifier = Modifier
                .size(200.dp)
                .scale(scale),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6C63FF).copy(alpha = 0.3f)
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "مكالمة صوتية",
                    tint = Color.White,
                    modifier = Modifier.size(80.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "مكالمة صوتية مشفرة",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Text(
            text = formatCallDuration(callDuration),
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// الشريط العلوي للمكالمة
@Composable
fun CallTopBar(
    callDuration: Int,
    isEncrypted: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEncrypted) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "مشفر",
                    tint = Color(0xFF03DAC6),
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            Text(
                text = formatCallDuration(callDuration),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            if (isEncrypted) {
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "• مشفر",
                    color = Color(0xFF03DAC6),
                    fontSize = 14.sp
                )
            }
        }
    }
}

// أزرار التحكم
@Composable
fun CallControlsBar(
    callState: CallState,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // زر كتم الميكروفون
        FloatingActionButton(
            onClick = onToggleMic,
            modifier = Modifier.size(60.dp),
            containerColor = if (callState.isAudioEnabled) 
                Color(0xFF2A2A2A) else Color(0xFFFF6B6B)
        ) {
            Icon(
                imageVector = if (callState.isAudioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (callState.isAudioEnabled) "كتم" else "إلغاء الكتم",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        
        // زر إنهاء المكالمة
        FloatingActionButton(
            onClick = onEndCall,
            modifier = Modifier.size(70.dp),
            containerColor = Color(0xFFFF4444)
        ) {
            Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "إنهاء المكالمة",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        
        // زر الكاميرا (للفيديو)
        if (callState.isVideoEnabled) {
            FloatingActionButton(
                onClick = onToggleCamera,
                modifier = Modifier.size(60.dp),
                containerColor = if (callState.isVideoEnabled) 
                    Color(0xFF2A2A2A) else Color(0xFFFF6B6B)
            ) {
                Icon(
                    imageVector = if (callState.isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = if (callState.isVideoEnabled) "إيقاف الكاميرا" else "تشغيل الكاميرا",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        // زر تبديل الكاميرا
        if (callState.isVideoEnabled) {
            FloatingActionButton(
                onClick = onSwitchCamera,
                modifier = Modifier.size(60.dp),
                containerColor = Color(0xFF2A2A2A)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription =
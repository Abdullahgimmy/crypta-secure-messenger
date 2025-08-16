// MainActivity.kt
package com.secureapp.privacy

import android.os.Bundle
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SecurePrivacyTheme {
                SecureApp()
            }
        }
    }
}

// الألوان والتصميم الجميل
@Composable
fun SecurePrivacyTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF6C63FF),
        secondary = Color(0xFF03DAC6),
        tertiary = Color(0xFFFF6B6B),
        background = Color(0xFF0A0A0F),
        surface = Color(0xFF1A1A2E),
        onPrimary = Color.White,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White
    )
    
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

// الشاشة الرئيسية
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureApp() {
    var currentScreen by remember { mutableStateOf("home") }
    var isLocked by remember { mutableStateOf(true) }
    var animationState by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        animationState = true
    }
    
    Box(
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
    ) {
        // خلفية متحركة جميلة
        AnimatedBackground()
        
        when {
            isLocked -> {
                LockScreen(
                    onUnlock = { 
                        isLocked = false
                        currentScreen = "home"
                    }
                )
            }
            currentScreen == "home" -> {
                HomeScreen(
                    onNavigate = { screen -> currentScreen = screen }
                )
            }
            currentScreen == "messages" -> {
                MessagesScreen(
                    onBack = { currentScreen = "home" }
                )
            }
            currentScreen == "vault" -> {
                VaultScreen(
                    onBack = { currentScreen = "home" }
                )
            }
            currentScreen == "settings" -> {
                SettingsScreen(
                    onBack = { currentScreen = "home" },
                    onLock = { isLocked = true }
                )
            }
        }
    }
}

// الخلفية المتحركة الجميلة
@Composable
fun AnimatedBackground() {
    var offset1 by remember { mutableStateOf(0f) }
    var offset2 by remember { mutableStateOf(100f) }
    var offset3 by remember { mutableStateOf(200f) }
    
    LaunchedEffect(Unit) {
        while (true) {
            offset1 += 0.5f
            offset2 += 0.3f
            offset3 += 0.7f
            delay(50)
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // دوائر متحركة
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (offset1 % 400).dp, y = (offset1 % 800).dp)
                .background(
                    Color(0xFF6C63FF).copy(alpha = 0.1f),
                    CircleShape
                )
                .blur(20.dp)
        )
        
        Box(
            modifier = Modifier
                .size(150.dp)
                .offset(x = (offset2 % 350).dp, y = (offset2 % 700).dp)
                .background(
                    Color(0xFF03DAC6).copy(alpha = 0.08f),
                    CircleShape
                )
                .blur(15.dp)
        )
        
        Box(
            modifier = Modifier
                .size(100.dp)
                .offset(x = (offset3 % 300).dp, y = (offset3 % 600).dp)
                .background(
                    Color(0xFFFF6B6B).copy(alpha = 0.06f),
                    CircleShape
                )
                .blur(10.dp)
        )
    }
}

// شاشة القفل الجميلة
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var isShaking by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    
    LaunchedEffect(isShaking) {
        if (isShaking) {
            repeat(5) {
                scale = 1.1f
                delay(50)
                scale = 0.9f
                delay(50)
            }
            scale = 1f
            isShaking = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // أيقونة القفل مع تأثير جميل
        Card(
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF6C63FF).copy(alpha = 0.2f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "قفل",
                    modifier = Modifier.size(60.dp),
                    tint = Color(0xFF6C63FF)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "تطبيق الخصوصية الآمن",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "أدخل الرقم السري للدخول",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // عرض PIN المدخل
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            repeat(4) { index ->
                Card(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (index < pin.length) 
                            Color(0xFF6C63FF) else Color(0xFF2A2A2A)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (index < pin.length) {
                            Icon(
                                imageVector = Icons.Default.Circle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(40.dp))
        
        // لوحة الأرقام الجميلة
        PinPad(
            onNumberClick = { number ->
                if (pin.length < 4) {
                    pin += number
                    if (pin.length == 4) {
                        if (pin == "1234") { // الرقم السري الافتراضي
                            onUnlock()
                        } else {
                            isShaking = true
                            pin = ""
                        }
                    }
                }
            },
            onDelete = {
                if (pin.isNotEmpty()) {
                    pin = pin.dropLast(1)
                }
            }
        )
    }
}

// لوحة الأرقام
@Composable
fun PinPad(
    onNumberClick: (String) -> Unit,
    onDelete: () -> Unit
) {
    val numbers = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        numbers.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { number ->
                    if (number.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .size(70.dp)
                                .clickable {
                                    when (number) {
                                        "⌫" -> onDelete()
                                        else -> onNumberClick(number)
                                    }
                                },
                            shape = CircleShape,
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2A2A2A)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (number == "⌫") {
                                    Icon(
                                        imageVector = Icons.Default.Backspace,
                                        contentDescription = "حذف",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    Text(
                                        text = number,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(70.dp))
                    }
                }
            }
        }
    }
}

// الشاشة الرئيسية
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            delay(1000)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        // الترحيب والوقت
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E).copy(alpha = 0.8f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "مرحباً بك",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = currentTime,
                    fontSize = 16.sp,
                    color = Color(0xFF03DAC6),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Text(
                    text = "تطبيقك الآمن للخصوصية",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // الميزات الرئيسية
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(getMainFeatures()) { feature ->
                FeatureCard(
                    feature = feature,
                    onClick = { onNavigate(feature.route) }
                )
            }
        }
    }
}

// بطاقة الميزة
@Composable
fun FeatureCard(
    feature: Feature,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .scale(if (isPressed) 0.95f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                modifier = Modifier.size(60.dp),
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = feature.color.copy(alpha = 0.2f)
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = feature.icon,
                        contentDescription = feature.title,
                        tint = feature.color,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feature.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = feature.description,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "اذهب",
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// شاشة الرسائل المشفرة
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(onBack: () -> Unit) {
    var messages by remember { mutableStateOf(getSampleMessages()) }
    var newMessage by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // شريط علوي
        TopAppBar(
            title = {
                Text(
                    text = "الرسائل المشفرة",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1A2E)
            )
        )
        
        // قائمة الرسائل
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message = message)
            }
        }
        
        // حقل إدخال الرسالة
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newMessage,
                onValueChange = { newMessage = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("اكتب رسالة آمنة...")
                },
                shape = RoundedCornerShape(25.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = Color(0xFF2A2A2A)
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            FloatingActionButton(
                onClick = {
                    if (newMessage.isNotEmpty()) {
                        val encryptedMessage = encryptMessage(newMessage)
                        messages = messages + Message(
                            text = encryptedMessage,
                            isFromUser = true,
                            timestamp = getCurrentTime()
                        )
                        newMessage = ""
                    }
                },
                modifier = Modifier.size(56.dp),
                containerColor = Color(0xFF6C63FF)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "إرسال",
                    tint = Color.White
                )
            }
        }
    }
}

// فقاعة الرسالة
@Composable
fun MessageBubble(message: Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (message.isFromUser) 20.dp else 4.dp,
                bottomEnd = if (message.isFromUser) 4.dp else 20.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromUser) 
                    Color(0xFF6C63FF) else Color(0xFF2A2A2A)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = decryptMessage(message.text),
                    color = Color.White,
                    fontSize = 16.sp
                )
                
                Text(
                    text = message.timestamp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// شاشة الخزنة الآمنة
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onBack: () -> Unit) {
    var vaultItems by remember { mutableStateOf(getVaultItems()) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "الخزنة الآمنة",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
            },
            actions = {
                IconButton(onClick = { /* إضافة عنصر جديد */ }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1A2E)
            )
        )
        
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(vaultItems) { item ->
                VaultItemCard(item = item)
            }
        }
    }
}

// بطاقة عنصر الخزنة
@Composable
fun VaultItemCard(item: VaultItem) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = Color(0xFF03DAC6),
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    Text(
                        text = item.subtitle,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "طي" else "توسيع",
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
            
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Divider(
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    
                    Text(
                        text = "المحتوى المشفر:",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    
                    Text(
                        text = decryptMessage(item.encryptedContent),
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// شاشة الإعدادات
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLock: () -> Unit
) {
    val settingsItems = getSettingsItems()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "الإعدادات",
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color(0xFF1A1A2E)
            )
        )
        
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(settingsItems) { item ->
                SettingItem(
                    item = item,
                    onClick = {
                        when (item.title) {
                            "قفل التطبيق" -> onLock()
                            // يمكن إضافة المزيد من الإجراءات هنا
                        }
                    }
                )
            }
        }
    }
}

// عنصر الإعداد
@Composable
fun SettingItem(
    item: SettingItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E).copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = Color(0xFF6C63FF),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                
                if (item.description.isNotEmpty()) {
                    Text(
                        text = item.description,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "اذهب",
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// نماذج البيانات
data class Feature(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

data class Message(
    val text: String,
    val isFromUser: Boolean,
    val timestamp: String
)

data class VaultItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val encryptedContent: String
)

data class SettingItem(
    val title: String,
    val description: String,
    val icon: ImageVector
)

// وظائف مساعدة
fun getCurrentTime(): String {
    return SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale("ar")).format(Date())
}

fun getMainFeatures(): List<Feature> {
    return listOf(
        Feature(
            title = "الرسائل المشفرة",
            description = "أرسل واستقبل رسائل مشفرة بأمان",
            icon = Icons.Default.Message,
            color = Color(0xFF6C63FF),
            route = "messages"
        ),
        Feature(
            title = "الخزنة الآمنة",
            description = "احفظ بياناتك الحساسة بأمان",
            icon = Icons.Default.Lock,
            color = Color(0xFF03DAC6),
            route = "vault"
        ),
        Feature(
            title = "الإعدادات",
            description = "تخصيص التطبيق وإعدادات الأمان",
            icon = Icons.Default.Settings,
            color = Color(0xFFFF6B6B),
            route = "settings"
        )
    )
}

fun getSampleMessages(): List<Message> {
    return listOf(
        Message(
            text = encryptMessage("مرحباً! كيف حالك؟"),
            isFromUser = false,
            timestamp = "10:30"
        ),
        Message(
            text = encryptMessage("الحمد لله، كل شيء بخير"),
            isFromUser = true,
            timestamp = "10:35"
        ),
        Message
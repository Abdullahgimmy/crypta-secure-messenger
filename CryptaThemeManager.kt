// CryptaThemeManager.kt
package com.crypta.app.themes

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// مدير الثيمات الرئيسي
class CryptaThemeManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("crypta_themes", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREF_THEME_ID = "selected_theme_id"
        private const val PREF_CUSTOM_PRIMARY = "custom_primary_color"
        private const val PREF_CUSTOM_SECONDARY = "custom_secondary_color"
        private const val PREF_CUSTOM_TERTIARY = "custom_tertiary_color"
        private const val PREF_ANIMATION_SPEED = "animation_speed"
        private const val PREF_BLUR_ENABLED = "blur_enabled"
        private const val PREF_PARTICLES_ENABLED = "particles_enabled"
        private const val PREF_STEALTH_MODE = "stealth_mode"
        private const val PREF_DYNAMIC_COLORS = "dynamic_colors"
    }
    
    // حفظ الثيم المحدد
    fun saveSelectedTheme(themeId: String) {
        prefs.edit().putString(PREF_THEME_ID, themeId).apply()
    }
    
    // الحصول على الثيم المحدد
    fun getSelectedTheme(): String {
        return prefs.getString(PREF_THEME_ID, CryptaTheme.DARK_PURPLE.id) ?: CryptaTheme.DARK_PURPLE.id
    }
    
    // حفظ الألوان المخصصة
    fun saveCustomColors(primary: Long, secondary: Long, tertiary: Long) {
        prefs.edit()
            .putLong(PREF_CUSTOM_PRIMARY, primary)
            .putLong(PREF_CUSTOM_SECONDARY, secondary)
            .putLong(PREF_CUSTOM_TERTIARY, tertiary)
            .apply()
    }
    
    // الحصول على الألوان المخصصة
    fun getCustomColors(): Triple<Long, Long, Long> {
        return Triple(
            prefs.getLong(PREF_CUSTOM_PRIMARY, 0xFF6C63FF),
            prefs.getLong(PREF_CUSTOM_SECONDARY, 0xFF03DAC6),
            prefs.getLong(PREF_CUSTOM_TERTIARY, 0xFFFF6B6B)
        )
    }
    
    // حفظ سرعة الأنيميشن
    fun saveAnimationSpeed(speed: Float) {
        prefs.edit().putFloat(PREF_ANIMATION_SPEED, speed).apply()
    }
    
    // الحصول على سرعة الأنيميشن
    fun getAnimationSpeed(): Float {
        return prefs.getFloat(PREF_ANIMATION_SPEED, 1.0f)
    }
    
    // حفظ إعدادات التأثيرات
    fun saveEffectSettings(blurEnabled: Boolean, particlesEnabled: Boolean) {
        prefs.edit()
            .putBoolean(PREF_BLUR_ENABLED, blurEnabled)
            .putBoolean(PREF_PARTICLES_ENABLED, particlesEnabled)
            .apply()
    }
    
    // الحصول على إعدادات التأثيرات
    fun getEffectSettings(): Pair<Boolean, Boolean> {
        return Pair(
            prefs.getBoolean(PREF_BLUR_ENABLED, true),
            prefs.getBoolean(PREF_PARTICLES_ENABLED, true)
        )
    }
    
    // وضع التخفي
    fun setStealthMode(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_STEALTH_MODE, enabled).apply()
    }
    
    fun isStealthMode(): Boolean {
        return prefs.getBoolean(PREF_STEALTH_MODE, false)
    }
    
    // الألوان الديناميكية
    fun setDynamicColors(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_DYNAMIC_COLORS, enabled).apply()
    }
    
    fun isDynamicColors(): Boolean {
        return prefs.getBoolean(PREF_DYNAMIC_COLORS, false)
    }
}

// تعريف الثيمات المختلفة
enum class CryptaTheme(
    val id: String,
    val name: String,
    val description: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val tertiaryColor: Color,
    val backgroundColor: Color,
    val surfaceColor: Color,
    val gradientColors: List<Color>
) {
    DARK_PURPLE(
        id = "dark_purple",
        name = "البنفسجي الداكن",
        description = "الثيم الكلاسيكي لـ Crypta",
        primaryColor = Color(0xFF6C63FF),
        secondaryColor = Color(0xFF03DAC6),
        tertiaryColor = Color(0xFFFF6B6B),
        backgroundColor = Color(0xFF0A0A0F),
        surfaceColor = Color(0xFF1A1A2E),
        gradientColors = listOf(Color(0xFF0A0A0F), Color(0xFF1A1A2E), Color(0xFF16213E))
    ),
    
    CYBERPUNK(
        id = "cyberpunk",
        name = "سايبر بانك",
        description = "مستوحى من المستقبل الرقمي",
        primaryColor = Color(0xFF00FFFF),
        secondaryColor = Color(0xFFFF0080),
        tertiaryColor = Color(0xFFFFFF00),
        backgroundColor = Color(0xFF0D0D0D),
        surfaceColor = Color(0xFF1A0D1A),
        gradientColors = listOf(Color(0xFF0D0D0D), Color(0xFF1A0D1A), Color(0xFF2D1B3D))
    ),
    
    OCEAN_DEPTH(
        id = "ocean_depth",
        name = "أعماق المحيط",
        description = "هدوء الأعماق الزرقاء",
        primaryColor = Color(0xFF0077BE),
        secondaryColor = Color(0xFF00C9A7),
        tertiaryColor = Color(0xFF4FC3F7),
        backgroundColor = Color(0xFF001122),
        surfaceColor = Color(0xFF112233),
        gradientColors = listOf(Color(0xFF001122), Color(0xFF112233), Color(0xFF1B3A5C))
    ),
    
    FOREST_NIGHT(
        id = "forest_night",
        name = "ليل الغابة",
        description = "الطبيعة في الظلام",
        primaryColor = Color(0xFF4CAF50),
        secondaryColor = Color(0xFF8BC34A),
        tertiaryColor = Color(0xFF00BCD4),
        backgroundColor = Color(0xFF0F1419),
        surfaceColor = Color(0xFF1A2D1A),
        gradientColors = listOf(Color(0xFF0F1419), Color(0xFF1A2D1A), Color(0xFF2E4A2E))
    ),
    
    SUNSET_GLOW(
        id = "sunset_glow",
        name = "توهج الغروب",
        description = "دفء المساء الذهبي",
        primaryColor = Color(0xFFFF6B35),
        secondaryColor = Color(0xFFF7931E),
        tertiaryColor = Color(0xFFFFD23F),
        backgroundColor = Color(0xFF1A0F0A),
        surfaceColor = Color(0xFF2D1A0F),
        gradientColors = listOf(Color(0xFF1A0F0A), Color(0xFF2D1A0F), Color(0xFF4A2C1A))
    ),
    
    MATRIX_GREEN(
        id = "matrix_green",
        name = "المصفوفة الخضراء",
        description = "عالم المصفوفة الرقمي",
        primaryColor = Color(0xFF00FF41),
        secondaryColor = Color(0xFF39FF14),
        tertiaryColor = Color(0xFF00FF00),
        backgroundColor = Color(0xFF000000),
        surfaceColor = Color(0xFF0A0A0A),
        gradientColors = listOf(Color(0xFF000000), Color(0xFF001100), Color(0xFF002200))
    ),
    
    MINIMAL_WHITE(
        id = "minimal_white",
        name = "الأبيض البسيط",
        description = "نظافة وبساطة",
        primaryColor = Color(0xFF2196F3),
        secondaryColor = Color(0xFF4CAF50),
        tertiaryColor = Color(0xFFFF9800),
        backgroundColor = Color(0xFFFAFAFA),
        surfaceColor = Color(0xFFFFFFFF),
        gradientColors = listOf(Color(0xFFFAFAFA), Color(0xFFFFFFFF), Color(0xFFF5F5F5))
    ),
    
    CUSTOM(
        id = "custom",
        name = "مخصص",
        description = "ألوانك الخاصة",
        primaryColor = Color(0xFF6C63FF),
        secondaryColor = Color(0xFF03DAC6),
        tertiaryColor = Color(0xFFFF6B6B),
        backgroundColor = Color(0xFF0A0A0F),
        surfaceColor = Color(0xFF1A1A2E),
        gradientColors = listOf(Color(0xFF0A0A0F), Color(0xFF1A1A2E), Color(0xFF16213E))
    )
}

// Composable للثيم الرئيسي
@Composable
fun CryptaAdvancedTheme(
    theme: CryptaTheme = CryptaTheme.DARK_PURPLE,
    customColors: Triple<Long, Long, Long>? = null,
    animationSpeed: Float = 1.0f,
    effectsEnabled: Boolean = true,
    stealthMode: Boolean = false,
    dynamicColors: Boolean = false,
    content: @Composable () -> Unit
) {
    // الألوان النهائية
    val finalTheme = if (theme == CryptaTheme.CUSTOM && customColors != null) {
        theme.copy(
            primaryColor = Color(customColors.first),
            secondaryColor = Color(customColors.second),
            tertiaryColor = Color(customColors.third)
        )
    } else theme
    
    // الألوان الديناميكية
    var currentPrimary by remember { mutableStateOf(finalTheme.primaryColor) }
    var currentSecondary by remember { mutableStateOf(finalTheme.secondaryColor) }
    
    // تأثير الألوان الديناميكية
    LaunchedEffect(dynamicColors) {
        if (dynamicColors) {
            while (true) {
                val time = System.currentTimeMillis() / 1000f
                currentPrimary = Color(
                    red = (sin(time * 0.5f) * 0.5f + 0.5f),
                    green = (sin(time * 0.3f + 2) * 0.5f + 0.5f),
                    blue = (sin(time * 0.7f + 4) * 0.5f + 0.5f)
                )
                currentSecondary = Color(
                    red = (cos(time * 0.4f) * 0.5f + 0.5f),
                    green = (cos(time * 0.6f + 1) * 0.5f + 0.5f),
                    blue = (cos(time * 0.2f + 3) * 0.5f + 0.5f)
                )
                delay((100 / animationSpeed).toLong())
            }
        }
    }
    
    val colorScheme = if (finalTheme.backgroundColor.luminance() > 0.5f) {
        lightColorScheme(
            primary = if (dynamicColors) currentPrimary else finalTheme.primaryColor,
            secondary = if (dynamicColors) currentSecondary else finalTheme.secondaryColor,
            tertiary = finalTheme.tertiaryColor,
            background = finalTheme.backgroundColor,
            surface = finalTheme.surfaceColor,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    } else {
        darkColorScheme(
            primary = if (dynamicColors) currentPrimary else finalTheme.primaryColor,
            secondary = if (dynamicColors) currentSecondary else finalTheme.secondaryColor,
            tertiary = finalTheme.tertiaryColor,
            background = finalTheme.backgroundColor,
            surface = finalTheme.surfaceColor,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        content = {
            CompositionLocalProvider(
                LocalCryptaTheme provides CryptaThemeData(
                    currentTheme = finalTheme,
                    animationSpeed = animationSpeed,
                    effectsEnabled = effectsEnabled,
                    stealthMode = stealthMode,
                    dynamicColors = dynamicColors
                )
            ) {
                content()
            }
        }
    )
}

// بيانات الثيم المحلية
data class CryptaThemeData(
    val currentTheme: CryptaTheme,
    val animationSpeed: Float,
    val effectsEnabled: Boolean,
    val stealthMode: Boolean,
    val dynamicColors: Boolean
)

val LocalCryptaTheme = compositionLocalOf { 
    CryptaThemeData(
        currentTheme = CryptaTheme.DARK_PURPLE,
        animationSpeed = 1.0f,
        effectsEnabled = true,
        stealthMode = false,
        dynamicColors = false
    )
}

// Activity إعدادات الثيمات
class ThemeSettingsActivity : ComponentActivity() {
    private lateinit var themeManager: CryptaThemeManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        themeManager = CryptaThemeManager(this)
        
        setContent {
            val selectedThemeId = remember { mutableStateOf(themeManager.getSelectedTheme()) }
            val customColors = remember { mutableStateOf(themeManager.getCustomColors()) }
            val animationSpeed = remember { mutableStateOf(themeManager.getAnimationSpeed()) }
            val (blurEnabled, particlesEnabled) = themeManager.getEffectSettings()
            val effectSettings = remember { mutableStateOf(blurEnabled to particlesEnabled) }
            val stealthMode = remember { mutableStateOf(themeManager.isStealthMode()) }
            val dynamicColors = remember { mutableStateOf(themeManager.isDynamicColors()) }
            
            val currentTheme = CryptaTheme.values().find { it.id == selectedThemeId.value } 
                ?: CryptaTheme.DARK_PURPLE
            
            CryptaAdvancedTheme(
                theme = currentTheme,
                customColors = if (currentTheme == CryptaTheme.CUSTOM) customColors.value else null,
                animationSpeed = animationSpeed.value,
                effectsEnabled = effectSettings.value.first && effectSettings.value.second,
                stealthMode = stealthMode.value,
                dynamicColors = dynamicColors.value
            ) {
                ThemeSettingsScreen(
                    selectedThemeId = selectedThemeId.value,
                    customColors = customColors.value,
                    animationSpeed = animationSpeed.value,
                    effectSettings = effectSettings.value,
                    stealthMode = stealthMode.value,
                    dynamicColors = dynamicColors.value,
                    onThemeSelect = { themeId ->
                        selectedThemeId.value = themeId
                        themeManager.saveSelectedTheme(themeId)
                    },
                    onCustomColorsChange = { colors ->
                        customColors.value = colors
                        themeManager.saveCustomColors(colors.first, colors.second, colors.third)
                    },
                    onAnimationSpeedChange = { speed ->
                        animationSpeed.value = speed
                        themeManager.saveAnimationSpeed(speed)
                    },
                    onEffectSettingsChange = { blur, particles ->
                        effectSettings.value = blur to particles
                        themeManager.saveEffectSettings(blur, particles)
                    },
                    onStealthModeChange = { enabled ->
                        stealthMode.value = enabled
                        themeManager.setStealthMode(enabled)
                    },
                    onDynamicColorsChange = { enabled ->
                        dynamicColors.value = enabled
                        themeManager.setDynamicColors(enabled)
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

// شاشة إعدادات الثيمات
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    selectedThemeId: String,
    customColors: Triple<Long, Long, Long>,
    animationSpeed: Float,
    effectSettings: Pair<Boolean, Boolean>,
    stealthMode: Boolean,
    dynamicColors: Boolean,
    onThemeSelect: (String) -> Unit,
    onCustomColorsChange: (Triple<Long, Long, Long>) -> Unit,
    onAnimationSpeedChange: (Float) -> Unit,
    onEffectSettingsChange: (Boolean, Boolean) -> Unit,
    onStealthModeChange: (Boolean) -> Unit,
    onDynamicColorsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val currentTheme = LocalCryptaTheme.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                if (currentTheme.effectsEnabled) {
                    Brush.verticalGradient(currentTheme.currentTheme.gradientColors)
                } else {
                    Brush.verticalGradient(listOf(
                        currentTheme.currentTheme.backgroundColor,
                        currentTheme.currentTheme.backgroundColor
                    ))
                }
            )
    ) {
        // خلفية متحركة إذا كانت التأثيرات مفعلة
        if (currentTheme.effectsEnabled && !stealthMode) {
            AnimatedBackground(currentTheme.currentTheme)
        }
        
        // الشريط العلوي
        TopAppBar(
            title = {
                Text(
                    text = "الثيمات والتخصيص",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "رجوع",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // اختيار الثيم
                ThemeSelectionSection(
                    selectedThemeId = selectedThemeId,
                    onThemeSelect = onThemeSelect
                )
            }
            
            item {
                // الألوان المخصصة
                CustomColorsSection(
                    customColors = customColors,
                    isEnabled = selectedThemeId == CryptaTheme.CUSTOM.id,
                    onColorsChange = onCustomColorsChange
                )
            }
            
            item {
                // إعدادات الأنيميشن
                AnimationSettingsSection(
                    animationSpeed = animationSpeed,
                    onSpeedChange = onAnimationSpeedChange
                )
            }
            
            item {
                // إعدادات التأثيرات
                EffectsSettingsSection(
                    blurEnabled = effectSettings.first,
                    particlesEnabled = effectSettings.second,
                    onEffectChange = onEffectSettingsChange
                )
            }
            
            item {
                // الإعدادات المتقدمة
                AdvancedSettingsSection(
                    stealthMode = stealthMode,
                    dynamicColors = dynamicColors,
                    onStealthModeChange = onStealthModeChange,
                    onDynamicColorsChange = onDynamicColorsChange
                )
            }
            
            item {
                // معاينة الثيم
                ThemePreviewSection()
            }
        }
    }
}

// قسم اختيار الثيم
@Composable
fun ThemeSelectionSection(
    selectedThemeId: String,
    onThemeSelect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "اختيار الثيم",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(CryptaTheme.values()) { theme ->
                    ThemeCard(
                        theme = theme,
                        isSelected = theme.id == selectedThemeId,
                        onClick = { onThemeSelect(theme.id) }
                    )
                }
            }
        }
    }
}

// بطاقة الثيم
@Composable
fun ThemeCard(
    theme: CryptaTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Card(
        modifier = Modifier
            .size(100.dp, 120.dp)
            .scale(scale)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                theme.primaryColor.copy(alpha = 0.8f) 
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 12.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // معاينة الألوان
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(theme.primaryColor, theme.secondaryColor, theme.tertiaryColor).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(color, CircleShape)
                    )
                }
            }
            
            // خلفية مصغرة
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(theme.backgroundColor, theme.surfaceColor)
                        )
                    )
            )
            
            // اسم الثيم
            Text(
                text = theme.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

// قسم الألوان المخصصة
@Composable
fun CustomColorsSection(
    customColors: Triple<Long, Long, Long>,
    isEnabled: Boolean,
    onColorsChange: (Triple<Long, Long, Long>) -> Unit
) {
    AnimatedVisibility(
        visible = isEnabled,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "الألوان المخصصة",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // منتقي اللون الأساسي
                ColorPickerRow(
                    title = "اللون الأساسي",
                    color = Color(customColors.first),
                    onColorChange = { newColor ->
                        onColorsChange(Triple(newColor.value.toLong(), customColors.second, customColors.third))
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // منتقي اللون الثانوي
                ColorPickerRow(
                    title = "اللون الثانوي",
                    color = Color(customColors.second),
                    onColorChange = { newColor ->
                        onColorsChange(Triple(customColors.first, newColor.value.toLong(), customColors.third))
                    }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // منتقي اللون الثالث
                ColorPickerRow(
                    title = "اللون الثالث",
                    color = Color(customColors.third),
                    onColorChange = { newColor ->
                        onColorsChange(Triple(customColors.first, customColors.second, newColor.value.toLong()))
                    }
                )
            }
        }
    }
}

// صف منتقي الألوان
@Composable
fun ColorPickerRow(
    title: String,
    color: Color,
    onColorChange: (Color) -> Unit
) {
    val predefinedColors = listOf(
        Color(0xFF6C63FF), Color(0xFF03DAC6), Color(0xFFFF6B6B),
        Color(0xFF4CAF50), Color(0xFF2196F3), Color(0xFFFF9800),
        Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFF00BCD4),
        Color(0xFFCDDC39), Color(0xFFFF5722), Color(0xFF795548)
    )
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(color, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(predefinedColors) { presetColor ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(presetColor, Circle
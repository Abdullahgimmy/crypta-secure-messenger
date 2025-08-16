// CryptaFileManager.kt
package com.crypta.app.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.collections.ArrayList

// مدير الملفات المشفرة الرئيسي
class CryptaFileManager(private val context: Context) {
    
    private val secureRandom = SecureRandom()
    private var masterKey: ByteArray? = null
    private val encryptedFilesDir = File(context.filesDir, "crypta_encrypted")
    private val tempDir = File(context.cacheDir, "crypta_temp")
    
    companion object {
        private const val CHUNK_SIZE = 8192
        private const val ENCRYPTED_EXTENSION = ".crypta"
        private const val METADATA_EXTENSION = ".crypta_meta"
    }
    
    init {
        // إنشاء المجلدات اللازمة
        if (!encryptedFilesDir.exists()) {
            encryptedFilesDir.mkdirs()
        }
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        
        // تهيئة المفتاح الرئيسي
        initializeMasterKey()
    }
    
    // تهيئة المفتاح الرئيسي للتشفير
    private fun initializeMasterKey() {
        try {
            val keyFile = File(context.filesDir, "crypta_master.key")
            
            if (keyFile.exists()) {
                // قراءة المفتاح الموجود
                masterKey = keyFile.readBytes()
            } else {
                // إنشاء مفتاح جديد باستخدام X25519
                val privateKey = X25519PrivateKeyParameters(secureRandom)
                val publicKey = privateKey.generatePublicKey()
                
                // اشتقاق المفتاح الرئيسي
                val salt = ByteArray(32)
                secureRandom.nextBytes(salt)
                
                val info = "Crypta-File-Encryption-Master-Key-v1".toByteArray()
                val hkdf = HKDFBytesGenerator(SHA256Digest())
                hkdf.init(HKDFParameters(privateKey.encoded, salt, info))
                
                masterKey = ByteArray(32)
                hkdf.generateBytes(masterKey, 0, 32)
                
                // حفظ المفتاح بشكل آمن
                keyFile.writeBytes(masterKey!!)
            }
            
            println("🔑 تم تهيئة المفتاح الرئيسي بنجاح")
        } catch (e: Exception) {
            println("❌ خطأ في تهيئة المفتاح الرئيسي: ${e.message}")
        }
    }
    
    // تشفير ملف
    suspend fun encryptFile(sourceUri: Uri, fileName: String, description: String = ""): EncryptedFileResult {
        return withContext(Dispatchers.IO) {
            try {
                if (masterKey == null) {
                    return@withContext EncryptedFileResult.Error("المفتاح الرئيسي غير متوفر")
                }
                
                val inputStream = context.contentResolver.openInputStream(sourceUri)
                    ?: return@withContext EncryptedFileResult.Error("لا يمكن فتح الملف")
                
                // إنشاء معرف فريد للملف المشفر
                val fileId = UUID.randomUUID().toString()
                val encryptedFile = File(encryptedFilesDir, "$fileId$ENCRYPTED_EXTENSION")
                val metadataFile = File(encryptedFilesDir, "$fileId$METADATA_EXTENSION")
                
                // إنشاء IV عشوائي
                val iv = ByteArray(12)
                secureRandom.nextBytes(iv)
                
                // إعداد التشفير
                val keySpec = SecretKeySpec(masterKey, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
                
                // تشفير الملف
                val outputStream = FileOutputStream(encryptedFile)
                
                // كتابة IV في بداية الملف
                outputStream.write(iv)
                
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var totalBytes = 0L
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            val encryptedChunk = if (bytesRead == CHUNK_SIZE) {
                                cipher.update(buffer)
                            } else {
                                cipher.update(buffer, 0, bytesRead)
                            }
                            
                            if (encryptedChunk != null) {
                                output.write(encryptedChunk)
                            }
                            
                            totalBytes += bytesRead
                        }
                        
                        // كتابة البيانات النهائية
                        val finalChunk = cipher.doFinal()
                        output.write(finalChunk)
                    }
                }
                
                // إنشاء معلومات الملف
                val originalMimeType = context.contentResolver.getType(sourceUri) ?: "application/octet-stream"
                val fileExtension = getFileExtension(fileName)
                
                val metadata = EncryptedFileMetadata(
                    id = fileId,
                    originalName = fileName,
                    description = description,
                    mimeType = originalMimeType,
                    fileExtension = fileExtension,
                    originalSize = totalBytes,
                    encryptedSize = encryptedFile.length(),
                    createdAt = System.currentTimeMillis(),
                    lastModified = System.currentTimeMillis(),
                    checksum = calculateChecksum(encryptedFile)
                )
                
                // حفظ المعلومات المشفرة
                saveEncryptedMetadata(metadataFile, metadata)
                
                EncryptedFileResult.Success(metadata)
                
            } catch (e: Exception) {
                println("❌ خطأ في تشفير الملف: ${e.message}")
                EncryptedFileResult.Error("فشل في تشفير الملف: ${e.message}")
            }
        }
    }
    
    // فك تشفير ملف
    suspend fun decryptFile(fileId: String): DecryptedFileResult {
        return withContext(Dispatchers.IO) {
            try {
                if (masterKey == null) {
                    return@withContext DecryptedFileResult.Error("المفتاح الرئيسي غير متوفر")
                }
                
                val encryptedFile = File(encryptedFilesDir, "$fileId$ENCRYPTED_EXTENSION")
                val metadataFile = File(encryptedFilesDir, "$fileId$METADATA_EXTENSION")
                
                if (!encryptedFile.exists() || !metadataFile.exists()) {
                    return@withContext DecryptedFileResult.Error("الملف غير موجود")
                }
                
                // قراءة المعلومات
                val metadata = loadEncryptedMetadata(metadataFile)
                    ?: return@withContext DecryptedFileResult.Error("لا يمكن قراءة معلومات الملف")
                
                // إنشاء ملف مؤقت لفك التشفير
                val tempFile = File(tempDir, "${UUID.randomUUID()}_${metadata.originalName}")
                
                val inputStream = FileInputStream(encryptedFile)
                
                // قراءة IV
                val iv = ByteArray(12)
                inputStream.read(iv)
                
                // إعداد فك التشفير
                val keySpec = SecretKeySpec(masterKey, "AES")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val gcmSpec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
                
                // فك التشفير
                val outputStream = FileOutputStream(tempFile)
                
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            val decryptedChunk = if (input.available() == 0) {
                                // آخر chunk
                                cipher.doFinal(buffer, 0, bytesRead)
                            } else {
                                cipher.update(buffer, 0, bytesRead)
                            }
                            
                            if (decryptedChunk != null) {
                                output.write(decryptedChunk)
                            }
                        }
                    }
                }
                
                DecryptedFileResult.Success(tempFile, metadata)
                
            } catch (e: Exception) {
                println("❌ خطأ في فك تشفير الملف: ${e.message}")
                DecryptedFileResult.Error("فشل في فك تشفير الملف: ${e.message}")
            }
        }
    }
    
    // الحصول على قائمة الملفات المشفرة
    fun getEncryptedFiles(): List<EncryptedFileMetadata> {
        val files = mutableListOf<EncryptedFileMetadata>()
        
        try {
            val metadataFiles = encryptedFilesDir.listFiles { _, name ->
                name.endsWith(METADATA_EXTENSION)
            } ?: emptyArray()
            
            for (file in metadataFiles) {
                val metadata = loadEncryptedMetadata(file)
                if (metadata != null) {
                    files.add(metadata)
                }
            }
            
            // ترتيب حسب آخر تعديل
            files.sortByDescending { it.lastModified }
            
        } catch (e: Exception) {
            println("❌ خطأ في قراءة قائمة الملفات: ${e.message}")
        }
        
        return files
    }
    
    // حذف ملف مشفر
    fun deleteEncryptedFile(fileId: String): Boolean {
        return try {
            val encryptedFile = File(encryptedFilesDir, "$fileId$ENCRYPTED_EXTENSION")
            val metadataFile = File(encryptedFilesDir, "$fileId$METADATA_EXTENSION")
            
            val encryptedDeleted = if (encryptedFile.exists()) encryptedFile.delete() else true
            val metadataDeleted = if (metadataFile.exists()) metadataFile.delete() else true
            
            encryptedDeleted && metadataDeleted
        } catch (e: Exception) {
            println("❌ خطأ في حذف الملف: ${e.message}")
            false
        }
    }
    
    // حفظ المعلومات المشفرة
    private fun saveEncryptedMetadata(file: File, metadata: EncryptedFileMetadata) {
        try {
            val json = """
                {
                    "id": "${metadata.id}",
                    "originalName": "${metadata.originalName}",
                    "description": "${metadata.description}",
                    "mimeType": "${metadata.mimeType}",
                    "fileExtension": "${metadata.fileExtension}",
                    "originalSize": ${metadata.originalSize},
                    "encryptedSize": ${metadata.encryptedSize},
                    "createdAt": ${metadata.createdAt},
                    "lastModified": ${metadata.lastModified},
                    "checksum": "${metadata.checksum}"
                }
            """.trimIndent()
            
            // تشفير المعلومات
            val encryptedMetadata = encryptString(json)
            file.writeText(encryptedMetadata)
            
        } catch (e: Exception) {
            println("❌ خطأ في حفظ معلومات الملف: ${e.message}")
        }
    }
    
    // قراءة المعلومات المشفرة
    private fun loadEncryptedMetadata(file: File): EncryptedFileMetadata? {
        return try {
            val encryptedContent = file.readText()
            val decryptedJson = decryptString(encryptedContent)
            
            // تحليل JSON بسيط (يمكن استخدام مكتبة JSON)
            parseMetadataJson(decryptedJson)
            
        } catch (e: Exception) {
            println("❌ خطأ في قراءة معلومات الملف: ${e.message}")
            null
        }
    }
    
    // تحليل JSON للمعلومات
    private fun parseMetadataJson(json: String): EncryptedFileMetadata? {
        return try {
            val lines = json.lines().map { it.trim() }
            
            val id = extractJsonValue(lines, "id")
            val originalName = extractJsonValue(lines, "originalName")
            val description = extractJsonValue(lines, "description")
            val mimeType = extractJsonValue(lines, "mimeType")
            val fileExtension = extractJsonValue(lines, "fileExtension")
            val originalSize = extractJsonValue(lines, "originalSize").toLong()
            val encryptedSize = extractJsonValue(lines, "encryptedSize").toLong()
            val createdAt = extractJsonValue(lines, "createdAt").toLong()
            val lastModified = extractJsonValue(lines, "lastModified").toLong()
            val checksum = extractJsonValue(lines, "checksum")
            
            EncryptedFileMetadata(
                id = id,
                originalName = originalName,
                description = description,
                mimeType = mimeType,
                fileExtension = fileExtension,
                originalSize = originalSize,
                encryptedSize = encryptedSize,
                createdAt = createdAt,
                lastModified = lastModified,
                checksum = checksum
            )
            
        } catch (e: Exception) {
            println("❌ خطأ في تحليل معلومات الملف: ${e.message}")
            null
        }
    }
    
    // استخراج قيمة من JSON
    private fun extractJsonValue(lines: List<String>, key: String): String {
        val line = lines.find { it.contains("\"$key\":") }
            ?: throw IllegalArgumentException("Key $key not found")
        
        return line.substringAfter(":").trim()
            .removeSurrounding("\"")
            .removeSuffix(",")
    }
    
    // تشفير نص
    private fun encryptString(text: String): String {
        return try {
            if (masterKey == null) return text
            
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            
            val keySpec = SecretKeySpec(masterKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
            
            val encryptedBytes = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
            
            // دمج IV مع البيانات المشفرة
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)
            
            Base64.encodeToString(combined, Base64.NO_WRAP)
            
        } catch (e: Exception) {
            println("❌ خطأ في تشفير النص: ${e.message}")
            text
        }
    }
    
    // فك تشفير نص
    private fun decryptString(encryptedText: String): String {
        return try {
            if (masterKey == null) return encryptedText
            
            val combined = Base64.decode(encryptedText, Base64.NO_WRAP)
            
            val iv = ByteArray(12)
            System.arraycopy(combined, 0, iv, 0, 12)
            
            val encryptedBytes = ByteArray(combined.size - 12)
            System.arraycopy(combined, 12, encryptedBytes, 0, encryptedBytes.size)
            
            val keySpec = SecretKeySpec(masterKey, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
            
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
            
        } catch (e: Exception) {
            println("❌ خطأ في فك تشفير النص: ${e.message}")
            encryptedText
        }
    }
    
    // حساب checksum للملف
    private fun calculateChecksum(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            
            FileInputStream(file).use { inputStream ->
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            
            Base64.encodeToString(digest.digest(), Base64.NO_WRAP)
            
        } catch (e: Exception) {
            println("❌ خطأ في حساب checksum: ${e.message}")
            ""
        }
    }
    
    // الحصول على امتداد الملف
    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "")
    }
    
    // تنظيف الملفات المؤقتة
    fun cleanupTempFiles() {
        try {
            tempDir.listFiles()?.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            println("❌ خطأ في تنظيف الملفات المؤقتة: ${e.message}")
        }
    }
}

// نماذج البيانات
data class EncryptedFileMetadata(
    val id: String,
    val originalName: String,
    val description: String,
    val mimeType: String,
    val fileExtension: String,
    val originalSize: Long,
    val encryptedSize: Long,
    val createdAt: Long,
    val lastModified: Long,
    val checksum: String
) {
    fun getFormattedSize(): String {
        return formatFileSize(originalSize)
    }
    
    fun getFormattedDate(): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(Date(lastModified))
    }
    
    fun getFileIcon(): ImageVector {
        return when {
            mimeType.startsWith("image/") -> Icons.Default.Image
            mimeType.startsWith("video/") -> Icons.Default.VideoFile
            mimeType.startsWith("audio/") -> Icons.Default.AudioFile
            mimeType.startsWith("text/") -> Icons.Default.TextSnippet
            mimeType.contains("pdf") -> Icons.Default.PictureAsPdf
            else -> Icons.Default.InsertDriveFile
        }
    }
}

// نتائج العمليات
sealed class EncryptedFileResult {
    data class Success(val metadata: EncryptedFileMetadata) : EncryptedFileResult()
    data class Error(val message: String) : EncryptedFileResult()
}

sealed class DecryptedFileResult {
    data class Success(val file: File, val metadata: EncryptedFileMetadata) : DecryptedFileResult()
    data class Error(val message: String) : DecryptedFileResult()
}

// Activity إدارة الملفات
class FileManagerActivity : ComponentActivity() {
    private lateinit var fileManager: CryptaFileManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        fileManager = CryptaFileManager(this)
        
        setContent {
            CryptaTheme {
                FileManagerScreen(
                    fileManager = fileManager,
                    onBack = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        fileManager.cleanupTempFiles()
    }
}

// واجهة إدارة الملفات
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    fileManager: CryptaFileManager,
    onBack: () -> Unit
) {
    var encryptedFiles by remember { mutableStateOf(listOf<EncryptedFileMetadata>()) }
    var isLoading by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var viewMode by remember { mutableStateOf(ViewMode.LIST) }
    var searchQuery by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // تحديث قائمة الملفات
    LaunchedEffect(Unit) {
        loadFiles()
    }
    
    suspend fun loadFiles() {
        encryptedFiles = withContext(Dispatchers.IO) {
            fileManager.getEncryptedFiles()
        }
    }
    
    // مشغل اختيار الملفات
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                val fileName = getFileName(context, uri) ?: "ملف_غير_معروف"
                
                when (val result = fileManager.encryptFile(uri, fileName, "")) {
                    is EncryptedFileResult.Success -> {
                        loadFiles()
                        showUploadDialog = false
                    }
                    is EncryptedFileResult.Error -> {
                        // عرض رسالة خطأ
                        println("خطأ في رفع الملف: ${result.message}")
                    }
                }
                isLoading = false
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
    ) {
        // الشريط العلوي
        TopAppBar(
            title = {
                Text(
                    text = "الملفات المشفرة",
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
            actions = {
                // زر تغيير العرض
                IconButton(
                    onClick = {
                        viewMode = if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
                    }
                ) {
                    Icon(
                        imageVector = if (viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = "تغيير العرض",
                        tint = Color.White
                    )
                }
                
                // زر إضافة ملف
                IconButton(onClick = { showUploadDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "إضافة ملف",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )
        
        // شريط البحث
        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            modifier = Modifier.padding(16.dp)
        )
        
        // قائمة الملفات
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF6C63FF),
                        modifier = Modifier.size(50.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "جارٍ تشفير الملف...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            val filteredFiles = if (searchQuery.isBlank()) {
                encryptedFiles
            } else {
                encryptedFiles.filter { 
                    it.originalName.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
                }
            }
            
            if (filteredFiles.isEmpty()) {
                EmptyFilesList(
                    onAddFile = { showUploadDialog = true }
                )
            } else {
                when (viewMode) {
                    ViewMode.LIST -> {
                        FilesList(
                            files = filteredFiles,
                            onFileClick = { file ->
                                scope.launch {
                                    // فتح الملف أو عرض تفاصيله
                                    openEncryptedFile(context, fileManager, file)
                                }
                            },
                            onFileDelete = { file ->
                                scope.launch {
                                    fileManager.deleteEncryptedFile(file.id)
                                    loadFiles()
                                }
                            }
                        )
                    }
                    ViewMode.GRID -> {
                        FilesGrid(
                            files = filteredFiles,
                            onFileClick = { file ->
                                scope.launch {
                                    openEncryptedFile(context, fileManager, file)
                                }
                            },
                            onFileDelete = { file ->
                                scope.launch {
                                    fileManager.deleteEncryptedFile(file.id)
                                    loadFiles()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // نافذة رفع الملفات
    if (showUploadDialog) {
        UploadDialog(
            onDismiss = { showUploadDialog = false },
            onSelectFile = {
                filePickerLauncher.launch("*/*")
            }
        )
    }
}

// شريط البحث
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
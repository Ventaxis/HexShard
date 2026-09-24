package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.R

import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.example.network.supabase.ChallengeVerifyResult
import com.example.network.supabase.SupabaseAuthService
import com.example.network.supabase.TelegramChallenge
import com.example.ui.theme.SpotifyGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToFaq: () -> Unit,
    onNavigateToPrivacy: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onLogout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val currentLang = LocalAppLanguage.current
    
    val initialUsername = remember { com.example.data.SecurePrefsManager.getUsername(context) }
    val initialPhone = remember { 
        com.example.data.SecurePrefsManager.getPhone(context)
    }
    val initialAvatar = remember { com.example.data.SecurePrefsManager.getAvatarUri(context) }

    var name by remember { 
        val savedName = com.example.data.SecurePrefsManager.getPrefs(context).getString("name", null)
        mutableStateOf(if (!savedName.isNullOrBlank()) savedName else initialUsername)
    }
    var username by remember { mutableStateOf(initialUsername) }
    var phone by remember { mutableStateOf(initialPhone) }
    var avatarUri by remember { mutableStateOf(initialAvatar) }

    var showEditProfile by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showStorageDialog by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTelegramDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showScanQrDialog by remember { mutableStateOf(false) }
    var showCameraScanner by remember { mutableStateOf(false) }
    var scannedProfile by remember { mutableStateOf<com.example.util.ProfileQrData?>(null) }
    var manualQrInput by remember { mutableStateOf("") }
    var qrStatusMessage by remember { mutableStateOf<String?>(null) }

    // Privacy & Backup dialog state
    var backupPassphrase by remember { mutableStateOf("") }
    var isBackupLoading by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    // Notifications state
    var notificationsEnabled by remember { mutableStateOf(com.example.data.SecurePrefsManager.isNotificationsEnabled(context)) }
    var inAppSoundsEnabled by remember { mutableStateOf(com.example.data.SecurePrefsManager.isInAppSoundsEnabled(context)) }

    // Storage state
    var cacheSizeMb by remember {
        mutableStateOf(
            try {
                val bytes = context.cacheDir.walkTopDown().sumOf { it.length() }
                String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            } catch (_: Exception) { "0.0 MB" }
        )
    }

    val accountId = remember { com.example.data.SecurePrefsManager.getAccountId(context) }
    val currentUserId = remember { com.example.data.SecurePrefsManager.getUserId(context) }
    val privateVirtualNumber = remember { com.example.data.SecurePrefsManager.getPrivateVirtualNumber(context) }
    var isTelegramVerified by remember { mutableStateOf(com.example.data.SecurePrefsManager.isTelegramVerified(context)) }
    var tgChallenge by remember { mutableStateOf<TelegramChallenge?>(null) }
    var tgCodeInput by remember { mutableStateOf("") }
    var tgError by remember { mutableStateOf<String?>(null) }
    var isTgLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Profile Background State
    var profileBgPath by remember {
        mutableStateOf(com.example.data.SecurePrefsManager.getProfileBackgroundPath(context, currentUserId))
    }
    var profileBgType by remember {
        mutableStateOf(com.example.data.SecurePrefsManager.getProfileBackgroundType(context, currentUserId))
    }
    var isUploadingBg by remember { mutableStateOf(false) }
    var showBgOptionsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNotBlank()) {
            val bgInfo = com.example.data.repository.ProfileBackgroundManager.syncProfileBackground(context)
            if (bgInfo != null) {
                profileBgPath = bgInfo.storagePath
                profileBgType = bgInfo.mimeType
            }
        }
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploadingBg = true
            scope.launch {
                try {
                    val validation = com.example.data.repository.ProfileBackgroundManager.validateUri(context, uri)
                    if (validation is com.example.data.repository.BackgroundValidationResult.Error) {
                        isUploadingBg = false
                        val msg = when (validation.reason) {
                            com.example.data.repository.ValidationErrorReason.FILE_TOO_LARGE -> strings.fileTooLargeError
                            com.example.data.repository.ValidationErrorReason.UNSUPPORTED_FORMAT -> strings.unsupportedFileFormat
                            else -> validation.details.ifBlank { strings.backgroundUploadFailed }
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val res = com.example.data.repository.ProfileBackgroundManager.uploadProfileBackground(context, uri)
                    res.onSuccess { info ->
                        profileBgPath = info.storagePath
                        profileBgType = info.mimeType
                        isUploadingBg = false
                        Toast.makeText(context, strings.backgroundUpdated, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        isUploadingBg = false
                        Toast.makeText(context, err.message ?: strings.backgroundUploadFailed, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    isUploadingBg = false
                    Toast.makeText(context, e.message ?: strings.backgroundUploadFailed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showTelegramDialog) {
        AlertDialog(
            onDismissRequest = { showTelegramDialog = false },
            title = { Text(strings.telegramBindingTitle) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = strings.telegramBindingDesc,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Account ID Card with Copy button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.accountIdLabel.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = accountId.ifBlank { "—" },
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("HexShard ID", accountId)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, strings.accountIdCopied, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = strings.copyAccountId,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                val url = tgChallenge?.deepLink ?: "https://t.me/HexShardBot?start=$accountId"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "t.me/HexShardBot", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.telegramBotButton)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tgCodeInput,
                        onValueChange = {
                            val digits = it.filter { ch -> ch.isDigit() }
                            if (digits.length <= 6) tgCodeInput = digits
                        },
                        label = { Text(strings.telegramCodePlaceholder) },
                        placeholder = { Text("6-digit code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tgError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(tgError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = tgChallenge?.challengeId ?: accountId
                        if (token.isNotBlank() && tgCodeInput.length == 6) {
                            isTgLoading = true
                            tgError = null
                            scope.launch {
                                when (val res = SupabaseAuthService.verifyTelegramChallenge(token, tgCodeInput, context)) {
                                    is ChallengeVerifyResult.Success -> {
                                        isTelegramVerified = true
                                        showTelegramDialog = false
                                        isTgLoading = false
                                        Toast.makeText(context, strings.telegramConnected, Toast.LENGTH_SHORT).show()
                                    }
                                    is ChallengeVerifyResult.Error -> {
                                        isTgLoading = false
                                        tgError = res.message
                                    }
                                }
                            }
                        }
                    },
                    enabled = tgCodeInput.length == 6 && !isTgLoading
                ) {
                    Text(strings.verifyTelegramCode)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTelegramDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                // Take persistable URI permission if supported
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not applicable for this content URI
            }
            avatarUri = uri.toString()
            com.example.data.SecurePrefsManager.setAvatarUri(context, uri.toString())
        }
    }

    val qrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = android.graphics.BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val decoded = com.example.util.QrCodeManager.decodeQrFromBitmap(bitmap)
                        if (decoded != null) {
                            val token = com.example.util.QrCodeManager.parseProfileToken(decoded)
                            if (token != null) {
                                qrStatusMessage = "Resolving secure profile token..."
                                scope.launch {
                                    val userRepo = com.example.data.repository.UserRepository(context)
                                    val profile = userRepo.resolveProfileShareToken(token)
                                    if (profile != null) {
                                        scannedProfile = profile
                                        qrStatusMessage = "Profile verified successfully via secure token!"
                                    } else {
                                        qrStatusMessage = "Token expired or invalid on server"
                                    }
                                }
                            } else {
                                qrStatusMessage = "Invalid QR: not a valid HexShard v2 tokenized profile"
                            }
                        } else {
                            qrStatusMessage = "No QR code could be detected in this image"
                        }
                    } else {
                        qrStatusMessage = "Unable to read image data"
                    }
                }
            } catch (e: Exception) {
                qrStatusMessage = "Error processing image: ${e.message}"
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPrivacyDialog = false
                backupStatusMessage = null
                backupPassphrase = ""
            },
            title = { Text(strings.privacySecurity, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cryptography & Vault",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "• Peer Protocol: ECDH secp256r1 Key Agreement\n• Content Cipher: AES-256-GCM Authenticated\n• Key Derivation: Deterministic per-session key derivation\n• Local Database: SQLCipher 256-bit AES vault",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    HorizontalDivider()

                    Text(
                        text = "AI Encrypted Backup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Passphrase-protected backup encrypted with PBKDF2 (100,000 rounds) + AES-256-GCM.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Backup Passphrase") },
                        placeholder = { Text("Enter private passphrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (backupStatusMessage != null) {
                        Text(
                            text = backupStatusMessage ?: "",
                            fontSize = 12.sp,
                            color = if (backupStatusMessage?.contains("fail", ignoreCase = true) == true || backupStatusMessage?.contains("error", ignoreCase = true) == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (backupPassphrase.isBlank()) {
                                    backupStatusMessage = "Passphrase cannot be empty"
                                    return@Button
                                }
                                isBackupLoading = true
                                scope.launch {
                                    try {
                                        val db = com.example.data.database.AppDatabase.getDatabase(context)
                                        val res = com.example.crypto.AiBackupManager.exportAiHistory(
                                            context = context,
                                            chatDao = db.chatDao(),
                                            accountId = accountId,
                                            passphrase = backupPassphrase.toCharArray()
                                        )
                                        backupStatusMessage = if (res.success) {
                                            "Exported ${res.messageCount} messages to ${res.filePath?.substringAfterLast('/')}"
                                        } else {
                                            res.message
                                        }
                                    } catch (e: Exception) {
                                        backupStatusMessage = "Export failed: ${e.message}"
                                    } finally {
                                        isBackupLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isBackupLoading && backupPassphrase.isNotBlank()
                        ) {
                            Text("Export", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                if (backupPassphrase.isBlank()) {
                                    backupStatusMessage = "Passphrase cannot be empty"
                                    return@OutlinedButton
                                }
                                isBackupLoading = true
                                scope.launch {
                                    try {
                                        val db = com.example.data.database.AppDatabase.getDatabase(context)
                                        val res = com.example.crypto.AiBackupManager.importAiHistory(
                                            context = context,
                                            chatDao = db.chatDao(),
                                            accountId = accountId,
                                            passphrase = backupPassphrase.toCharArray()
                                        )
                                        backupStatusMessage = if (res.success) {
                                            "Restored ${res.messageCount} messages successfully"
                                        } else {
                                            res.message
                                        }
                                    } catch (e: Exception) {
                                        backupStatusMessage = "Restore failed: ${e.message}"
                                    } finally {
                                        isBackupLoading = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isBackupLoading && backupPassphrase.isNotBlank()
                        ) {
                            Text("Restore", fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { 
                    showPrivacyDialog = false
                    backupStatusMessage = null
                    backupPassphrase = ""
                }) {
                    Text(strings.ok)
                }
            }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text(strings.notifications, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Message Notifications", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Show notifications for incoming messages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = {
                                notificationsEnabled = it
                                com.example.data.SecurePrefsManager.setNotificationsEnabled(context, it)
                            }
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("In-App Sounds", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            Text("Play audio alerts for sent and received messages", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = inAppSoundsEnabled,
                            onCheckedChange = {
                                inAppSoundsEnabled = it
                                com.example.data.SecurePrefsManager.setInAppSoundsEnabled(context, it)
                            }
                        )
                    }

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Cannot open system settings", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("System Notification Settings", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text(strings.ok)
                }
            }
        )
    }

    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            title = { Text(strings.storageData, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Temporary Cache", fontSize = 14.sp)
                        Text(cacheSizeMb, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                                cacheSizeMb = "0.0 MB"
                                Toast.makeText(context, "Cache cleared successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to clear cache: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Temporary Cache")
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Local Vault Storage", fontSize = 14.sp)
                        Text("SQLCipher AES-256", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "Encrypted local SQLite database managed by Android KeyStore master key.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text(strings.ok)
                }
            }
        )
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(strings.selectLanguage) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LocalizationManager.setLanguage(context, AppLanguage.ENGLISH)
                                showLanguage = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLang == AppLanguage.ENGLISH,
                            onClick = {
                                LocalizationManager.setLanguage(context, AppLanguage.ENGLISH)
                                showLanguage = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.english, fontSize = 16.sp, fontWeight = if (currentLang == AppLanguage.ENGLISH) FontWeight.Bold else FontWeight.Normal)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LocalizationManager.setLanguage(context, AppLanguage.RUSSIAN)
                                showLanguage = false
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLang == AppLanguage.RUSSIAN,
                            onClick = {
                                LocalizationManager.setLanguage(context, AppLanguage.RUSSIAN)
                                showLanguage = false
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(strings.russian, fontSize = 16.sp, fontWeight = if (currentLang == AppLanguage.RUSSIAN) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguage = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showEditProfile) {
        var editName by remember { mutableStateOf(name) }
        var editUsername by remember { mutableStateOf(username) }

        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text(strings.editProfile) },
            text = {
                Column {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            .align(Alignment.CenterHorizontally),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(model = avatarUri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AddAPhoto, contentDescription = "Select Photo", modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(
                        text = strings.tapAvatarToChoose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(strings.name) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text(strings.username) },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            showEditProfile = false
                            showBgOptionsDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.profileBackground)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    name = editName
                    username = editUsername
                    com.example.data.SecurePrefsManager.getPrefs(context).edit()
                        .putString("name", name)
                        .putString("username", username)
                        .apply()
                    showEditProfile = false
                }) { Text(strings.save) }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) { Text(strings.cancel) }
            }
        )
    }

    if (showBgOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showBgOptionsDialog = false },
            title = { Text(strings.profileBackground) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (profileBgPath.isNullOrBlank()) strings.setProfileBackground else strings.changeProfileBackground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBgOptionsDialog = false
                        backgroundPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageAndVideo
                            )
                        )
                    }
                ) {
                    Text(if (profileBgPath.isNullOrBlank()) strings.setProfileBackground else strings.changeProfileBackground)
                }
            },
            dismissButton = {
                Row {
                    if (!profileBgPath.isNullOrBlank()) {
                        TextButton(
                            onClick = {
                                showBgOptionsDialog = false
                                scope.launch {
                                    val res = com.example.data.repository.ProfileBackgroundManager.removeProfileBackground(context)
                                    if (res.isSuccess) {
                                        profileBgPath = null
                                        profileBgType = null
                                        Toast.makeText(context, strings.backgroundRemoved, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text(strings.removeProfileBackground, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { showBgOptionsDialog = false }) {
                        Text(strings.cancel)
                    }
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(strings.logout) },
            text = { Text(strings.logoutConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(strings.logout, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(strings.deleteAccount) },
            text = { Text(strings.deleteAccountConfirm) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccount()
                    }
                ) {
                    Text(strings.deleteAccount, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showQrDialog) {
        var qrToken by remember { mutableStateOf<String?>(null) }
        var isGeneratingToken by remember { mutableStateOf(false) }
        var qrError by remember { mutableStateOf<String?>(null) }
        var isRevoking by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            isGeneratingToken = true
            qrError = null
            try {
                val userRepo = com.example.data.repository.UserRepository(context)
                val token = userRepo.createProfileShareToken(7)
                if (token != null) {
                    qrToken = token
                } else {
                    qrError = strings.unableToGenerateQr
                }
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Exception creating profile share token")
                qrError = strings.unableToGenerateQr
            } finally {
                isGeneratingToken = false
            }
        }

        val profileUri = remember(qrToken) {
            qrToken?.let { com.example.util.QrCodeManager.buildProfileUri(it) } ?: ""
        }
        val qrBitmap = remember(profileUri) {
            if (profileUri.isNotBlank()) com.example.util.QrCodeManager.generateQrBitmap(profileUri, 512) else null
        }

        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = SpotifyGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.profileQrCode, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isGeneratingToken) {
                        CircularProgressIndicator(color = SpotifyGreen, modifier = Modifier.padding(24.dp))
                        Text(strings.generatingShareToken, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (qrBitmap != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = strings.myProfileQrCode,
                                modifier = Modifier.size(220.dp).padding(10.dp)
                            )
                        }
                    } else {
                        Text(qrError ?: strings.unableToGenerateQr, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = name.ifBlank { username },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "@$username",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    if (privateVirtualNumber.isNotBlank()) {
                        Text(
                            text = privateVirtualNumber,
                            fontFamily = FontFamily.Monospace,
                            color = SpotifyGreen,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Encrypted v2 token expires in 7 days. Contains no plaintext credentials or identity keys.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRevoking = true
                                val userRepo = com.example.data.repository.UserRepository(context)
                                val success = userRepo.revokeProfileShareTokens()
                                isRevoking = false
                                if (success) {
                                    qrToken = null
                                    Toast.makeText(context, strings.tokensRevoked, Toast.LENGTH_SHORT).show()
                                    showQrDialog = false
                                } else {
                                    Toast.makeText(context, "Failed to revoke tokens", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isRevoking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRevoking) "Revoking..." else strings.revokeActiveTokens, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, profileUri)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share HexShard Profile"))
                    },
                    enabled = profileUri.isNotBlank()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showScanQrDialog) {
        AlertDialog(
            onDismissRequest = {
                showScanQrDialog = false
                scannedProfile = null
                qrStatusMessage = null
                manualQrInput = ""
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = SpotifyGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.scanQrTitle, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCameraScanner = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.scanQrCamera, color = Color.Black, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                qrImagePickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.scanQrGallery, fontSize = 12.sp)
                        }
                    }

                    Text("Or paste profile link / URI:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value = manualQrInput,
                        onValueChange = {
                            manualQrInput = it
                            val token = com.example.util.QrCodeManager.parseProfileToken(it)
                            if (token != null) {
                                qrStatusMessage = strings.resolvingProfileToken
                                scope.launch {
                                    val userRepo = com.example.data.repository.UserRepository(context)
                                    val profile = userRepo.resolveProfileShareToken(token)
                                    if (profile != null) {
                                        scannedProfile = profile
                                        qrStatusMessage = strings.profileResolvedSuccess
                                    } else {
                                        qrStatusMessage = strings.tokenExpiredOrInvalid
                                    }
                                }
                            }
                        },
                        placeholder = { Text("hexshard://profile/v2/<token>", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )

                    if (qrStatusMessage != null) {
                        Text(
                            text = qrStatusMessage ?: "",
                            fontSize = 12.sp,
                            color = if (scannedProfile != null) SpotifyGreen else MaterialTheme.colorScheme.error
                        )
                    }

                    val profileData = scannedProfile
                    if (profileData != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF12121D),
                            border = BorderStroke(1.dp, Color(0xFF252538)),
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = strings.serverVerifiedProfile.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyGreen
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = profileData.displayName.ifBlank { "User" }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Text(text = "ID: ${profileData.userId}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF8B8B9E))
                                if (profileData.hexNumber.isNotBlank()) {
                                    Text(text = "Number: ${profileData.hexNumber}", fontSize = 11.sp, color = SpotifyGreen)
                                }
                                Text(
                                    text = "E2E Identity Key: ${profileData.publicKey.take(16)}...",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF8B8B9E)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = scannedProfile
                        if (p != null) {
                            scope.launch {
                                try {
                                    val userRepo = com.example.data.repository.UserRepository(context)
                                    userRepo.registerVerifiedPeerKey(p.userId, p.publicKey)
                                    Toast.makeText(context, "Contact added!", Toast.LENGTH_SHORT).show()
                                    showScanQrDialog = false
                                    onBack()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to import contact: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = scannedProfile != null
                ) {
                    Text(strings.addContact)
                }
            },
            dismissButton = {
                TextButton(onClick = { showScanQrDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    if (showCameraScanner) {
        CameraQrScannerDialog(
            onDismiss = { showCameraScanner = false },
            onQrScanned = { rawResult ->
                val token = com.example.util.QrCodeManager.parseProfileToken(rawResult)
                if (token != null) {
                    qrStatusMessage = strings.resolvingProfileToken
                    showScanQrDialog = true
                    scope.launch {
                        val userRepo = com.example.data.repository.UserRepository(context)
                        val profile = userRepo.resolveProfileShareToken(token)
                        if (profile != null) {
                            scannedProfile = profile
                            qrStatusMessage = strings.profileResolvedSuccess
                        } else {
                            qrStatusMessage = strings.tokenExpiredOrInvalid
                        }
                    }
                } else {
                    Toast.makeText(context, strings.invalidQrFormat, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Hero Header with Background (WebM video or Image) behind avatar, name, username, and status
            ProfileBackgroundHeader(
                name = name,
                username = username,
                phone = phone,
                privateVirtualNumber = privateVirtualNumber,
                avatarUri = avatarUri,
                isTelegramVerified = isTelegramVerified,
                backgroundPath = profileBgPath,
                backgroundType = profileBgType,
                isUploadingBackground = isUploadingBg,
                onAvatarClick = { showEditProfile = true },
                onBackgroundActionClick = { showBgOptionsDialog = true }
            )

            // Cryptographic Identity & Telegram Bot Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF12121D)),
                border = BorderStroke(1.dp, Color(0xFF252538))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.accountIdLabel.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1DB954),
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = accountId.ifBlank { "—" },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (accountId.isNotBlank()) {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Account ID", accountId))
                                    Toast.makeText(context, strings.accountIdCopied, Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = strings.copyAccountId,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = { showQrDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Show QR Code",
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = strings.accountIdDescription,
                        fontSize = 11.sp,
                        color = Color(0xFF8B8B9E),
                        lineHeight = 15.sp
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF222232))

                    // Telegram 2FA Gateway Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Telegram (@HexShardBot)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = if (isTelegramVerified) strings.telegramConnected else strings.telegramNotConnected,
                                fontSize = 12.sp,
                                color = if (isTelegramVerified) Color(0xFF1DB954) else Color(0xFF8B8B9E)
                            )
                        }
                        if (!isTelegramVerified) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        tgChallenge = SupabaseAuthService.createTelegramChallenge(accountId, context)
                                        tgCodeInput = ""
                                        tgError = null
                                        showTelegramDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2990D6)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(strings.connectTelegram, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ACCOUNT
            SettingsGroupHeader(strings.account)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsItem(icon = Icons.Default.Person, title = strings.profile, onClick = { showEditProfile = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.Wallpaper, title = strings.profileBackground, onClick = { showBgOptionsDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.QrCode, title = "My Profile QR Code", onClick = { showQrDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.QrCodeScanner, title = "Scan / Import Contact QR", onClick = { showScanQrDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.Lock, title = strings.privacySecurity, onClick = { showPrivacyDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.Notifications, title = strings.notifications, onClick = { showNotificationsDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.AutoMirrored.Filled.ExitToApp, title = strings.logout, onClick = { showLogoutDialog = true })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.DeleteForever, title = strings.deleteAccount, titleColor = MaterialTheme.colorScheme.error, onClick = { showDeleteDialog = true })
                }
            }

            // APP
            SettingsGroupHeader(strings.app)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = strings.language,
                        subtitle = if (currentLang == AppLanguage.RUSSIAN) strings.russian else strings.english,
                        onClick = { showLanguage = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.Storage, title = strings.storageData, onClick = { showStorageDialog = true })
                }
            }
            
            // SUPPORT
            SettingsGroupHeader(strings.support)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsItem(icon = Icons.Default.HelpOutline, title = strings.helpCenter, onClick = onNavigateToFaq)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.BugReport, title = strings.reportProblem, onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:supportventaxiscorp@gmail.com")
                            putExtra(Intent.EXTRA_SUBJECT, "Bug Report: HexShard")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                    })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.Email, title = strings.contactSupport, onClick = {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:supportventaxiscorp@gmail.com")
                        }
                        context.startActivity(Intent.createChooser(intent, "Send Email"))
                    })
                }
            }
            
            // ABOUT
            SettingsGroupHeader(strings.about)
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    SettingsItem(icon = Icons.Default.Security, title = strings.privacyPolicy, onClick = onNavigateToPrivacy)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsItem(icon = Icons.Default.Gavel, title = strings.termsService, onClick = onNavigateToTerms)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = strings.aboutDescription,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(strings.version, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.developer, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.leadDeveloper, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(strings.copyright, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(24.dp))
        Column {
            Text(
                text = title,
                fontSize = 16.sp,
                color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(text = subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}



package com.example.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.database.AnnouncementEntity
import com.example.data.database.ChannelEntity
import com.example.ui.viewmodel.AnnouncementViewModel

/**
 * GÖREV VE AMAÇ (MainAppScreen.kt açıklamaları):
 * Bu dosya uygulamamızın en geniş görsel arayüz (UI) bileşenlerini içerir.
 * Klasik Android projelerinde yer alan "res/layout/activity_main.xml" dosyalarının görevini tam olarak üstlenir.
 * Tamamen Jetpack Compose (Kotlin) ile yazılmış olup modern, dinamik ve esnek bir tasarıma sahiptir.
 *
 * EKRANLAR (Tabs) ve VERİ AKIŞLARI:
 * 1. BroadcastTab (Duyuru Gönder): seçilen aktif odalara metin + görsel dosya/link duyurusu çıkarır.
 * 2. IntegrationTab (Oda Ekle/Ayarlar): Telegram Bot Token, Discord Webhook ve Slack Webhook bilgilerini yönetir (SQLite'ta saklar).
 * 3. HistoryTab (Yayın Geçmişi): Gönderilmiş olan eski duyuru kayıtlarını ve gönderim sonuç raporlarını SQLite listesi olarak sunar.
 */
enum class Screen(val title: String, val icon: ImageVector) {
    BROADCAST("Duyuru Gönder", Icons.Default.Send),
    INTEGRATION("Sohbet Ekle/Ayarlar", Icons.Default.Settings),
    HISTORY("Yayın Geçmişi", Icons.Default.List)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: AnnouncementViewModel, modifier: Modifier = Modifier) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val historyLog by viewModel.history.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(Screen.BROADCAST) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "OmniAnnounce",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Çoklu Sohbet Odası Yayıncı Paneli",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                windowInsets = WindowInsets.navigationBars
            ) {
                Screen.values().forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        label = { Text(screen.title, fontSize = 11.sp) },
                        icon = { Icon(screen.icon, contentDescription = screen.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            AnimatedContent(
                targetState = currentScreen,
                label = "ScreenTransition",
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                }
            ) { targetScreen ->
                when (targetScreen) {
                    Screen.BROADCAST -> BroadcastTab(viewModel, uiState, channels, onNavigateToChannels = {
                        currentScreen = Screen.INTEGRATION
                    })
                    Screen.INTEGRATION -> IntegrationTab(viewModel, uiState, channels)
                    Screen.HISTORY -> HistoryTab(viewModel, historyLog)
                }
            }
        }
    }

    // Beautiful Dialog with detailed broadcast summary
    if (uiState.broadcastSuccess != null) {
        Dialog(onDismissRequest = { viewModel.dismissBroadcastResult() }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val statusIcon = if (uiState.broadcastSuccess == true) Icons.Default.CheckCircle else Icons.Default.Warning
                    val statusColor = if (uiState.broadcastSuccess == true) Color(0xFF4CAF50) else Color(0xFFF44336)
                    val statusTitle = if (uiState.broadcastSuccess == true) "Yayın Başarılı" else "Kısmi Hata/Rapor"

                    Icon(
                        imageVector = statusIcon,
                        contentDescription = "Durum",
                        tint = statusColor,
                        modifier = Modifier.size(64.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = statusTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Show dynamic results from SQLite
                    Text(
                        text = uiState.broadcastResultMessage,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { viewModel.dismissBroadcastResult() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kapat")
                    }
                }
            }
        }
    }
}

/**
 * GÖREV/AMAÇ: Tanımlı kanalları seçip duyuru metni girmeyi ve görsel (galeriden veya link olarak) iliştirip göndermeyi sağlayan ama sekmedir.
 * NEREDEN BESLENİYOR:
 * - Kanal Listesi: SQLite veritabanındaki aktif oda tanımlarından alınır.
 * - Seçim ve metin durumları: AnnouncementViewModel'deki MutableStateFlow akışlarından anlık beslenir.
 */
@Composable
fun BroadcastTab(
    viewModel: AnnouncementViewModel,
    uiState: com.example.ui.viewmodel.BroadcastUiState,
    channels: List<ChannelEntity>,
    onNavigateToChannels: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedIds by viewModel.selectedChannelIds.collectAsStateWithLifecycle()

    // Galeriden (cihaz depolama alanından) görsel seçmek için kullanılan modern Android Activity sonucu tetikleyicisi
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                // Seçilen yerel görselin akışını (InputStream) açıp byte dizisine döküyoruz
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    val bytes = inputStream.readBytes()
                    viewModel.setLocalAttachment(it.toString(), bytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        // Targets selection card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mesaj Gönderilecek Kanallar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    if (channels.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                onClick = { viewModel.selectAllChannels() }
                            ) {
                                Text("Tümünü Seç", fontSize = 11.sp)
                            }
                            TextButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                onClick = { viewModel.deselectAllChannels() }
                            ) {
                                Text("Seçimi Temizle", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (channels.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Kayıtlı sohbet kanalı bulunmamaktadır.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onNavigateToChannels,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sohbet / Oda Ekle", fontSize = 12.sp)
                        }
                    }
                } else {
                    // List channels with checkboxes
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        channels.forEach { channel ->
                            val isSelected = selectedIds.contains(channel.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleChannelSelection(channel.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleChannelSelection(channel.id) },
                                    modifier = Modifier.testTag("checkbox_${channel.id}")
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (channel.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = if (channel.platformType == "Telegram") "Telegram Chat: ${channel.telegramChatId}" else "Webhook: ${channel.webhookUrl.take(40)}...",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DynamicPlatformBadge(platform = channel.platformType)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Text entry input
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "Düzenle",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duyuru İçeriği",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.announcementText,
                    onValueChange = { viewModel.onAnnouncementTextChange(it) },
                    placeholder = { Text("Seçilen tüm sohbet odalarına aynı anda iletilecek duyuru metnini yazın...", fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .testTag("announcement_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    enabled = !uiState.isBroadcasting
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Seçilenler: ${selectedIds.size} / ${channels.size} • Karakter: ${uiState.announcementText.length}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.announcementText.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onAnnouncementTextChange("") },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // GÖRSEL EKLEME PANELİ (Opsiyonel)
        // Görevi: Yayınlanacak duyuruya internetten çekilen bir link veya doğrudan galeriden yerel resim eklenmesini koordine eder.
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Görsel Seçimi",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duyuru Görseli (Opsiyonel)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (uiState.attachmentUriString != null) {
                    // Yerel seçilen görsel için önizleme ve temizleme ekranı
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AsyncImage(
                            model = uiState.attachmentUriString,
                            contentDescription = "Yerel Önizleme",
                            modifier = Modifier
                                .height(130.dp)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "✓ Galeriden görsel eklendi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF43A047)
                            )
                            TextButton(
                                onClick = { viewModel.removeAttachment() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Görseli Kaldır", fontSize = 12.sp)
                            }
                        }
                    }
                } else if (uiState.attachmentUrl.isNotBlank()) {
                    // Web adresi girildiğinde önizleme ve düzenleme alanı
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.attachmentUrl,
                            onValueChange = { viewModel.onAttachmentUrlChange(it) },
                            placeholder = { Text("https://example.com/gorsel.jpg") },
                            label = { Text("Görsel Web Linki (URL)", fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.removeAttachment() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Kaldır")
                                }
                            }
                        )

                        if (uiState.attachmentUrl.startsWith("http")) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AsyncImage(
                                model = uiState.attachmentUrl,
                                contentDescription = "Web Link Önizlemesi",
                                modifier = Modifier
                                    .height(130.dp)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                    }
                } else {
                    // Hiçbir görsel seçilmediğinde çıkacak seçim butonları
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galeriden Seç", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { viewModel.onAttachmentUrlChange("https://") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("URL Bağlantısı Ekle", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        val hasSelectedSlack = channels.any { it.platformType == "Slack" && selectedIds.contains(it.id) }
        val hasLocalAttachment = uiState.attachmentBytes != null

        if (hasSelectedSlack && hasLocalAttachment) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Uyarı",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Slack Görsel Uyarısı ⚠️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Seçili kanallarınız arasında Slack bulunmaktadır. Slack Webhook yapısı doğrudan cep telefonunuzdan resim göndermeyi (yerel görsel yüklemeyi) desteklemez.\n\nEğer Slack kanalınızda görsel görünmesini istiyorsanız, resmi internete yükleyip linkini 'URL Bağlantısı Ekle' seçeneğiyle ekleyebilirsiniz. Yerel görsel seçerek gönderirseniz, duyuru Slack'e sadece yazılı metin olarak gidecek, Telegram/Discord kanallarına ise resimli olarak ulaştırılacaktır.",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Publish trigger
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.broadcastAnnouncement()
            },
            enabled = uiState.announcementText.isNotBlank() && selectedIds.isNotEmpty() && !uiState.isBroadcasting,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("broadcast_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (uiState.isBroadcasting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Paylaşılıyor...", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Seçilen Sohbetlerde Yayınla", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun DynamicPlatformBadge(platform: String) {
    val color = when (platform) {
        "Telegram" -> Color(0xFF229ED9)
        "Discord" -> Color(0xFF5865F2)
        "Slack" -> Color(0xFFE01E5A)
        else -> Color.Gray
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = platform,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntegrationTab(
    viewModel: AnnouncementViewModel,
    uiState: com.example.ui.viewmodel.BroadcastUiState,
    channels: List<ChannelEntity>
) {
    var showsForm by remember { mutableStateOf(false) }
    
    // Form fields hold state for adding or editing
    var editingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var inputName by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("Telegram") } // Telegram, Discord, Slack
    var showTelegramHelp by remember { mutableStateOf(false) }
    
    // Fields for Telegram
    var txtTelegramToken by remember { mutableStateOf("") }
    var txtTelegramChatId by remember { mutableStateOf("") }
    
    // Fields for Webhook
    var txtWebhookUrl by remember { mutableStateOf("") }

    // Helper functions
    val resetForm = {
        editingChannel = null
        inputName = ""
        selectedPlatform = "Telegram"
        txtTelegramToken = ""
        txtTelegramChatId = ""
        txtWebhookUrl = ""
        showTelegramHelp = false
        viewModel.clearTestResult()
        showsForm = false
    }

    val openEditForm = { channel: ChannelEntity ->
        editingChannel = channel
        inputName = channel.name
        selectedPlatform = channel.platformType
        txtTelegramToken = channel.telegramToken
        txtTelegramChatId = channel.telegramChatId
        txtWebhookUrl = channel.webhookUrl
        showTelegramHelp = false
        viewModel.clearTestResult()
        showsForm = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sohbet Odası Bağlantıları",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!showsForm) {
                Button(
                    onClick = {
                        resetForm()
                        showsForm = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Yeni Oda", fontSize = 12.sp)
                }
            }
        }

        // ADD / EDIT FORM
        if (showsForm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (editingChannel == null) "Yeni Sohbet Odası Tanımla" else "Odayı Güncelle: ${editingChannel?.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Platform Selection Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Telegram", "Discord", "Slack").forEach { typ ->
                            val isSel = selectedPlatform == typ
                            val color = when (typ) {
                                "Telegram" -> Color(0xFF229ED9)
                                "Discord" -> Color(0xFF5865F2)
                                "Slack" -> Color(0xFFE01E5A)
                                else -> Color.Gray
                            }
                            
                            OutlinedButton(
                                onClick = { selectedPlatform = typ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) color.copy(alpha = 0.15f) else Color.Transparent,
                                    contentColor = if (isSel) color else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = if (isSel) ButtonDefaults.outlinedButtonBorder.copy(width = 2.dp) else ButtonDefaults.outlinedButtonBorder
                            ) {
                                Text(typ, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Channel Alias Name
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("Mecra/Oda Takma Adı", fontSize = 13.sp) },
                        placeholder = { Text("E.g., Mühendislik Grubu, Genel Duyuru") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Context Based Fields
                    if (selectedPlatform == "Telegram") {
                        OutlinedTextField(
                            value = txtTelegramToken,
                            onValueChange = { txtTelegramToken = it },
                            label = { Text("Telegram Bot Token", fontSize = 13.sp) },
                            placeholder = { Text("612345678:AAFdff_...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = txtTelegramChatId,
                            onValueChange = { txtTelegramChatId = it },
                            label = { Text("Chat veya Kanal ID", fontSize = 13.sp) },
                            placeholder = { Text("-10023456789 veya @kanal_adi") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            onClick = { showTelegramHelp = !showTelegramHelp },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sayısal ID'yi Bulmak Çok Kolay! 💡",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Text(
                                        text = if (showTelegramHelp) "▲" else "▼",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                AnimatedVisibility(visible = showTelegramHelp) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        Text(
                                            text = "Grup veya Kanalınızın ID'sini çözmek için aşağıdaki pratik yolları izleyebilirsiniz:",
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Column {
                                                Text(
                                                    text = "1. @Kullanıcı Adı Kullanın (En Kolayı)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Eğer grubunuz veya kanalınız 'Herkese Açık (Public)' ise, sayısal ID yazmanıza gerek yoktur! Direkt @kanal_adi (örneğin @duyurulariniz) yazıp kaydedebilirsiniz.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 14.sp
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "2. Bot İle ID Öğrenin (Sadece 10 Saniye)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Herhangi bir grubun ID'sini öğrenmek için, grubunuza geçici olarak @RawDataBot veya @GetMyChatID_Bot ekleyin. Grupta sadece /id yazın; bot size grubu temsil eden sayıyı (örn: -10023456789) söyleyecektir. ID'yi buraya kopyaladıktan sonra botu gruptan silebilirsiniz.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 14.sp
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "3. Web Sürümü Linkinden Bulma",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Tarayıcıda Telegram Web'den (web.telegram.org) gruba tıkladığınızda, üstteki adres çubuğunda (URL) yer alan sayılar (örn: '2234056711') o sohbetin ID'sidir. Başına -100 ekleyip (örn: -1002234056711) kullanabilirsiniz.",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Discord/Slack webhook field
                        OutlinedTextField(
                            value = txtWebhookUrl,
                            onValueChange = { txtWebhookUrl = it },
                            label = { Text("Webhook URL Adresi", fontSize = 13.sp) },
                            placeholder = { Text("https://...") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Status and Test Connection row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                when (selectedPlatform) {
                                    "Telegram" -> viewModel.testTelegramConnection(txtTelegramToken, txtTelegramChatId)
                                    "Discord" -> viewModel.testDiscordConnection(txtWebhookUrl)
                                    "Slack" -> viewModel.testSlackConnection(txtWebhookUrl)
                                }
                            },
                            enabled = !uiState.isTesting && (
                                if (selectedPlatform == "Telegram") txtTelegramToken.isNotBlank() && txtTelegramChatId.isNotBlank()
                                else txtWebhookUrl.startsWith("http")
                            ),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            if (uiState.isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Text("Bağlantıyı Test Et", fontSize = 12.sp)
                            }
                        }

                        IconButton(
                            onClick = resetForm,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Vazgeç")
                        }
                    }

                    uiState.testResult?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (it.contains("Hata")) Color(0xFFE53935) else Color(0xFF43A047),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Persist Buttons
                    Button(
                        onClick = {
                            if (inputName.isBlank()) return@Button
                            viewModel.saveChannel(
                                ChannelEntity(
                                    id = editingChannel?.id ?: 0,
                                    name = inputName,
                                    platformType = selectedPlatform,
                                    telegramToken = txtTelegramToken,
                                    telegramChatId = txtTelegramChatId,
                                    webhookUrl = txtWebhookUrl,
                                    isEnabled = editingChannel?.isEnabled ?: true
                                )
                            )
                            resetForm()
                        },
                        enabled = inputName.isNotBlank() && (
                            if (selectedPlatform == "Telegram") txtTelegramToken.isNotBlank() && txtTelegramChatId.isNotBlank()
                            else txtWebhookUrl.isNotBlank()
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (editingChannel == null) "Sohbet Odasını Kaydet" else "Değişiklikleri Güncelle")
                    }
                }
            }
        }

        // CURRENT CHANNELS LISTED
        Text(
            text = "Tanımlı Sohbet Odaları (${channels.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lütfen yayın duyurusu göndermek istediğiniz Telegram, Discord veya Slack sohbet odalarını eklemek için yukarıdaki form panelini doldurun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                channels.forEach { channel ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val color = when (channel.platformType) {
                                        "Telegram" -> Color(0xFF229ED9)
                                        "Discord" -> Color(0xFF5865F2)
                                        "Slack" -> Color(0xFFE01E5A)
                                        else -> Color.Gray
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = color,
                                        modifier = Modifier.size(12.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = channel.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { openEditForm(channel) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { viewModel.deleteChannel(channel) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Platform: ${channel.platformType}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (channel.platformType == "Telegram") {
                                Text(text = "ID: ${channel.telegramChatId}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = "Token: •••••", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text(text = "Web Hook: ${channel.webhookUrl.take(50)}...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (channel.isEnabled) "Durum: Aktif" else "Durum: Devre Dışı",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (channel.isEnabled) Color(0xFF43A047) else Color(0xFF757575)
                                )

                                Switch(
                                    checked = channel.isEnabled,
                                    onCheckedChange = { viewModel.toggleChannelEnabled(channel) },
                                    thumbContent = if (channel.isEnabled) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryTab(viewModel: AnnouncementViewModel, historyLog: List<AnnouncementEntity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yayın Arşivi (${historyLog.size} Mesaj)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (historyLog.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearHistory() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Arşivi Temizle", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Arşivi Temizle", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (historyLog.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Henüz Yayın Bulunmuyor",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Gönderdiğiniz tüm duyurular burada arşivlenir ve başarı durumları saklanır.",
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyLog, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_item_${log.id}"),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val relativeTime = DateUtils.getRelativeTimeSpanString(
                                log.timestamp,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                            ).toString()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = relativeTime,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                IconButton(
                                    onClick = { viewModel.onAnnouncementTextChange(log.message) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Tekrar Hazırla",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = log.message,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Gönderim Raporu:\n${log.resultSummary}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelBadge(name: String, enabled: Boolean, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) color.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.12f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (enabled) color else Color.Gray,
                modifier = Modifier.size(8.dp)
            ) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) color else Color.Gray
            )
        }
    }
}

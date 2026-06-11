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
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
 * 2. IntegrationTab (Oda Ekle/Ayarlar): Telegram Bot Token ve Slack Webhook bilgilerini yönetir (SQLite'ta saklar).
 * 3. HistoryTab (Yayın Geçmişi): Gönderilmiş olan eski duyuru kayıtlarını ve gönderim sonuç raporlarını SQLite listesi olarak sunar.
 */
enum class Screen(val title: String, val icon: ImageVector) {
    BROADCAST("Duyuru Gönder", Icons.AutoMirrored.Filled.Send),
    INTEGRATION("Sohbet Ekle/Ayarlar", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: AnnouncementViewModel, modifier: Modifier = Modifier) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200L)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen()
    } else {
        val channels by viewModel.channels.collectAsStateWithLifecycle()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()

        var currentScreen by remember { mutableStateOf(Screen.BROADCAST) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "HadiPaylaş",
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
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
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

    // Genel hata mesajı diyaloğu (DB hataları, görsel yükleme hataları vb.)
    uiState.errorMessage?.let { errorMsg ->
        Dialog(onDismissRequest = { viewModel.dismissError() }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Hata",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Hata",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.dismissError() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tamam", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
    }
}
}

@Composable
fun SplashScreen() {
    // Background gradient matching the premium brand colors
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFFCFEFF),
            Color(0xFFF0F5FF),
            Color(0xFFE3EDFD)
        )
    )

    // Logo pulsing scale animation for a living look
    val infiniteTransition = rememberInfiniteTransition(label = "LogoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScaleSweep"
    )

    // State for staggered texts fade-in
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        // Decorative background elements representing the smooth visual waves in the brand asset
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .offset(x = (-60).dp, y = (-60).dp)
                    .background(Color(0x0A8E2DE2), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(310.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 90.dp, y = 90.dp)
                    .background(Color(0x0600E676), CircleShape)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant centered circle logo container
            Surface(
                modifier = Modifier
                    .size(160.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 10.dp,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_background),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                        Image(
                            painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Text animations
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(900)) + expandVertically(animationSpec = tween(900)),
                exit = fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // App brand name
                    Text(
                        text = "haydi paylaş",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E355B), // Elegant brand blue-slate text color
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // App description
                    Text(
                        text = "Çoklu Sohbet Odası Yayıncı Paneli",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6B7A99),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color(0xFF3A7BD5)
                    )
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

    // State for searching and filtering the target channels list
    var channelSearchQuery by remember { mutableStateOf("") }
    var isTelegramExpanded by remember { mutableStateOf(true) }
    var isSlackExpanded by remember { mutableStateOf(true) }

    val filteredChannels = remember(channels, channelSearchQuery) {
        if (channelSearchQuery.isBlank()) {
            channels
        } else {
            channels.filter {
                it.name.contains(channelSearchQuery, ignoreCase = true) ||
                it.platformType.contains(channelSearchQuery, ignoreCase = true)
            }
        }
    }

    // Galeriden (cihaz depolama alanından) görsel seçmek için kullanılan modern Android Activity sonucu tetikleyicisi
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                // Seçilen yerel görselin akışını (InputStream) açıp byte dizisine döküyoruz
                val stream = context.contentResolver.openInputStream(it)
                if (stream != null) {
                    stream.use { inputStream ->
                        val bytes = inputStream.readBytes()
                        viewModel.setLocalAttachment(it.toString(), bytes)
                    }
                } else {
                    viewModel.onImageLoadError("Dosya akışı açılamadı (null InputStream)")
                }
            } catch (e: SecurityException) {
                viewModel.onImageLoadError("Dosya erişim izni reddedildi: ${e.localizedMessage}")
            } catch (e: java.io.IOException) {
                viewModel.onImageLoadError("Dosya okuma hatası: ${e.localizedMessage}")
            } catch (e: OutOfMemoryError) {
                viewModel.onImageLoadError("Görsel çok büyük, bellek yetersiz")
            } catch (e: Exception) {
                viewModel.onImageLoadError(e.localizedMessage ?: "Bilinmeyen hata")
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Mesaj Gönderilecek Kanallar",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    if (channels.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tümünü Seç Butonu (Harika görünümlü, ikonlu ve çerçeveli pill butonu)
                            Surface(
                                modifier = Modifier
                                    .clickable { viewModel.selectAllChannels() },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.22f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Tümünü Seç",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Seçimi Temizle Butonu (Harika görünümlü, ikonlu ve çerçeveli pill butonu)
                            Surface(
                                modifier = Modifier
                                    .clickable { viewModel.deselectAllChannels() },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White.copy(alpha = 0.10f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Seçimi Temizle",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onNavigateToChannels,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sohbet / Oda Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Search bar for filtering channels
                    OutlinedTextField(
                        value = channelSearchQuery,
                        onValueChange = { channelSearchQuery = it },
                        placeholder = { Text("Kanal adına veya platforma göre ara...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Ara", modifier = Modifier.size(20.dp), tint = Color.White)
                        },
                        trailingIcon = {
                            if (channelSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { channelSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(18.dp), tint = Color.White)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (filteredChannels.isEmpty()) {
                        Text(
                            text = "Aramanıza uygun sohbet odası bulunamadı. 🔍",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    } else {
                        // Group channels by their platform types
                        val telegramChannels = filteredChannels.filter { it.platformType == "Telegram" }
                        val slackChannels = filteredChannels.filter { it.platformType == "Slack" }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (telegramChannels.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedTelegramCount = telegramChannels.count { selectedIds.contains(it.id) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isTelegramExpanded = !isTelegramExpanded }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            DynamicPlatformBadge(platform = "Telegram")
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Telegram Odaları (${telegramChannels.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            if (selectedTelegramCount > 0) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color.White.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "$selectedTelegramCount seçili",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isTelegramExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isTelegramExpanded) "Daralt" else "Genişlet",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isTelegramExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            telegramChannels.forEach { channel ->
                                                val isSelected = selectedIds.contains(channel.id)
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.toggleChannelSelection(channel.id) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = 1.dp,
                                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isSelected,
                                                            onCheckedChange = { viewModel.toggleChannelSelection(channel.id) },
                                                            modifier = Modifier.testTag("checkbox_${channel.id}"),
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = Color.White,
                                                                uncheckedColor = Color.White.copy(alpha = 0.6f),
                                                                checkmarkColor = MaterialTheme.colorScheme.primary
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = channel.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = Color.White
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Telegram Chat ID: ${channel.telegramChatId}",
                                                                fontSize = 11.sp,
                                                                color = Color.White.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                        DynamicPlatformBadge(platform = channel.platformType)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (slackChannels.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val selectedSlackCount = slackChannels.count { selectedIds.contains(it.id) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isSlackExpanded = !isSlackExpanded }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            DynamicPlatformBadge(platform = "Slack")
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Slack Odaları (${slackChannels.size})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color.White
                                            )
                                            if (selectedSlackCount > 0) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color.White.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "$selectedSlackCount seçili",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isSlackExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isSlackExpanded) "Daralt" else "Genişlet",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    AnimatedVisibility(
                                        visible = isSlackExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp, bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            slackChannels.forEach { channel ->
                                                val isSelected = selectedIds.contains(channel.id)
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.toggleChannelSelection(channel.id) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        width = 1.dp,
                                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f)
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Checkbox(
                                                            checked = isSelected,
                                                            onCheckedChange = { viewModel.toggleChannelSelection(channel.id) },
                                                            modifier = Modifier.testTag("checkbox_${channel.id}"),
                                                            colors = CheckboxDefaults.colors(
                                                                checkedColor = Color.White,
                                                                uncheckedColor = Color.White.copy(alpha = 0.6f),
                                                                checkmarkColor = MaterialTheme.colorScheme.primary
                                                            )
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = channel.name,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 14.sp,
                                                                color = Color.White
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "Webhook: ${channel.webhookUrl.take(40)}...",
                                                                fontSize = 11.sp,
                                                                color = Color.White.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                        DynamicPlatformBadge(platform = channel.platformType)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Create,
                        contentDescription = "Düzenle",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duyuru İçeriği",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.announcementText,
                    onValueChange = { viewModel.onAnnouncementTextChange(it) },
                    placeholder = { Text("Seçilen tüm sohbet odalarına aynı anda iletilecek duyuru metnini yazın...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .testTag("announcement_input"),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    enabled = !uiState.isBroadcasting,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    )
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
                        color = Color.White.copy(alpha = 0.8f)
                    )

                    if (uiState.announcementText.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onAnnouncementTextChange("") },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Temizle", modifier = Modifier.size(18.dp), tint = Color.White)
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
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Görsel Seçimi",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Duyuru Görseli (Opsiyonel)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White
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
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
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
                                color = Color(0xFF81C784)
                            )
                            TextButton(
                                onClick = { viewModel.removeAttachment() },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                            ) {
                                Text("Görseli Kaldır", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (uiState.attachmentUrl.isNotBlank()) {
                    // Web adresi girildiğinde önizleme ve düzenleme alanı
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = uiState.attachmentUrl,
                            onValueChange = { viewModel.onAttachmentUrlChange(it) },
                            placeholder = { Text("https://example.com/gorsel.jpg", color = Color.White.copy(alpha = 0.6f)) },
                            label = { Text("Görsel Web Linki (URL)", fontSize = 13.sp, color = Color.White) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { viewModel.removeAttachment() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Kaldır", tint = Color.White)
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                            )
                        )

                        if (uiState.attachmentUrl.startsWith("http")) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AsyncImage(
                                model = uiState.attachmentUrl,
                                contentDescription = "Web Link Önizlemesi",
                                modifier = Modifier
                                    .height(130.dp)
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
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
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Galeriden Seç", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.onAttachmentUrlChange("https://") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Create, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("URL Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            text = "Seçili kanallarınız arasında Slack bulunmaktadır. Slack Webhook yapısı doğrudan cep telefonunuzdan resim göndermeyi (yerel görsel yüklemeyi) desteklemez.\n\nEğer Slack kanalınızda görsel görünmesini istiyorsanız, resmi internete yükleyip linkini 'URL Bağlantısı Ekle' seçeneğiyle ekleyebilirsiniz. Yerel görsel seçerek gönderirseniz, duyuru Slack'e sadece yazılı metin olarak gidecek, Telegram kanallarına ise resimli olarak ulaştırılacaktır.",
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
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
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
    var isTelegramSettingsExpanded by remember { mutableStateOf(true) }
    var isSlackSettingsExpanded by remember { mutableStateOf(true) }
    
    // Form fields hold state for adding or editing
    var editingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var inputName by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf("Telegram") } // Telegram, Slack
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
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (editingChannel == null) "Yeni Sohbet Odası Tanımla" else "Odayı Güncelle: ${editingChannel?.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Platform Selection Tab
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Telegram", "Slack").forEach { typ ->
                            val isSel = selectedPlatform == typ
                            val color = when (typ) {
                                "Telegram" -> Color(0xFF229ED9)
                                "Slack" -> Color(0xFFE01E5A)
                                else -> Color.Gray
                            }
                            
                            OutlinedButton(
                                onClick = { selectedPlatform = typ },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSel) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                    contentColor = Color.White
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSel) 2.dp else 1.dp,
                                    color = if (isSel) Color.White else Color.White.copy(alpha = 0.3f)
                                )
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
                        label = { Text("Mecra/Oda Takma Adı", fontSize = 13.sp, color = Color.White) },
                        placeholder = { Text("E.g., Mühendislik Grubu, Genel Duyuru", color = Color.White.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Context Based Fields
                    if (selectedPlatform == "Telegram") {
                        OutlinedTextField(
                            value = txtTelegramToken,
                            onValueChange = { txtTelegramToken = it },
                            label = { Text("Telegram Bot Token", fontSize = 13.sp, color = Color.White) },
                            placeholder = { Text("612345678:AAFdff_...", color = Color.White.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = txtTelegramChatId,
                            onValueChange = { txtTelegramChatId = it },
                            label = { Text("Chat veya Kanal ID", fontSize = 13.sp, color = Color.White) },
                            placeholder = { Text("-10023456789 veya @kanal_adi", color = Color.White.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            onClick = { showTelegramHelp = !showTelegramHelp },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.15f),
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
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Sayısal ID'yi Bulmak Çok Kolay! 💡",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = if (showTelegramHelp) "▲" else "▼",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                AnimatedVisibility(visible = showTelegramHelp) {
                                    Column(modifier = Modifier.padding(top = 8.dp)) {
                                        Text(
                                            text = "Grup veya Kanalınızın ID'sini çözmek için aşağıdaki pratik yolları izleyebilirsiniz:",
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Column {
                                                Text(
                                                    text = "1. @Kullanıcı Adı Kullanın (En Kolayı)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Eğer grubunuz veya kanalınız 'Herkese Açık (Public)' ise, sayısal ID yazmanıza gerek yoktur! Direkt @kanal_adi (örneğin @duyurulariniz) yazıp kaydedebilirsiniz.",
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    lineHeight = 14.sp
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "2. Bot İle ID Öğrenin (Sadece 10 Saniye)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Herhangi bir grubun ID'sini öğrenmek için, grubunuza geçici olarak @RawDataBot veya @GetMyChatID_Bot ekleyin. Grupta sadece /id yazın; bot size grubu temsil eden sayıyı (örn: -10023456789) söyleyecektir. ID'yi buraya kopyaladıktan sonra botu gruptan silebilirsiniz.",
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    lineHeight = 14.sp
                                                )
                                            }

                                            Column {
                                                Text(
                                                    text = "3. Web Sürümü Linkinden Bulma",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "Tarayıcıda Telegram Web'den (web.telegram.org) gruba tıkladığınızda, üstteki adres çubuğunda (URL) yer alan sayılar (örn: '2234056711') o sohbetin ID'sidir. Başına -100 ekleyip (örn: -1002234056711) kullanabilirsiniz.",
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Slack webhook field
                        OutlinedTextField(
                            value = txtWebhookUrl,
                            onValueChange = { txtWebhookUrl = it },
                            label = { Text("Webhook URL Adresi", fontSize = 13.sp, color = Color.White) },
                            placeholder = { Text("https://...", color = Color.White.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.4f),
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                            )
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
                                    "Slack" -> viewModel.testSlackConnection(txtWebhookUrl)
                                }
                            },
                            enabled = !uiState.isTesting && (
                                if (selectedPlatform == "Telegram") txtTelegramToken.isNotBlank() && txtTelegramChatId.isNotBlank()
                                else txtWebhookUrl.startsWith("http")
                            ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = Color.White.copy(alpha = 0.3f),
                                disabledContentColor = Color.White.copy(alpha = 0.5f)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            if (uiState.isTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("Bağlantıyı Test Et", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = resetForm,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Vazgeç", tint = Color.White)
                        }
                    }

                    uiState.testResult?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = it,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (it.contains("Hata")) Color(0xFFFFCDD2) else Color(0xFFC8E6C9),
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
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = Color.White.copy(alpha = 0.3f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (editingChannel == null) "Sohbet Odasını Kaydet" else "Değişiklikleri Güncelle", fontWeight = FontWeight.Bold)
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
                    text = "Lütfen yayın duyurusu göndermek istediğiniz Telegram veya Slack sohbet odalarını eklemek için yukarıdaki form panelini doldurun.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val telegramSettingsChannels = channels.filter { it.platformType == "Telegram" }
            val slackSettingsChannels = channels.filter { it.platformType == "Slack" }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (telegramSettingsChannels.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isTelegramSettingsExpanded = !isTelegramSettingsExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF229ED9),
                                    modifier = Modifier.size(12.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Telegram Odaları (${telegramSettingsChannels.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isTelegramSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isTelegramSettingsExpanded) "Daralt" else "Genişlet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isTelegramSettingsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                telegramSettingsChannels.forEach { channel ->
                                    SavedChannelCard(channel, openEditForm, viewModel)
                                }
                            }
                        }
                    }
                }

                if (slackSettingsChannels.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSlackSettingsExpanded = !isSlackSettingsExpanded }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFFE01E5A),
                                    modifier = Modifier.size(12.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Slack Odaları (${slackSettingsChannels.size})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (isSlackSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isSlackSettingsExpanded) "Daralt" else "Genişlet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        AnimatedVisibility(
                            visible = isSlackSettingsExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                slackSettingsChannels.forEach { channel ->
                                    SavedChannelCard(channel, openEditForm, viewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedChannelCard(
    channel: ChannelEntity,
    openEditForm: (ChannelEntity) -> Unit,
    viewModel: AnnouncementViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = when (channel.platformType) {
                        "Telegram" -> Color(0xFF80DEEA)
                        "Slack" -> Color(0xFFF48FB1)
                        else -> Color.White.copy(alpha = 0.5f)
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
                        fontSize = 15.sp,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { openEditForm(channel) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", modifier = Modifier.size(16.dp), tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { viewModel.deleteChannel(channel) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Sil", modifier = Modifier.size(16.dp), tint = Color(0xFFEF9A9A))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Platform: ${channel.platformType}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )

            if (channel.platformType == "Telegram") {
                Text(text = "ID: ${channel.telegramChatId}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                Text(text = "Token: •••••", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            } else {
                Text(text = "Web Hook: ${channel.webhookUrl.take(45)}...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (channel.isEnabled) "Durum: Aktif" else "Durum: Devre Dışı",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (channel.isEnabled) Color(0xFF81C784) else Color.White.copy(alpha = 0.5f)
                )

                Switch(
                    checked = channel.isEnabled,
                    onCheckedChange = { viewModel.toggleChannelEnabled(channel) },
                    thumbContent = if (channel.isEnabled) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize), tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.5f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
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

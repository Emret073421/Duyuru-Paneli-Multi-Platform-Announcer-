package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.api.BroadcastService
import com.example.data.database.AppDatabase
import com.example.data.repository.AnnouncementRepository
import com.example.ui.components.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AnnouncementViewModel
import com.example.ui.viewmodel.AnnouncementViewModelFactory

/**
 * GÖREV VE AMAÇ:
 * Bu sınıf uygulamamızın ana giriş noktasıdır (Entry Point / Launcher Activity).
 *
 * NASIL ÇALIŞIR?
 * 1. Room Veritabanını (SQLite yerel veritabanı), OkHttp Ağ Servisini (BroadcastService) ve Repository katmanını kurar.
 * 2. ViewModelFactory aracılığıyla bağımlılıkları ViewModel katmanına enjekte eder.
 * 3. Arayüz için klasik XML düzen dosyaları (layout/activity_main.xml) YERİNE modern Jetpack Compose kullanarak
 *    MainAppScreen'i tam ekran kaplayacak şekilde render eder.
 *
 * VERİ NEREDEN GELİYOR VE APİ ETKİLEŞİMİ:
 * - Oda Tanımları ve Mesaj Geçmişi: Yerel SQLite veritabanından (Room kütüphanesi).
 * - Dış Servisler (Telegram, Discord, Slack): Doğrudan REST API ve Webhook üzerinden BroadcastService sınıfı aracılığıyla beslenir.
 */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Kenardan kenara (edge-to-edge) modern ekran yerleşimi desteğini aktif eder (Safe bar renk uyumu için)
    enableEdgeToEdge()

    // 1. Veritabanını İlklendir (SQLite veritabanı Room sarmalayıcısı aracılığıyla ayağa kalkar)
    val database = AppDatabase.getDatabase(applicationContext)
    
    // 2. İş mantığı, Veritabanı işlemleri ve Ağ isteklerini koordine eden Depo (Repository) nesnesini oluştur
    val repository = AnnouncementRepository(
      channelDao = database.channelDao(),
      announcementDao = database.announcementDao(),
      broadcastService = BroadcastService()
    )
    
    // 3. ViewModel'in bağımlılıklarını alabilmesi için özel üretici fabrikasını (Factory) üret
    val viewModelFactory = AnnouncementViewModelFactory(repository)

    setContent {
      MyApplicationTheme {
        // Kotlin ve Jetpack Compose ile yazılmış olan ana ekran arayüzünü oluşturur ve viewModel'i bağlar.
        val viewModel: AnnouncementViewModel = viewModel(factory = viewModelFactory)
        
        MainAppScreen(
          viewModel = viewModel,
          modifier = Modifier.fillMaxSize()
        )
      }
    }
  }
}

# 📢 Çoklu Kanal Duyuru ve Yayın Sistemi (Multi-Channel Broadcast App)

Bu proje; Android platformu üzerinde modern **Jetpack Compose** ve **Android Architecture Components** kullanılarak geliştirilmiş, tek bir merkezden **Telegram**, **Discord** ve **Slack** kanallarına aynı anda anlık görsel/metinsel duyurular (broadcast) göndermeyi sağlayan profesyonel bir mobil uygulamadır.

---

## 🚀 Temel Özellikler & Görevler

1. **Çoklu Platform Desteği (Multi-Platform Broadcast):**
   * **Telegram:** Bot Token ve Chat ID aracılığıyla ister genel (`@kanal_adi`) ister gizli sayısal sohbet odalarına gönderim. Hem metin hem de görsel (URL veya yerel dosya) desteği.
   * **Discord:** Webhook URL segmenti kullanılarak anlık görsel (embeds veya yerel dosya eki) ve metinsel yayın kontrolü.
   * **Slack:** Webhook API entegrasyonu ile zengin içerikli **Block Kit** mesaj gönderimi.

2. **Dinamik Görsel/Medya Gönderimi (Rich Media Attachments):**
   * Kullanıcılar duyurularına cihaz galerisinden **yerel görsel** seçebilir ya da **web adresi (URL)** iliştirebilir.
   * **Slack Webhook Sınırlaması Uyarı Mekanizması:** Slack Webhook standartlarının doğrudan binary görsel yüklemeyi (multipart form-data) desteklememesi nedeniyle sadece Slack seçildiğinde kullanıcıyı bilgilendiren dinamik durum duyarlı arayüz uyarısı eklenmiştir.

3. **Gelişmiş Ağ ve Dayanıklılık Desteği (Network Retry Logic):**
   * VPN geçişleri ve kararsız mobil veri bağlantılarında oluşabilecek anlık kopmalara karşı **3 kez katlanarak artan bekleme süreli (Exponential Backoff Retry)** istek mekanizması mevcuttur.

4. **Sayısal Telegram ID Bulma Rehberi:**
   * Kullanıcıların `-10023456789` gibi gizli kanal/grup ID'lerini bulabilmesi için pratik rehber ve sık sorulan sorular (FAQ) yardım kartı yerleşik olarak sunulmaktadır.

5. **Güvenli Yerel Veri Tabanı (Offline-First Channel Storage & Logs):**
   * Kanallar ve gönderilen eski duyuruların raporları SQLite üzerinde **Room Database** ile güvenli bir şekilde cihazda saklanır.

---

## 🛠️ Mimari ve Proje Yapısı (Architecture)

Uygulama, modern Android standartlarına uygun olarak **Clean Architecture** prensiplerine ve **MVVM (Model-View-ViewModel)** desenine göre yapılandırılmıştır.

```
/app/src/main/java/com/example
├── MainActivity.kt                      # Giriş kapısı (Entry Point) ve Dependency Injection kurulumu
├── data
│   ├── api
│   │   └── BroadcastService.kt          # OkHttp istemcisi, API istekleri, JSON/Multipart payload oluşturucu
│   ├── database
│   │   ├── AppDatabase.kt               # Room veritabanı tanımı ve SQLite koordinasyonu
│   │   ├── ChannelEntity.kt             # Kanal/Sohbet tanımlarını tutan tablo yapısı
│   │   ├── ChannelDao.kt                # Kanallar üzerindeki veritabanı sorguları (CRUD)
│   │   ├── AnnouncementEntity.kt        # Gönderilen duyuruların metinleri ve durum özet rapor tablosu
│   │   └── AnnouncementDao.kt           # Duyuru geçmişi veritabanı sorguları
│   └── repository
│       └── AnnouncementRepository.kt    # ViewModel ile veri/API kaynaklarını birleştiren ara katman (Single Source of Truth)
└── ui
    ├── components
    │   └── MainAppScreen.kt             # Jetpack Compose tabanlı tüm arayüz (Duyuru Gönder, Kanal Ekle, Geçmiş)
    ├── theme
    │   └── Theme.kt                     # Materyal 3 (M3) dinamik renk temaları, tipografi ve stiller
    └── viewmodel
        └── AnnouncementViewModel.kt     # Ekran durumlarını (UiState) yöneten ve UI nesnelerini besleyen kalıcı ViewModel
```

---

## 📥 Veri Akış Şeması (Data Flow)

1. **Arayüz (MainAppScreen):** Kullanıcı duyuru metnini yazar, dilerse galeri butonuna basarak yerel görsel seçer. `AnnouncementViewModel` üzerindeki durum güncellenir.
2. **ViewModel (AnnouncementViewModel):** Mesaj bilgisini ve görsel binary/link verilerini alır, repository sınıfına istek gönderir. Gönderim esnasında UI üzerinde yükleme simgeleri (`isBroadcasting`) gösterilir.
3. **Repository (AnnouncementRepository):** Seçilen tüm aktif entegrasyonları tespit eder. `BroadcastService` üzerinden paralel olarak HTTP POST istekleri fırlatır. Sonuçları toplayıp bir rapor özetine dönüştürür.
4. **Veritabanı Katmanı (Room & SQLite):** Gönderim başarısı veya başarısızlığı ile birlikte rapor satırını cihaz veritabanına kaydeder. Geçmiş sekmesi anında güncellenir.

---

## 🚀 Çalıştırma ve Doğrulama (Compilation & Test)

Uygulama modern Gradle build yapısını kullanmaktadır.

### Projeyi Derlemek İçin:
```bash
gradle assembleDebug
```

### Birim ve Robolectric Testlerini Koşmak İçin:
```bash
gradle :app:testDebugUnitTest
```

---

## 🎨 Tasarım Standartları (Material Design 3)

* **Edge-to-Edge:** Cihazın durum çubuğu ve navigasyon çekmecesi alanlarıyla bütünleşen modern tasarıma sahiptir.
* **Modern Material 3 Card ve Surface Yapıları:** Kullanıcı girdilerinde ve uyarılarında tamamen M3 standartları uygulanmıştır.
* **Responsive Layout:** Hem kompakt telefonlarda hem de tablet ekranlarında sığmayan taşma sorunlarını önleyecek şekilde dinamik boşluklar kullanılmıştır.

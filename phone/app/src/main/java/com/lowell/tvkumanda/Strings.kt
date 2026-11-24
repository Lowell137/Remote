package com.lowell.tvkumanda

enum class AppLanguage(val label: String) {
    Auto("Otomatik / Auto"),
    Tr("Türkçe"),
    En("English"),
}

enum class AppTheme(val labelTr: String, val labelEn: String) {
    System("Sistem", "System"),
    Light("Açık", "Light"),
    Dark("Koyu", "Dark");

    fun label(isTr: Boolean): String = if (isTr) labelTr else labelEn
}

class Strings(val isTr: Boolean) {
    fun t(tr: String, en: String): String = if (isTr) tr else en

    // Tabs
    val tabPad get() = t("Kumanda", "Remote")
    val tabApps get() = t("Uygulamalar", "Apps")
    val tabTv get() = t("TV", "TV")
    val tabKeyboard get() = t("Klavye", "Keyboard")

    // Connection
    val connected get() = t("Bağlı", "Connected")
    val notConnected get() = t("Bağlı değil", "Not connected")
    val searchingTv get() = t("TV aranıyor…", "Searching for TV…")
    fun tvFound(ip: String) = t("TV bulundu: $ip", "TV found: $ip")
    val tvNotFound get() = t("TV bulunamadı, adresi ayarlardan gir", "TV not found, enter IP in settings")
    val addrSaved get() = t("Adres kaydedildi", "Address saved")

    // Mode
    val modeButtons get() = t("Tuş", "Buttons")
    val modeSwipe get() = t("Kaydır", "Swipe")
    val modeMouse get() = t("Fare", "Mouse")
    val modeSwipeHint get() = t(
        "Kaydır: yön tuşları · Dokun: OK\nİki parmak dokun: Geri · Basılı tut: Menü",
        "Swipe: arrow keys · Tap: OK\nTwo-finger tap: Back · Long press: Menu"
    )
    val modeMouseHint get() = t(
        "Kaydır: imleç · Dokun: sol tık\nİki parmak dokun: sağ tık · İki parmak kaydır: sayfa\nÇift dokun ve sürükle: taşı",
        "Swipe: cursor · Tap: left click\nTwo-finger tap: right click · Two-finger swipe: scroll\nDouble tap & drag: move"
    )

    // Mouse buttons
    val mouseLeft get() = t("Sol", "Left")
    val mouseMiddle get() = t("Orta", "Middle")
    val mouseRight get() = t("Sağ", "Right")

    // Gestures help
    val gesturesTitle get() = t("Hareketler", "Gestures")
    val swipeModeTitle get() = t("Kaydır modu", "Swipe mode")
    val twoFingersTitle get() = t("İki parmak", "Two fingers")
    val threeFingersTitle get() = t("Üç parmak (her modda)", "Three fingers (all modes)")
    val mouseModeTitle get() = t("Fare modu", "Mouse mode")
    val volumeTitle get() = t("Ses", "Volume")
    val phoneVolKeyTitle get() = t("Telefonun ses tuşları", "Phone volume buttons")
    val tvVolDesc get() = t("TV sesi", "TV volume")
    val volMiniDesc get() = t("Sol alttaki − / sessiz / +", "Bottom-left − / mute / +")

    // Power
    val powerTitle get() = t("Güç", "Power")
    val sleepAct get() = t("Uyku (ekranı kapat)", "Sleep (turn off display)")
    val wakeAct get() = t("Uyandır", "Wake")
    val rebootAct get() = t("Yeniden başlat", "Restart")
    val powerOffAct get() = t("Kapat", "Power off")
    val sleepSent get() = t("Uyku komutu gönderildi", "Sleep command sent")
    val wakingUp get() = t("Uyandırılıyor", "Waking up")
    val rebooting get() = t("Yeniden başlatılıyor", "Restarting")
    val shuttingDown get() = t("Kapatılıyor", "Shutting down")
    val restartConfirmTitle get() = t("Yeniden başlatılsın mı?", "Restart TV?")
    val restartConfirmMsg get() = t("TV yaklaşık 1,5 dakika kapalı kalır, kumanda da bu sürede çalışmaz.", "TV will stay off for ~1.5 min, remote will not work during this time.")
    val powerOffConfirmTitle get() = t("Kapatılsın mı?", "Power off TV?")
    val powerOffConfirmMsg get() = t("Kapatınca bu kumandayla geri açamazsın, fiziksel düğme gerekir.", "Once powered off, you cannot turn it back on with this remote; physical button required.")
    val cancel get() = t("İptal", "Cancel")

    // Apps
    val searchApps get() = t("Uygulama ara…", "Search apps…")
    val manage get() = t("Yönet", "Manage")
    val done get() = t("Bitti", "Done")
    val open get() = t("Aç", "Open")
    val stop get() = t("Durdur", "Force Stop")
    val appInfo get() = t("Bilgi", "App Info")
    val clearData get() = t("Verileri temizle", "Clear Data")
    val uninstall get() = t("Kaldır", "Uninstall")
    val noApps get() = t("Yüklü uygulama yok", "No apps found")
    val stopConfirmTitle get() = t("Durdurulsun mu?", "Force stop?")
    val stopConfirmMsg get() = t("Uygulama arka planda sonlandırılır.", "The application will be terminated.")
    val clearConfirmTitle get() = t("Veriler temizlensin mi?", "Clear app data?")
    val clearConfirmMsg get() = t("Tüm ayarları, hesapları ve verileri silinir.", "All settings, accounts, and data will be erased.")
    fun uninstallConfirmTitle(name: String) = t("$name kaldırılsın mı?", "Uninstall $name?")
    val uninstallConfirmMsg get() = t("Uygulama TV'den tamamen silinir.", "The app will be completely removed from TV.")

    // TV tab
    val homeLauncherTitle get() = t("Ana ekran (launcher)", "Home Screen (Launcher)")
    val quickSettingsTitle get() = t("Hızlı ayarlar", "Quick Settings")
    val launcherFailed get() = t("Liste alınamadı", "Could not load list")
    fun alreadyDefault(name: String) = t("$name zaten varsayılan", "$name is already default")
    fun defaultPrompt(name: String) = t("$name varsayılan olsun mu?", "Set $name as default?")
    val defaultDesc get() = t("Home tuşu ve açılış bu ana ekrana gider. İstediğin zaman buradan geri değiştirebilirsin.", "Home key and startup will use this launcher. You can change it back anytime.")
    val makeDefault get() = t("Varsayılan yap", "Set as default")
    fun defaultSuccess(name: String) = t("$name varsayılan yapıldı", "$name set as default")
    val defaultError get() = t("Değiştirilemedi", "Failed to change")
    fun settingsOpening(name: String) = t("$name ayarları TV'de açılıyor", "Opening $name settings on TV")

    // Settings
    val settingsTitle get() = t("Ayarlar", "Settings")
    val tvSection get() = t("TV", "TV")
    val save get() = t("Kaydet", "Save")
    val scanTv get() = t("TV'yi ara", "Scan TV")
    val scanning get() = t("Aranıyor…", "Scanning…")
    val notFound get() = t("TV bulunamadı", "TV not found")
    val connection get() = t("Bağlantı", "Connection")
    val tvIpPlaceholder get() = t("TV IP adresi (örn. 192.168.1.50)", "TV IP address (e.g. 192.168.1.50)")
    val touchpadSection get() = t("Touchpad", "Touchpad")
    val speed get() = t("Hız", "Speed")
    val stepSwipe get() = t("Gezinti: tuş başına kaydırma", "Navigation: swipe step")
    val preferencesSection get() = t("Tercihler", "Preferences")
    val naturalScroll get() = t("Doğal kaydırma", "Natural scrolling")
    val hapticFeedback get() = t("Titreşim", "Haptic feedback")
    val leftHandMode get() = t("Sol el modu", "Left-hand mode")
    val themeTitle get() = t("Tema", "Theme")
    val languageTitle get() = t("Dil / Language", "Language / Dil")

    // Keyboard
    val keyboardTitle get() = t("TV Klavye", "TV Keyboard")
    val keyboardHint get() = t("TV'deki odaklı alana yazılır. Türkçe ve özel harfler otomatik yapıştırılır.", "Types into focused TV field. Special characters are pasted automatically.")
    val keyboardInputPlaceholder get() = t("Yazmaya başla (harf harf TV'ye gider)…", "Start typing (synced to TV live)…")
    val clear get() = t("Temizle", "Clear")
    val pasteClip get() = t("Panoyu Yapıştır", "Paste Clipboard")
    val enterKey get() = t("Enter ↵", "Enter ↵")
    val clipPasted get() = t("Pano TV'ye yapıştırıldı", "Clipboard pasted to TV")
    val clipEmpty get() = t("Pano boş", "Clipboard is empty")
    val liveTypingDesc get() = t("Canlı yazma: yazdıkça TV'ye aktarılır", "Live typing: synced to TV as you type")
    val close get() = t("Kapat", "Close")
    fun targetHint(hint: String) = t("Hedef: $hint", "Target: $hint")
    fun appPkg(pkg: String) = t("Uygulama: $pkg", "App: $pkg")

    val tvNotConnected get() = t("TV'ye bağlı değil", "Not connected to TV")
    val sleepRow get() = t("Uyku", "Sleep")
    val homeAction get() = t("Ana ekran", "Home")
    val menuAction get() = t("Menü", "Menu")
    val backAction get() = t("Geri", "Back")
    val prevAction get() = t("Önceki", "Previous")
    val nextAction get() = t("Sonraki", "Next")
    val playPauseAction get() = t("Oynat / duraklat", "Play / pause")
    val searchAction get() = t("Ara", "Search")
    val manageHintManage get() = t("Yönet: bir uygulamaya dokun", "Manage: tap an app")
    val manageHintOpen get() = t("Aç: dokununca uygulama açılır", "Open: tap to launch")
    fun openingApp(name: String) = t("$name açılıyor", "Opening $name")
    val appInfoAct get() = t("Uygulama bilgisi (TV)", "App info (TV)")
    fun operationDone(name: String) = t("$name: tamam", "$name: done")
    val operationFailed get() = t("İşlem başarısız", "Operation failed")
    val uninstallActualDesc get() = t("Uygulama ve verileri silinir. Geri alınamaz.", "The app and its data will be deleted. This cannot be undone.")
    val clearActualDesc get() = t("Giriş bilgileri ve ayarlar sıfırlanır.", "Sign-in details and settings will be reset.")
    val uninstalled get() = t("Kaldırıldı", "Uninstalled")
    val cleared get() = t("Temizlendi", "Cleared")
}

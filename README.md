# Remote

Telefondan Android TV'yi kontrol eden iki parçalı proje: TV üzerinde çalışan sunucu ve telefon uygulaması. PC veya Termux gerekmez, sabit IP gerekmez.

![Remote — TV sunucusu + telefon kumanda](remote_screenshots.png)

## Parçalar

- [`tv/`](tv/README.md) — Android TV sunucusu (`com.lowell.tvremote`). HTTP/WS 8080, sanal HID fare/klavye/medya, kablosuz ADB ile otomatik kurulum.
- [`phone/`](phone/README.md) — Telefon kumandası (`com.lowell.tvkumanda`). Compose arayüz, TR/EN, açık/koyu/sistem tema, touchpad + hareketler, canlı klavye.

## Hızlı başlangıç

1. Android TV'de **Kablosuz hata ayıklama** açık olsun (ADB izni bir kez onaylanır).
2. Telefon ve TV aynı Wi-Fi ağında olsun.
3. Tek komutla ikisini de derleyip kur:

```
powershell -ExecutionPolicy Bypass -File .\phone\deploy.ps1
```

Başka TV IP'si için:

```
powershell -ExecutionPolicy Bypass -File .\phone\deploy.ps1 -TvIp 192.168.X.Y:5555
```

İkonu değiştirmek için (TV + telefon birlikte):

```
powershell -ExecutionPolicy Bypass -File .\phone\set-icon.ps1 -Icon custom
```

Seçenekler: `dark`, `solar`, `mono`, `custom` (şu an aktif: yuvarlak custom).

## Nasıl bulur?

TV sunucusu 8080'i tüm arayüzlere açar ve kendi LAN adresini ekranda gösterir. Telefon ilk açılışta aynı `/24` alt ağını `/status` ile tarar; bulamazsa Ayarlar → TV → TV'yi ara ile elle aranır. Kodda sabit IP yoktur.

## Lisans

MIT — bkz. [LICENSE](LICENSE).

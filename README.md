# Cincik Not

Android için el yazısı + ses ile not alma uygulaması ve ana ekran widget'ı.

- **El yazısı → metin:** Alttaki alana parmakla yaz; elini kaldırınca (≈1 sn) yazı metne dönüşür
  (Google ML Kit Digital Ink, Türkçe model; ilk açılışta bir kez indirilir, sonra internetsiz çalışır).
- **Hesap:** `12 + 7 =` gibi `=` ile biten bir işlem yazınca sonuç otomatik eklenir.
  `= Hesapla` düğmesi imlecin bulunduğu satırı hesaplar. Desteklenenler: `+ - x × * ÷ : / ^ √ ( )`, ondalık için `,` veya `.`.
- **Ses → metin:** `🎤 Konuş` telefonun Google ses tanımasını (tr-TR) kullanır.
- **Kopyala:** Notun tamamını panoya kopyalar. `⌫` uzun basınca notu siler.
- **Widget:** Ana ekrana "Cincik Not" widget'ını ekle; son notu gösterir, `Yaz` / `Konuş` ile uygulamayı açar.

## İndirme

Her push'ta GitHub Actions APK'yı derleyip bir Release olarak yayınlar:
https://github.com/ilkayrodo-commits/cincikgames/releases/latest

`app/debug.keystore.b64` (derlemede `debug.keystore` olarak açılır) yalnızca test imzası içindir (her derleme aynı imzayla çıksın, güncelleme kaldırmadan kurulabilsin diye).

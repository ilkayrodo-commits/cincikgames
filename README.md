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

---

# Cincik Duvar (`wallpapers/`)

10 hareketli Android canlı duvar kağıdı. Galeride dokun → tam ekran önizle → **Uygula**.

| | Sahne | Hareket |
|---|---|---|
| 1 | Galata Saçları | Kullanıcının duvar kağıdı: saçlar tel yönünde akar ve savrulur, kuleyi saran tutamlar dönerek önünden/arkasından geçer, pencerelerde mum ışığı, gökyüzünde savrulan teller ve martılar. Telefonu eğince derinlik (paralaks), dokununca saçlar itilir. |
| 2 | İstanbul Gecesi | Hilal, aydınlatılmış camiler, boğazda yansımalar, geçen vapur |
| 3 | Kuzey Işıkları | Karlı dağlar, göl yansıması, aurora perdeleri |
| 4 | Retro Gün Batımı | Neon ızgara, çizgili güneş |
| 5 | Derin Uzay | Akan bulutsu, kayan yıldızlar |
| 6 | Yağmurlu Cam | Kayan damlalar, bulanık şehir ışıkları; dokununca cam silinir |
| 7 | Okyanus Altı | Işık huzmeleri, kostikler, kabarcıklar, yosunlar |
| 8 | Sakura | Süzülen kiraz yaprakları |
| 9 | Altın Mermer | Akan altın damarlar; dokununca girdap |
| 10 | Lav Lambası | Birleşip ayrılan damlalar |

- Sahneler `app/src/main/assets/shaders/*.frag` (GLSL ES 1.0, ortak başlık `common.glsl`).
- Galata görüntüsü `tools/clean_galata.py` ile ekran görüntüsündeki saat/tarih/simgelerden temizlendi,
  haritalar `tools/galata_maps.py` ile üretildi: yönlü tel akışı (işaret tutarlılığı için Prim yayılımı),
  saç/gökyüzü/kule ayrımı, kuleyi saran bantlar ve pencereler.
- `tools/render_shader.js`: sahneleri başsız Chromium'da WebGL ile çizer (önizleme/küçük resim).
- Pil için ~30 fps, görünmezken durur; ağır sahneler düşük çözünürlükte çizilip büyütülür.

package com.cincikgames.dost

/** Gardırop kataloğu: 6 kategori x 10 parça + hazır kombinler. */

enum class Category(val title: String, val emoji: String) {
    HAT("Şapka", "🎩"), GLASSES("Gözlük", "🕶"), JACKET("Ceket", "🧥"),
    SHIRT("Tişört", "👕"), PANTS("Pantolon", "👖"), SHOES("Ayakkabı", "👟"),
}

enum class Pattern { SOLID, STRIPES_H, STRIPES_V, DOTS, CHECK, CAMO, FLORAL, KNIT, SPARKLE, PUFFER, SIDE_STRIPE, SEAMS }

enum class Glyph(val rows: Array<String>) {
    HEART(arrayOf("X.X", "XXX", ".X.")),
    BOLT(arrayOf("..X", ".X.", "X..")),
    LETTER_C(arrayOf("XXX", "X..", "XXX")),
    STAR(arrayOf(".X.", "XXX", ".X.")),
    NUMBER(arrayOf("XXX", ".X.", ".X.")),
}

enum class ShoeStyle { SNEAKER, BOOT, SLIPPER, RAIN, PATENT, CHECKER, SOCK, FLIPPER, HEEL, LIGHT }

enum class LensStyle { SUN, NERD, HEART, STAR, SKI, MONOCLE, THREE_D, PILOT, DEAL, RETRO }

sealed class Look {
    /** Kumaş: tişört/ceket/pantolon. */
    data class Fabric(
        val base: Int,
        val second: Int = base,
        val third: Int = second,
        val pattern: Pattern = Pattern.SOLID,
        val glyph: Glyph? = null,
        val glyphColor: Int = second,
        val longSleeve: Boolean = false,
        val open: Boolean = false,          // ceket: önü açık
        val sleeve: Int? = null,            // ceket: farklı renk kol (varsity)
        val shorts: Boolean = false,        // pantolon: şort/mayo
    ) : Look()

    data class Shoe(val style: ShoeStyle, val base: Int, val second: Int = base) : Look()

    /** Şapka çizimi: [baseRow]. satır kafanın en üst satırına oturur; x=0 karakterin 0. sütunu. */
    data class Hat(val art: Array<String>, val colors: Map<Char, Int>, val baseRow: Int, val x0: Int = 0) : Look()

    data class Glasses(val style: LensStyle, val frame: Int, val lens: Int, val lens2: Int = lens) : Look()
}

data class Item(val id: String, val category: Category, val name: String, val look: Look)

data class Outfit(
    val hat: String? = null,
    val glasses: String? = null,
    val jacket: String? = null,
    val shirt: String? = null,
    val pants: String? = null,
    val shoes: String? = null,
) {
    fun get(c: Category): String? = when (c) {
        Category.HAT -> hat; Category.GLASSES -> glasses; Category.JACKET -> jacket
        Category.SHIRT -> shirt; Category.PANTS -> pants; Category.SHOES -> shoes
    }

    fun with(c: Category, id: String?): Outfit = when (c) {
        Category.HAT -> copy(hat = id); Category.GLASSES -> copy(glasses = id); Category.JACKET -> copy(jacket = id)
        Category.SHIRT -> copy(shirt = id); Category.PANTS -> copy(pants = id); Category.SHOES -> copy(shoes = id)
    }

    fun encode(): String = listOf(hat, glasses, jacket, shirt, pants, shoes).joinToString("|") { it ?: "" }

    companion object {
        fun decode(s: String?): Outfit {
            val p = (s ?: "").split("|").map { it.ifEmpty { null } } + List(6) { null }
            return Outfit(p[0], p[1], p[2], p[3], p[4], p[5])
        }
    }
}

data class Preset(val name: String, val emoji: String, val outfit: Outfit)

object Catalog {
    private fun c(hex: String) = SpriteSheet.argb(hex)

    // sık kullanılan renkler
    private val BLACK = c("#22232A")
    private val WHITE = c("#F4F4F0")
    private val RED = c("#E5484D")
    private val NAVY = c("#24365C")
    private val YELLOW = c("#FFD23F")
    private val GOLD = c("#E8B931")

    val items: List<Item> = listOf(
        // ---------------------------------------------------------------- şapkalar
        Item("hat_cap", Category.HAT, "Kasket", Look.Hat(arrayOf(
            "....GGGGGGGG....",
            "...GgGgGgGgGGGGG",
        ), mapOf('G' to c("#7D7F86"), 'g' to c("#6A6C73")), baseRow = 1)),
        Item("hat_pompom", Category.HAT, "Ponponlu bere", Look.Hat(arrayOf(
            ".......WW.......",
            "......WWWW......",
            "....RRRRRRRR....",
            "...RWRWRWRWRW...",
            "..WWWWWWWWWWWW..",
        ), mapOf('W' to WHITE, 'R' to RED), baseRow = 4)),
        Item("hat_straw", Category.HAT, "Hasır şapka", Look.Hat(arrayOf(
            ".....YyYyYY.....",
            ".....yYyYyY.....",
            ".....BBBBBB.....",
            "YYyYYyYYyYYyYYyY",
        ), mapOf('Y' to c("#E6C36A"), 'y' to c("#D2AC52"), 'B' to c("#C0392B")), baseRow = 3)),
        Item("hat_party", Category.HAT, "Parti külahı", Look.Hat(arrayOf(
            ".......Y........",
            ".......P........",
            "......PCP.......",
            "......CPC.......",
            ".....PCPCP......",
            ".....CPCPC......",
        ), mapOf('Y' to YELLOW, 'P' to c("#F06A9B"), 'C' to c("#4CC9F0")), baseRow = 5)),
        Item("hat_cowboy", Category.HAT, "Kovboy", Look.Hat(arrayOf(
            ".....BBBBBB.....",
            "....BBBBBBBB....",
            "....bbbbbbbb....",
            "BB.BBBBBBBBBB.BB",
            ".BBB........BBB.",
        ), mapOf('B' to c("#8B5A2B"), 'b' to c("#3E2A17")), baseRow = 3)),
        Item("hat_pirate", Category.HAT, "Korsan", Look.Hat(arrayOf(
            "....KKKKKKKK....",
            "...KKKKWWKKKK...",
            "..KKKKKWWKKKKK..",
            ".KKKKKKKKKKKKKK.",
        ), mapOf('K' to BLACK, 'W' to WHITE), baseRow = 3)),
        Item("hat_crown", Category.HAT, "Taç", Look.Hat(arrayOf(
            "....Y...Y...Y...",
            "....YY.YYY.YY...",
            "....YYYYYYYYY...",
            "....YRYYBYYRY...",
        ), mapOf('Y' to GOLD, 'R' to RED, 'B' to c("#4C8BF5")), baseRow = 3)),
        Item("hat_headphones", Category.HAT, "Kulaklık", Look.Hat(arrayOf(
            "...KKKKKKKKKK...",
            "..K..........K..",
            "..K..........K..",
            ".KKK........KKK.",
            ".KRK........KRK.",
            ".KKK........KKK.",
        ), mapOf('K' to BLACK, 'R' to RED), baseRow = 1)),
        Item("hat_baseball", Category.HAT, "Ters kep", Look.Hat(arrayOf(
            ".....RRRRRR.....",
            "....RRRWRRRR....",
            "RRRRRRRRRRRR....",
        ), mapOf('R' to c("#2F6FB3"), 'W' to WHITE), baseRow = 2)),
        Item("hat_beanie", Category.HAT, "Kışlık bere", Look.Hat(arrayOf(
            ".....NNNNNN.....",
            "....NnNnNnNN....",
            "...NNNNNNNNNN...",
            "..nnnnnnnnnnnn..",
        ), mapOf('N' to c("#3A4A7A"), 'n' to c("#2A3760")), baseRow = 3)),

        // ---------------------------------------------------------------- gözlükler
        Item("gl_sun", Category.GLASSES, "Güneş gözlüğü", Look.Glasses(LensStyle.SUN, BLACK, c("#15161B"))),
        Item("gl_nerd", Category.GLASSES, "Kalın çerçeve", Look.Glasses(LensStyle.NERD, BLACK, 0)),
        Item("gl_heart", Category.GLASSES, "Kalp gözlük", Look.Glasses(LensStyle.HEART, c("#C2185B"), c("#FF5A8A"))),
        Item("gl_star", Category.GLASSES, "Yıldız gözlük", Look.Glasses(LensStyle.STAR, c("#B8860B"), YELLOW)),
        Item("gl_ski", Category.GLASSES, "Kayak gözlüğü", Look.Glasses(LensStyle.SKI, c("#2B2D35"), c("#FF8C42"), c("#FFC15E"))),
        Item("gl_monocle", Category.GLASSES, "Monokl", Look.Glasses(LensStyle.MONOCLE, GOLD, 0)),
        Item("gl_3d", Category.GLASSES, "3D gözlük", Look.Glasses(LensStyle.THREE_D, WHITE, RED, c("#28C2E8"))),
        Item("gl_pilot", Category.GLASSES, "Pilot", Look.Glasses(LensStyle.PILOT, GOLD, c("#2E4D3A"))),
        Item("gl_deal", Category.GLASSES, "Piksel havalı", Look.Glasses(LensStyle.DEAL, BLACK, BLACK)),
        Item("gl_retro", Category.GLASSES, "Retro pembe", Look.Glasses(LensStyle.RETRO, c("#8A2D5C"), c("#F49AC1"))),

        // ---------------------------------------------------------------- ceketler
        Item("jk_leather", Category.JACKET, "Deri ceket", Look.Fabric(c("#2B2B30"), c("#3C3C44"), pattern = Pattern.SEAMS, longSleeve = true, open = true)),
        Item("jk_hoodie", Category.JACKET, "Kapüşonlu", Look.Fabric(c("#6C5CE7"), c("#5A4BD1"), pattern = Pattern.SEAMS, longSleeve = true)),
        Item("jk_puffer", Category.JACKET, "Şişme mont", Look.Fabric(c("#E8603C"), c("#C94F2F"), pattern = Pattern.PUFFER, longSleeve = true)),
        Item("jk_denim", Category.JACKET, "Kot ceket", Look.Fabric(c("#4A78B5"), c("#6E97CC"), pattern = Pattern.SEAMS, longSleeve = true, open = true)),
        Item("jk_tux", Category.JACKET, "Smokin", Look.Fabric(BLACK, c("#33343C"), longSleeve = true, open = true)),
        Item("jk_rain", Category.JACKET, "Yağmurluk", Look.Fabric(c("#FFCB2B"), c("#E6B220"), pattern = Pattern.SEAMS, longSleeve = true)),
        Item("jk_cardigan", Category.JACKET, "Hırka", Look.Fabric(c("#C9A27E"), c("#B58D68"), pattern = Pattern.KNIT, longSleeve = true, open = true)),
        Item("jk_varsity", Category.JACKET, "Kolej ceketi", Look.Fabric(c("#8E1B2E"), WHITE, pattern = Pattern.SOLID, longSleeve = true, sleeve = WHITE)),
        Item("jk_fur", Category.JACKET, "Peluş kürk", Look.Fabric(c("#EADBC8"), c("#D8C4AC"), c("#F7EEE2"), pattern = Pattern.CAMO, longSleeve = true, open = true)),
        Item("jk_party", Category.JACKET, "Simli ceket", Look.Fabric(c("#B03ADB"), c("#E59CFF"), pattern = Pattern.SPARKLE, longSleeve = true, open = true)),

        // ---------------------------------------------------------------- tişörtler
        Item("sh_white", Category.SHIRT, "Beyaz tişört", Look.Fabric(WHITE, c("#E2E2DC"))),
        Item("sh_sailor", Category.SHIRT, "Denizci", Look.Fabric(WHITE, NAVY, pattern = Pattern.STRIPES_H)),
        Item("sh_heart", Category.SHIRT, "Kalpli", Look.Fabric(c("#FFB3C7"), glyph = Glyph.HEART, glyphColor = RED)),
        Item("sh_bolt", Category.SHIRT, "Şimşek", Look.Fabric(c("#2D2F3A"), glyph = Glyph.BOLT, glyphColor = YELLOW)),
        Item("sh_hawaii", Category.SHIRT, "Hawaii", Look.Fabric(c("#2EC4B6"), c("#FFE66D"), c("#FF6B6B"), pattern = Pattern.FLORAL)),
        Item("sh_sweater", Category.SHIRT, "Örgü kazak", Look.Fabric(c("#3F7D5A"), c("#E9E3D5"), pattern = Pattern.KNIT, longSleeve = true)),
        Item("sh_tank", Category.SHIRT, "Atlet", Look.Fabric(c("#F2F2F2"), c("#DCDCDC"), pattern = Pattern.STRIPES_V)),
        Item("sh_party", Category.SHIRT, "Simli parti", Look.Fabric(c("#F5C542"), c("#FFF3B0"), pattern = Pattern.SPARKLE)),
        Item("sh_logo", Category.SHIRT, "Cıncık tişört", Look.Fabric(c("#D97757"), glyph = Glyph.LETTER_C, glyphColor = WHITE)),
        Item("sh_jersey", Category.SHIRT, "Forma", Look.Fabric(YELLOW, RED, pattern = Pattern.STRIPES_V, glyph = Glyph.NUMBER, glyphColor = WHITE)),

        // ---------------------------------------------------------------- pantolonlar
        Item("pt_jeans", Category.PANTS, "Kot", Look.Fabric(c("#3B5F9A"), c("#5C82BE"), pattern = Pattern.SEAMS)),
        Item("pt_shorts", Category.PANTS, "Şort", Look.Fabric(c("#C8B48A"), c("#B39E73"), shorts = true)),
        Item("pt_track", Category.PANTS, "Eşofman", Look.Fabric(c("#55575F"), WHITE, pattern = Pattern.SIDE_STRIPE)),
        Item("pt_cargo", Category.PANTS, "Kargo", Look.Fabric(c("#6B7A3A"), c("#57642E"), pattern = Pattern.DOTS)),
        Item("pt_plaid", Category.PANTS, "Ekose", Look.Fabric(c("#B22D35"), BLACK, pattern = Pattern.CHECK)),
        Item("pt_camo", Category.PANTS, "Kamuflaj", Look.Fabric(c("#5E6B3C"), c("#3F4A28"), c("#8C8055"), pattern = Pattern.CAMO)),
        Item("pt_linen", Category.PANTS, "Keten", Look.Fabric(c("#EFE6D2"), c("#DED2B8"))),
        Item("pt_leather", Category.PANTS, "Deri", Look.Fabric(c("#1E1E22"), c("#3A3A42"), pattern = Pattern.SEAMS)),
        Item("pt_swim", Category.PANTS, "Mayo", Look.Fabric(c("#1F8FD6"), c("#FFE66D"), c("#FF6B6B"), pattern = Pattern.FLORAL, shorts = true)),
        Item("pt_pajama", Category.PANTS, "Pijama", Look.Fabric(c("#9CC7F0"), WHITE, pattern = Pattern.STRIPES_H)),

        // ---------------------------------------------------------------- ayakkabılar
        Item("sk_sneaker", Category.SHOES, "Spor ayakkabı", Look.Shoe(ShoeStyle.SNEAKER, WHITE, RED)),
        Item("sk_boot", Category.SHOES, "Bot", Look.Shoe(ShoeStyle.BOOT, c("#6B4226"), c("#4E2F1A"))),
        Item("sk_slipper", Category.SHOES, "Terlik", Look.Shoe(ShoeStyle.SLIPPER, c("#2EC4B6"), WHITE)),
        Item("sk_rain", Category.SHOES, "Yağmur çizmesi", Look.Shoe(ShoeStyle.RAIN, c("#FFCB2B"), c("#E6B220"))),
        Item("sk_patent", Category.SHOES, "Rugan", Look.Shoe(ShoeStyle.PATENT, BLACK, c("#5A5C66"))),
        Item("sk_skate", Category.SHOES, "Kaykay ayakkabısı", Look.Shoe(ShoeStyle.CHECKER, BLACK, WHITE)),
        Item("sk_sock", Category.SHOES, "Yün çorap", Look.Shoe(ShoeStyle.SOCK, RED, WHITE)),
        Item("sk_flipper", Category.SHOES, "Palet", Look.Shoe(ShoeStyle.FLIPPER, c("#2FBF71"), c("#1E9E5A"))),
        Item("sk_heel", Category.SHOES, "Topuklu", Look.Shoe(ShoeStyle.HEEL, c("#D7263D"), c("#A81B2E"))),
        Item("sk_light", Category.SHOES, "Işıklı", Look.Shoe(ShoeStyle.LIGHT, WHITE, c("#4CC9F0"))),
    )

    val byId: Map<String, Item> = items.associateBy { it.id }

    fun of(c: Category) = items.filter { it.category == c }

    val presets = listOf(
        Preset("Yazlık", "☀️", Outfit("hat_straw", "gl_sun", null, "sh_hawaii", "pt_shorts", "sk_slipper")),
        Preset("Kışlık", "❄️", Outfit("hat_pompom", null, "jk_puffer", "sh_sweater", "pt_jeans", "sk_boot")),
        Preset("Parti", "🎉", Outfit("hat_party", "gl_star", "jk_party", "sh_party", "pt_leather", "sk_light")),
        Preset("Sokak", "🛹", Outfit("hat_baseball", "gl_deal", "jk_hoodie", "sh_bolt", "pt_cargo", "sk_skate")),
        Preset("Spor", "⚽", Outfit(null, null, null, "sh_jersey", "pt_track", "sk_sneaker")),
        Preset("Şık", "🎩", Outfit(null, "gl_monocle", "jk_tux", "sh_white", "pt_leather", "sk_patent")),
        Preset("Plaj", "🏖", Outfit("hat_straw", "gl_retro", null, "sh_tank", "pt_swim", "sk_flipper")),
        Preset("Pijama", "😴", Outfit("hat_beanie", null, null, "sh_white", "pt_pajama", "sk_sock")),
        Preset("Kovboy", "🤠", Outfit("hat_cowboy", "gl_pilot", "jk_leather", "sh_white", "pt_jeans", "sk_boot")),
        Preset("Kral", "👑", Outfit("hat_crown", "gl_heart", "jk_fur", "sh_party", "pt_plaid", "sk_heel")),
    )
}

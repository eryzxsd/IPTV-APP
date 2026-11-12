package app.lumentv.data

import java.util.Locale

data class PlaylistPreset(val label: String, val url: String)

data class EpgSource(val code: String, val label: String, val isService: Boolean) {
    val url get() = Sources.epgshareUrl(code)
}

object Sources {
    val PLAYLISTS = listOf(
        PlaylistPreset("All channels", "https://iptv-org.github.io/iptv/index.m3u"),
        PlaylistPreset("Grouped by country", "https://iptv-org.github.io/iptv/index.country.m3u"),
        PlaylistPreset("Grouped by category", "https://iptv-org.github.io/iptv/index.category.m3u"),
        PlaylistPreset("Grouped by language", "https://iptv-org.github.io/iptv/index.language.m3u"),
    )

    const val EPGSHARE_BASE = "https://epgshare01.online/epgshare01/"
    fun epgshareUrl(code: String) = "${EPGSHARE_BASE}epg_ripper_$code.xml.gz"

    /** Files that exist on epgshare01 (checked against the directory listing). */
    private val COUNTRY_FILES = listOf(
        "AE1", "AL1", "AR1", "AT1", "AU1", "BA1", "BB1", "BE2", "BG1", "BR1", "BR2", "CA2", "CH1", "CL1", "CO1", "CR1",
        "CY1", "CZ1", "DE1", "DK1", "DO1", "EC1", "EG1", "ES1", "FI1", "FR1", "GR1", "HK1", "HR1", "HU1", "ID1", "IE1",
        "IL1", "IN1", "IN2", "IN4", "IT1", "JM1", "JP1", "JP2", "KE1", "KR1", "KZ1", "LT1", "LU1", "LV1", "MN1", "MT1",
        "MX1", "MY1", "NG1", "NL1", "NO1", "NZ1", "PA1", "PE1", "PH1", "PH2", "PK1", "PL1", "PT1", "RO1", "RO2", "RS1",
        "SA1", "SA2", "SE1", "SG1", "SK1", "SV1", "TH1", "TR1", "TR3", "UA1", "UK1", "US2", "US_LOCALS1", "US_SPORTS1",
        "UY1", "VN1", "ZA1",
    )
    private val SERVICE_FILES = listOf(
        "PLEX1" to "Plex", "DISTROTV1" to "DistroTV", "RAKUTEN1" to "Rakuten TV", "PEACOCK1" to "Peacock",
        "AMCCHANNELS1" to "AMC", "ALJAZEERA1" to "Al Jazeera", "BEIN1" to "beIN Sports", "VOA1" to "Voice of America",
        "TBNPLUS1" to "TBN+", "WHALETVPLUS1" to "Whale TV+", "ASIANTELEVISION1" to "Asian Television",
        "MUSICBOX1" to "Music Box", "DELUXEMUSIC1" to "Deluxe Music", "RALLY_TV1" to "Rally TV",
        "DIRECTVSPORTS1" to "DirecTV Sports", "ALL_SOURCES1" to "Everything (≈200 MB)",
    )

    val EPG_SOURCES: List<EpgSource> by lazy {
        val countries = COUNTRY_FILES.map { code ->
            val m = Regex("^([A-Z]{2})(_[A-Z]+)?(\\d)$").find(code)!!
            val cc = m.groupValues[1]
            val suffix = m.groupValues[2]
            val extra = when (suffix) {
                "_LOCALS" -> " Locals"
                "_SPORTS" -> " Sports"
                else -> ""
            }
            val siblings = COUNTRY_FILES.count { it.startsWith(cc) && !it.contains('_') }
            val number = if (suffix.isEmpty() && siblings > 1) " ${m.groupValues[3]}" else ""
            EpgSource(code, countryName(if (cc == "UK") "GB" else cc) + extra + number, false)
        }.sortedBy { it.label }
        countries + SERVICE_FILES.map { (c, l) -> EpgSource(c, l, true) }
    }

    val DEFAULT_PLAYLISTS = listOf(PLAYLISTS[0].url)
    val DEFAULT_EPG_CODES = setOf("US2", "UK1", "CA2", "AU1", "PLEX1", "DISTROTV1")
}

fun countryName(code: String): String =
    if (code.isEmpty()) "International" else Locale("", code).getDisplayCountry(Locale.ENGLISH).ifEmpty { code }

fun flag(code: String): String {
    if (!Regex("^[A-Z]{2}$").matches(code)) return "🌐"
    return code.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

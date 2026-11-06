package com.stocktrack.app.data

import java.util.Calendar

/**
 * The hidden themes of the start screen (Settings > GUI color scheme). Each runs from its first to its
 * last date, month * 100 + day, and most are the week that ends on the holiday itself. Vasario 16-oji
 * starts the day before, because Valentine's week ends on Feb 14, and Halloween's week ends a day early,
 * because the birthday starts on Oct 31. Greetings are always in Lithuanian.
 */
enum class Season(
    val label: String,
    val emoji: String,
    val title: String,
    private val subtitle: String,
    private val first: Int,
    private val last: Int
) {
    VALENTINE("Valentino diena", "💝", "Su Šv. Valentino diena!", "Tegul diena būna pilna meilės", 208, 214),
    INDEPENDENCE("Vasario 16-oji", "🇱🇹", "Su Vasario 16-ąja!", "Lietuvos valstybės atkūrimo diena", 215, 221),
    MARCH_11("Kovo 11-oji", "🇱🇹", "Su Kovo 11-ąja!", "Lietuvos nepriklausomybės atkūrimo diena", 305, 311),
    JONINES("Joninės", "🔥", "Linksmų Joninių!", "Tegul pražysta paparčio žiedas", 618, 624),
    MINDAUGINES("Mindauginės", "👑", "Su Valstybės diena!", "Mindauginės – karaliaus Mindaugo karūnavimo diena", 630, 706),
    HALLOWEEN("Helovinas", "🎃", "Šiurpiai linksmo Helovino!", "Saugokitės šikšnosparnių ir moliūgų!", 1024, 1030),
    BIRTHDAY("Gimtadienis", "🎂", "Su gimtadieniu, Beata!", "Daug laimės, sveikatos ir šypsenų!", 1031, 1102),
    CHRISTMAS("Kalėdos", "🎄", "Linksmų Šv. Kalėdų!", "Jaukių švenčių ir ramybės jūsų namams", 1219, 1225),
    NEW_YEAR("Naujieji metai", "🎆", "Laimingų Naujųjų metų!", "Sėkmingų %d-ųjų!", 1226, 101);

    private fun includes(date: Int): Boolean =
        if (first <= last) date in first..last else date >= first || date <= last

    /** [year] and [month] (1-12) are today's. The New Year's greeting names the year that is starting. */
    fun subtitle(year: Int, month: Int): String =
        if (this == NEW_YEAR) subtitle.format(if (month == 1) year else year + 1) else subtitle

    companion object {
        /** [month] is 1-12. */
        fun on(month: Int, day: Int): Season? = entries.firstOrNull { it.includes(month * 100 + day) }

        fun today(): Season? {
            val now = Calendar.getInstance()
            return on(now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        }
    }
}

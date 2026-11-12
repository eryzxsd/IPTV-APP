package app.lumentv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private fun ch(id: String, name: String, group: String, country: String = "US", logo: String = "x") =
        Channel(id, name, logo, listOf(group), "", "k$id", country, "https://e/$id.m3u8")

    private val letters = listOf("Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel")
    private val news = letters.map { ch("n$it", "$it News", "News") }
    private val abcFeeds = (1..5).map { ch("abc$it", "ABC News Live $it", "News") }
    private val sports = letters.take(6).map { ch("s$it", "$it Sports", "Sports") }
    private val kidsId = letters.take(6).map { ch("k$it", "$it Kids", "Kids", country = "ID") }
    private val movies = letters.take(6).map { ch("m$it", "$it Movies", "Movies") }
    private val all = news + abcFeeds + sports + kidsId + movies

    private fun rows(recent: List<Channel>, favs: List<Channel> = emptyList(), country: String = "US", offline: Set<String> = emptySet()) =
        Recommender.homeRows(all, recent, favs, country, hasGuideNow = { it.id.startsWith("s") }, isOffline = { it.id in offline }, playedOk = { false })

    @Test fun newUserGetsStarterPicksFromTheirCountry() {
        val r = rows(emptyList(), country = "ID")
        val forYou = r.first { it.id == "for-you" }
        assertEquals("Popular picks to get you started", forYou.subtitle)
        assertTrue("device country ranks first", forYou.channels.first().country == "ID")
        assertFalse(r.any { it.id == "continue" })
    }

    @Test fun recommendationsFollowWhatYouWatch() {
        val r = rows(recent = listOf(sports[0], sports[1]))
        val forYou = r.first { it.id == "for-you" }.channels
        assertEquals("Sports", forYou.first().groups.first())
        assertFalse("already watched channels are not recommended", forYou.any { it.id == sports[0].id || it.id == sports[1].id })
        val because = r.first { it.id == "because" }
        assertEquals("Because you watched Alpha Sports", because.title)
        assertTrue(because.channels.all { "Sports" in it.groups })
    }

    @Test fun numberedFeedsAreCollapsed() {
        val forYou = rows(recent = listOf(news[0])).first { it.id == "for-you" }.channels
        assertTrue("one ABC News Live feed at most", forYou.count { it.name.startsWith("ABC News Live") } <= 1)
        val news = rows(recent = listOf(news[0])).first { it.id == "cat:News" }.channels
        assertEquals(1, news.count { it.name.startsWith("ABC News Live") })
    }

    @Test fun offlineChannelsAreSkipped() {
        val r = rows(recent = listOf(sports[0]), offline = setOf(sports[1].id, sports[2].id))
        assertTrue(r.flatMap { it.channels }.none { it.id == sports[1].id || it.id == sports[2].id })
    }

    @Test fun categoriesAreMixed() {
        val forYou = rows(recent = listOf(news[0], movies[0])).first { it.id == "for-you" }.channels
        assertTrue("at most 6 from one category", forYou.groupBy { it.groups.first() }.values.all { it.size <= 6 })
        assertTrue(forYou.map { it.groups.first() }.toSet().size >= 2)
    }

    @Test fun rowsIncludeContinueOnNowFavoritesAndCategories() {
        val r = rows(recent = listOf(news[0]), favs = listOf(movies[2]))
        val ids = r.map { it.id }
        assertTrue(ids.containsAll(listOf("continue", "for-you", "on-now", "favorites")))
        assertTrue(ids.any { it.startsWith("cat:") })
        assertEquals(news[0], r.first { it.id == "continue" }.channels.first())
        assertEquals(Recommender.featured(r), r.first { it.id == "for-you" }.channels.first())
    }

    @Test fun family() {
        assertEquals("abc news live", Recommender.family(abcFeeds[3]))
        assertTrue(Recommender.sameFamily("cnn", "cnn international latin america"))
        assertTrue(Recommender.sameFamily("e latin america colombia", "e latin america"))
        assertFalse(Recommender.sameFamily("cnn", "cnbc"))
        assertFalse("whole words only", Recommender.sameFamily("star", "starz"))
        assertTrue(Recommender.sameFamily("cnn international latin america", "cnn international north america"))
        assertFalse("one shared word isn't enough", Recommender.sameFamily("fox news", "fox sports"))
        assertFalse(Recommender.sameFamily("bbc one", "bbc two"))
    }

    @Test fun rowsBringNewChannels() {
        val r = rows(recent = listOf(news[0]))
        val forYou = r.first { it.id == "for-you" }.channels.map { it.id }.toSet()
        val cat = r.first { it.id == "cat:News" }.channels.map { it.id }
        assertTrue("category row isn't a copy of the picks", cat.any { it !in forYou })
    }
}

package com.sugarbridge.app.sync

import com.sugarbridge.app.glucose.Reading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

/** Minute mode's aim at the next reading, against a LibreLinkUp that gets a reading every minute some seconds late. */
class AskClockTest {

    private val base = Instant.parse("2026-11-18T12:00:23Z")   // the sensor's readings come at 23 s past each minute

    /** The cloud: a reading every minute, there [delay] seconds after its sensor time. */
    private class Cloud(val start: Instant, minutes: Int, delay: (Int) -> Double) {
        val arrival: List<Instant> = (0 until minutes).map { start.plusSeconds(it * 60L).plusMillis((delay(it) * 1000).toLong()) }

        fun newestAt(t: Instant): Instant? =
            arrival.indices.lastOrNull { !arrival[it].isAfter(t) }?.let { start.plusSeconds(it * 60L) }

        fun index(sensorTime: Instant) = (Duration.between(start, sensorTime).seconds / 60).toInt()
    }

    /** How the asks went: requests a minute, and how long after reaching LibreLinkUp each reading reached the app. */
    private class Run(val asksPerMinute: Double, val extraSeconds: List<Double>) {
        val mean get() = extraSeconds.average()
        val worst get() = extraSeconds.max()
        override fun toString() = "asks a minute %.2f, after LibreLinkUp had it: mean %.1f s, worst %.1f s".format(asksPerMinute, mean, worst)
    }

    /** Asks the way minute mode does, from the first minute to the last but five. */
    private fun run(cloud: Cloud, minutes: Int, ask: (Instant, Instant?) -> Long): Run {
        var t = base.plusSeconds(7)
        val end = base.plusSeconds((minutes - 5) * 60L)
        var asks = 0
        val firstSeen = HashMap<Instant, Instant>()
        while (t.isBefore(end)) {
            asks++
            val newest = cloud.newestAt(t)
            if (newest != null) firstSeen.putIfAbsent(newest, t)
            t = t.plusMillis(ask(t, newest))
        }
        val extra = firstSeen.filterKeys { it.isAfter(base.plusSeconds(600)) }   // after ten minutes of learning
            .map { (time, seen) -> Duration.between(cloud.arrival[cloud.index(time)], seen).toMillis() / 1000.0 }
        return Run(asks / ((minutes - 5).toDouble()), extra)
    }

    private fun steady(cloud: Cloud, minutes: Int) = run(cloud, minutes) { _, _ -> 61_000L }   // the old way: a minute and the request

    private fun aimed(cloud: Cloud, minutes: Int, clock: AskClock = AskClock()) =
        run(cloud, minutes) { t, newest -> clock.next(t, newest, 60_000L) }

    @Test
    fun theAsksComeJustAfterTheReadingInsteadOfUpToAMinuteLater() {
        val cloud = Cloud(base, 600) { 25.0 }
        val before = steady(cloud, 600)
        val after = aimed(cloud, 600)
        assertTrue("before: $before", before.mean > 25)
        assertTrue("after: $after", after.mean < 7)
        assertTrue("after: $after", after.asksPerMinute < 1.15)
    }

    @Test
    fun aDelayThatWandersAFewSecondsCostsAFewSeconds() {
        val random = Random(7)
        val cloud = Cloud(base, 600) { 25 + random.nextDouble(-10.0, 10.0) }
        val after = aimed(cloud, 600)
        assertTrue("$after", after.mean < 12)
        assertTrue("$after", after.asksPerMinute < 1.15)
    }

    @Test
    fun aDelayLongerThanAMinuteIsLearnedToo() {
        val random = Random(11)
        val cloud = Cloud(base, 600) { 90 + random.nextDouble(-5.0, 5.0) }
        val after = aimed(cloud, 600)   // starting from 30 s, a whole minute too early
        assertTrue("$after", after.mean < 12)
        assertTrue("$after", after.asksPerMinute < 1.2)
    }

    @Test
    fun aReadingLateByChanceDoesNotSpoilTheAim() {
        val random = Random(3)
        val cloud = Cloud(base, 600) { 40.0 + if (random.nextDouble() < 0.05) 60 else 0 }
        val after = aimed(cloud, 600)
        assertTrue("$after", after.mean < 10)
        assertTrue("$after", after.asksPerMinute < 1.2)
    }

    @Test
    fun whenTheDelayChangesTheAimFollows() {
        val shorter = aimed(Cloud(base, 600) { if (it < 200) 60.0 else 20.0 }, 600)
        assertTrue("$shorter", shorter.mean < 8)
        val longer = aimed(Cloud(base, 600) { if (it < 200) 20.0 else 70.0 }, 600)
        assertTrue("$longer", longer.mean < 8)
    }

    @Test
    fun readingsUploadedSeveralAtOnceKeepTheRhythmOfAMinute() {
        // a sleeping phone lets LibreLink upload only every 5 minutes: nothing to aim at, no second asks
        val cloud = Cloud(base, 600) { 5 * 60 - (it % 5) * 60 + 7.0 }
        val clock = AskClock()
        val after = aimed(cloud, 600, clock)
        assertEquals("$after", 1.0, after.asksPerMinute, 0.03)
        assertEquals(AskClock.DEFAULT_OFFSET_S, clock.offsetSeconds)   // learned nothing wrong from it
    }

    @Test
    fun theFirstAskAimsAtTheNextReading() {
        val clock = AskClock(20)
        val asked = base.plusSeconds(5)   // 5 s after the newest reading's sensor time
        val wait = clock.next(asked, base, 60_000L)
        assertEquals(Duration.ofSeconds(75).toMillis(), wait)   // the next one at +60 s, there 20 s later
    }

    @Test
    fun anAskTooEarlyAsksOnceMoreAndAimsLater() {
        val clock = AskClock(20)
        clock.next(base.minusSeconds(40), base.minusSeconds(60), 60_000L)   // aims at base, asks at base + 20 s
        clock.next(base.plusSeconds(20), base, 60_000L)                     // it was there: a second earlier
        assertEquals(19, clock.offsetSeconds)
        val next = base.plusSeconds(60)
        assertEquals(AskClock.RETRY_MS, clock.next(next.plusSeconds(19), base, 60_000L))   // not there yet
        assertTrue(clock.secondTry)
        clock.next(next.plusSeconds(34), next, 60_000L)   // there now
        assertEquals(27, clock.offsetSeconds)
    }

    @Test
    fun anAskThatCameLateTeachesNothing() {
        // without "Keep the phone awake" the wait stretches while the phone sleeps: the reading was there, but the ask
        // can't tell how much sooner it would have been
        val clock = AskClock(20)
        clock.next(base.minusSeconds(40), base.minusSeconds(60), 60_000L)   // aims at base, asks at base + 20 s
        clock.next(base.plusSeconds(200), base.plusSeconds(120), 60_000L)   // three minutes late: aims at base + 240 s
        assertEquals(20, clock.offsetSeconds)
        // 30 s late, and the reading aimed at not there: no second ask, the next one is aimed anew
        assertEquals(30_000L, clock.next(base.plusSeconds(290), base.plusSeconds(180), 60_000L))
        assertEquals(20, clock.offsetSeconds)
        assertTrue(!clock.secondTry)
    }

    @Test
    fun anIntervalOfFiveMinutesStaysAboutFiveMinutes() {
        val cloud = Cloud(base, 1200) { 25.0 }
        val clock = AskClock()
        val after = run(cloud, 1200) { t, newest -> clock.next(t, newest, 300_000L) }
        // one ask every 5 minutes, and now and then a second one for a reading not there yet
        assertEquals("$after", 0.21, after.asksPerMinute, 0.02)
    }

    @Test
    fun withoutAReadingTheRhythmStays() {
        val clock = AskClock()
        assertEquals(60_000L, clock.next(base, null, 60_000L))
    }

    @Test
    fun theNewerReadingIsShown() {
        val old = Reading(base, 100.0, 3)
        val new = Reading(base.plusSeconds(60), 110.0, 4)
        assertSame(new, newerOf(old, new))
        assertSame(new, newerOf(new, old))
        assertSame(old, newerOf(old, null))
        assertSame(new, newerOf(null, new))
        assertNull(newerOf(null, null))
    }

    @Test
    fun aSyncKeepsTheReadingsFromThePhoneThatLibreLinkUpHasNotGotYet() {
        val cloud = (0..4L).map { Reading(base.plusSeconds(it * 60), 100.0 + it, 0) }
        val phone = listOf(Reading(base.plusSeconds(4 * 60 + 20), 104.0, 0), Reading(base.plusSeconds(5 * 60 + 20), 105.0, 3))
        val now = base.plusSeconds(6 * 60)
        val shown = shownAfterSync(cloud, cloud.dropLast(1) + phone, now)
        // LibreLinkUp's reading of the same minute wins, the newer one from the phone stays
        assertEquals(cloud + phone.last(), shown)
        // once LibreLinkUp has it too, its own replaces it
        val later = cloud + Reading(base.plusSeconds(5 * 60), 105.0, 4)
        assertEquals(later, shownAfterSync(later, shown, now))
        // nothing from LibreLinkUp: what was shown stays, but not older than the kept 13 hours
        assertEquals(shown, shownAfterSync(emptyList(), shown, now))
        assertEquals(emptyList<Reading>(), shownAfterSync(emptyList(), shown, now.plus(Duration.ofHours(14))))
    }
}

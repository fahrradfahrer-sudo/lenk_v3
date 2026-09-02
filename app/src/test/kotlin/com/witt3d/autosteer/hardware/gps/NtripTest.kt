package com.witt3d.autosteer.hardware.gps

import com.witt3d.autosteer.data.models.GpsFixStatus
import com.witt3d.autosteer.data.models.NtripConfig
import com.witt3d.autosteer.data.models.NtripProfile
import com.witt3d.autosteer.data.models.NtripStreamStats
import org.junit.Assert.assertEquals
import org.junit.Test

class NtripTest {

    @Test
    fun testGpsFixStatusDisplayTextMapping() {
        assertEquals("RTK", GpsFixStatus.RTK_FIX.displayText)
        assertEquals("FRTK", GpsFixStatus.FLOAT.displayText)
        assertEquals("DGPS", GpsFixStatus.DGPS.displayText)
        assertEquals("Single", GpsFixStatus.FIX.displayText)
        assertEquals("No Fix", GpsFixStatus.NO_FIX.displayText)
    }

    @Test
    fun testNtripStreamStatsFormatting() {
        val stats = NtripStreamStats(
            bytesReceivedPerSec = 512,
            bytesSentPerSec = 2048,
            totalBytesReceived = 1500000,
            totalBytesSent = 500
        )

        assertEquals("512 B/s", stats.formatRate(stats.bytesReceivedPerSec))
        assertEquals("2.00 KB/s", stats.formatRate(stats.bytesSentPerSec))
        assertEquals("1.43 MB", stats.formatTotalBytes(stats.totalBytesReceived))
        assertEquals("500 Bytes", stats.formatTotalBytes(stats.totalBytesSent))
    }

    @Test
    fun testNtripConfigProfilePrioritySorting() {
        val profile1 = NtripProfile(id = "1", name = "P1", priority = 1)
        val profile0 = NtripProfile(id = "0", name = "P0", priority = 0)
        val config = NtripConfig(profiles = listOf(profile1, profile0))

        val sorted = config.profiles.sortedBy { it.priority }
        assertEquals("P0", sorted[0].name)
        assertEquals("P1", sorted[1].name)
    }
}

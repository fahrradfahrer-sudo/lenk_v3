package com.witt3d.autosteer.hardware.gps

import com.witt3d.autosteer.data.models.GpsFixStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GpsTest {

    private lateinit var parser: NmeaParser

    @Before
    fun setUp() {
        parser = NmeaParser()
    }

    @Test
    fun testParseGgaSentence() {
        // Sample valid GNGGA sentence with matching checksum 5C
        // 4807.038 N -> 48 deg + 7.038 min = 48 + 0.1173 = 48.1173
        // 01131.000 E -> 11 deg + 31.000 min = 11 + 0.516666 = 11.516666
        val ggaSentence = "\$GNGGA,123519,4807.038,N,01131.000,E,4,08,0.9,545.4,M,46.9,M,,*5C"
        val result = parser.parseSentence(ggaSentence)

        assertNotNull(result)
        assertEquals(48.1173, result!!.latitude!!, 0.001)
        assertEquals(11.516666, result.longitude!!, 0.001)
        assertEquals(GpsFixStatus.RTK_FIX, result.fixStatus)
        assertEquals(545.4, result.altitudeMeters!!, 0.1)
        assertTrue(result.accuracyMeters!! < 0.1)
    }

    @Test
    fun testParseRmcSentence() {
        // Sample valid GNRMC sentence with matching checksum 74
        val rmcSentence = "\$GNRMC,123519,A,4807.038,N,01131.000,E,022.4,084.4,230394,003.1,W*74"
        val result = parser.parseSentence(rmcSentence)

        assertNotNull(result)
        assertEquals(48.1173, result!!.latitude!!, 0.001)
        assertEquals(11.516666, result.longitude!!, 0.001)
        assertEquals(41.4848, result.speedKmh!!, 0.1) // 22.4 knots * 1.852
        assertEquals(84.4, result.headingDegrees!!, 0.1)
    }

    @Test
    fun testUtmConversion() {
        // Munich, Germany: 48.137154 N, 11.576124 E => UTM Zone 32N
        val lat = 48.137154
        val lon = 11.576124

        val utm = UtmConverter.latLonToUtm(lat, lon)

        assertEquals(32, utm.zoneNumber)
        assertTrue(utm.isNorthern)
        assertEquals(691689.0, utm.easting, 500.0)
        assertEquals(5334863.0, utm.northing, 500.0)
    }

    @Test
    fun testInvalidChecksum() {
        val badChecksumSentence = "\$GNGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*99"
        val result = parser.parseSentence(badChecksumSentence)
        assertNull(result)
    }
}

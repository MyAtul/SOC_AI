package ai_soc_backend.incident.service;

import ai_soc_backend.incident.entity.Alert;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class DetectionWorkCorrelationTest {
    @Test
    void usesFixedFiveMinuteBucketsIncludingExactAndNegativeEpochBoundaries() {
        assertEquals(0, DetectionWorkProcessor.bucketStartEpoch(0));
        assertEquals(0, DetectionWorkProcessor.bucketStartEpoch(299));
        assertEquals(300, DetectionWorkProcessor.bucketStartEpoch(300));
        assertEquals(-300, DetectionWorkProcessor.bucketStartEpoch(-1));
    }

    @Test
    void canonicalizesHostWithNfcTrimAndRootLocaleCaseFolding() {
        String decomposed = "  CAFE\u0301-01  ";
        assertEquals(Normalizer.normalize("café-01", Normalizer.Form.NFC),
                DetectionWorkProcessor.canonicalHost(decomposed));
    }

    @Test
    void rejectsBlankLocalhostAndInvalidHostIdentifiersWithoutDnsResolution() {
        assertNull(DetectionWorkProcessor.canonicalHost("  "));
        assertNull(DetectionWorkProcessor.canonicalHost("LOCALHOST"));
        assertNull(DetectionWorkProcessor.canonicalHost("host with spaces"));
        assertEquals("host.example", DetectionWorkProcessor.canonicalHost(" HOST.EXAMPLE "));
    }

    @Test
    void keyIsStableLowercaseSha256OfAgreedCanonicalMaterial() {
        String key = DetectionWorkProcessor.correlationKey("host-a", 1_800_000_000L);
        assertEquals(64, key.length());
        assertTrue(key.matches("[0-9a-f]{64}"));
        assertEquals(key, DetectionWorkProcessor.correlationKey("host-a", 1_800_000_000L));
        assertNotEquals(key, DetectionWorkProcessor.correlationKey("host-b", 1_800_000_000L));
    }

    @Test
    void utcFieldIsRequestOnlySoTheExistingAlertResponseShapeStaysIntact() throws Exception {
        var field = Alert.class.getDeclaredField("eventTimestampUtc");
        JsonProperty property = field.getAnnotation(JsonProperty.class);
        assertNotNull(property);
        assertEquals(JsonProperty.Access.WRITE_ONLY, property.access());
        assertNotNull(Alert.class.getMethod("setEventTimestampUtc", Instant.class));
        assertNotNull(Alert.class.getMethod("getEventTimestampUtc"));
    }
}

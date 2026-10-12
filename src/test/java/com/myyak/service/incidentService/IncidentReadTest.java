package com.myyak.service.incidentService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.IncidentProperties;
import com.myyak.converter.IncidentConverter;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentReadTest {
    @Test void projectionDoesNotExposeApprovalOrStorageSecrets() throws Exception {
        var item = new HashMap<String, AttributeValue>();
        item.put("id", AttributeValue.builder().s("12345678-abcd-abcd-abcd-123456789012").build());
        item.put("state", AttributeValue.builder().s("AWAITING_APPROVAL").build());
        for (String key : List.of("approval", "reportKey", "resultKey", "reportMessageIds")) item.put(key, AttributeValue.builder().s("private-value").build());
        String json = new ObjectMapper().writeValueAsString(IncidentConverter.toIncident(DynamoIncidentReadStore.snapshot(item)));
        assertFalse(json.contains("private-value")); assertFalse(json.contains("reportKey"));
        assertTrue(json.contains("AWAITING_APPROVAL"));
    }
    @Test void cursorIsBoundToIncidentIndexAndRejectsArbitraryKeys() {
        String value = "1234567890:12345678-abcd-abcd-abcd-123456789012";
        String cursor = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        var key = DynamoIncidentReadStore.decodeCursor(cursor);
        assertEquals("INCIDENT", key.get("scope").s());
        assertEquals(cursor, DynamoIncidentReadStore.encodeCursor(key));
        for (String invalid : List.of("#bad", "a".repeat(129), Base64.getUrlEncoder().encodeToString("0:WORKER_LOCK".getBytes())))
            assertThrows(GeneralException.class, () -> DynamoIncidentReadStore.decodeCursor(invalid));
    }
    @Test void disabledReadDoesNotAccessStorageAndMissingItemIsNotSuccess() {
        IncidentReadStore store = mock(IncidentReadStore.class);
        IncidentProperties properties = new IncidentProperties();
        var service = new IncidentServiceImpl(store, properties);
        assertFalse(service.getIncidents(null).isEnabled()); verifyNoInteractions(store);
        properties.setEnabled(true); properties.setTableName("test");
        String id = "12345678-abcd-abcd-abcd-123456789012";
        when(store.findIncident(id)).thenReturn(Optional.empty());
        assertThrows(GeneralException.class, () -> service.getIncident(id));
        assertThrows(GeneralException.class, () -> service.getIncident("WORKER_LOCK"));
    }
}

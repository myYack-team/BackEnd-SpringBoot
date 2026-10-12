package com.myyak.service.incidentService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.IncidentProperties;
import com.myyak.domain.incident.IncidentSnapshot;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
public class DynamoIncidentReadStore implements IncidentReadStore {
    private final IncidentProperties properties;
    private final DynamoDbClient client;

    public DynamoIncidentReadStore(IncidentProperties properties) {
        this.properties = properties;
        this.client = DynamoDbClient.builder().region(Region.of(properties.getRegion()))
                .overrideConfiguration(builder -> builder.apiCallTimeout(Duration.ofSeconds(8))
                        .apiCallAttemptTimeout(Duration.ofSeconds(3))).build();
    }

    @Override
    public Page getIncidents(String cursor) {
        QueryRequest request = createQueryRequest(properties.getTableName(), cursor);
        try {
            QueryResponse response = client.query(request);
            List<IncidentSnapshot> items = response.items().stream().filter(this::retained).map(DynamoIncidentReadStore::snapshot).toList();
            return new Page(items, encodeCursor(response.lastEvaluatedKey()));
        } catch (SdkException error) { throw new GeneralException(ErrorStatus.ADMIN_UNAVAILABLE); }
    }

    static QueryRequest createQueryRequest(String tableName, String cursor) {
        Map<String, AttributeValue> start = decodeCursor(cursor);
        QueryRequest.Builder builder = QueryRequest.builder().tableName(tableName)
                .indexName("IncidentIndex").keyConditionExpression("#scope = :scope")
                .expressionAttributeNames(Map.of("#scope", "scope"))
                .expressionAttributeValues(Map.of(":scope", text("INCIDENT")))
                .scanIndexForward(false).limit(20);
        if (!start.isEmpty()) builder.exclusiveStartKey(start);
        return builder.build();
    }

    @Override
    public Optional<IncidentSnapshot> findIncident(String id) {
        try {
            Map<String, AttributeValue> item = client.getItem(GetItemRequest.builder().tableName(properties.getTableName())
                    .key(Map.of("pk", text("INC#" + id))).consistentRead(true).build()).item();
            return item.isEmpty() || !retained(item) ? Optional.empty() : Optional.of(snapshot(item));
        } catch (SdkException error) { throw new GeneralException(ErrorStatus.ADMIN_UNAVAILABLE); }
    }

    private boolean retained(Map<String, AttributeValue> item) {
        return "INCIDENT".equals(string(item, "scope")) && number(item, "expiresAt") > Instant.now().getEpochSecond();
    }

    static IncidentSnapshot snapshot(Map<String, AttributeValue> item) {
        List<String> frames = item.getOrDefault("frames", AttributeValue.builder().l(List.of()).build())
                .l().stream().map(AttributeValue::s).filter(Objects::nonNull).limit(30).toList();
        List<IncidentSnapshot.Event> history = item.getOrDefault("history", AttributeValue.builder().l(List.of()).build())
                .l().stream().limit(50).map(value -> new IncidentSnapshot.Event(number(value.m(), "at"), string(value.m(), "state"))).toList();
        return new IncidentSnapshot(string(item, "id"), string(item, "state"), number(item, "createdAt"), number(item, "updatedAt"),
                number(item, "occurrences"), string(item, "exception"), frames, string(item, "releaseSha"), string(item, "headSha"),
                string(item, "summary"), string(item, "validation"), string(item, "reason"), string(item, "prUrl"), string(item, "runUrl"),
                string(item, "deploymentUrl"), history);
    }

    static Map<String, AttributeValue> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return Map.of();
        try {
            if (cursor.length() > 128 || !cursor.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split(":", -1);
            if (parts.length != 2 || !parts[0].matches("[0-9]{1,12}") || !validId(parts[1])) throw new IllegalArgumentException();
            return Map.of("scope", text("INCIDENT"), "createdAt", AttributeValue.builder().n(parts[0]).build(), "pk", text("INC#" + parts[1]));
        } catch (IllegalArgumentException error) { throw new GeneralException(ErrorStatus._BAD_REQUEST); }
    }
    static String encodeCursor(Map<String, AttributeValue> key) {
        if (key == null || key.isEmpty()) return null;
        return Base64.getUrlEncoder().withoutPadding().encodeToString((key.get("createdAt").n() + ":" + key.get("pk").s().substring(4)).getBytes(StandardCharsets.UTF_8));
    }
    static boolean validId(String value) { return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"); }
    private static String string(Map<String, AttributeValue> item, String key) { return item.containsKey(key) ? item.get(key).s() : null; }
    private static long number(Map<String, AttributeValue> item, String key) { return item.containsKey(key) && item.get(key).n() != null ? Long.parseLong(item.get(key).n()) : 0; }
    private static AttributeValue text(String value) { return AttributeValue.builder().s(value).build(); }
    @PreDestroy public void close() { client.close(); }
}

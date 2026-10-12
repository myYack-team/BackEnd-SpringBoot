package com.myyak.web.dto.AdminDTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

public class IncidentResponseDTO {
    @Getter @AllArgsConstructor
    public static class Page {
        private boolean enabled;
        private List<Incident> items;
        private String nextCursor;
    }
    @Getter @AllArgsConstructor
    public static class Incident {
        private String id;
        private String state;
        private long createdAt;
        private long updatedAt;
        private long occurrences;
        private String exception;
        private List<String> frames;
        private String releaseSha;
        private String headSha;
        private String summary;
        private String validation;
        private String reason;
        private String prUrl;
        private String runUrl;
        private String deploymentUrl;
        private List<Event> history;
    }
    @Getter @AllArgsConstructor
    public static class Event {
        private long at;
        private String state;
    }
}

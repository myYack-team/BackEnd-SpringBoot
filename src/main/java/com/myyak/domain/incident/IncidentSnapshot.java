package com.myyak.domain.incident;

import java.util.List;

/** Read projection only. Approval secrets and storage object keys are deliberately absent. */
public record IncidentSnapshot(String id, String state, long createdAt, long updatedAt, long occurrences,
        String exception, List<String> frames, String releaseSha, String headSha, String summary,
        String validation, String reason, String prUrl, String runUrl, String deploymentUrl, List<Event> history) {
    public record Event(long at, String state) {}
}

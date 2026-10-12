package com.myyak.converter;

import com.myyak.domain.incident.IncidentSnapshot;
import com.myyak.web.dto.AdminDTO.IncidentResponseDTO;

public final class IncidentConverter {
    private IncidentConverter() {}
    public static IncidentResponseDTO.Incident toIncident(IncidentSnapshot item) {
        return new IncidentResponseDTO.Incident(item.id(), item.state(), item.createdAt(), item.updatedAt(), item.occurrences(),
                item.exception(), item.frames(), item.releaseSha(), item.headSha(), item.summary(), item.validation(), item.reason(),
                item.prUrl(), item.runUrl(), item.deploymentUrl(), item.history().stream()
                .map(event -> new IncidentResponseDTO.Event(event.at(), event.state())).toList());
    }
}

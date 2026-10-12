package com.myyak.service.incidentService;

import com.myyak.domain.incident.IncidentSnapshot;
import java.util.List;
import java.util.Optional;

public interface IncidentReadStore {
    record Page(List<IncidentSnapshot> items, String nextCursor) {}
    Page getIncidents(String cursor);
    Optional<IncidentSnapshot> findIncident(String id);
}

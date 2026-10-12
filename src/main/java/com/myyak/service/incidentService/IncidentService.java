package com.myyak.service.incidentService;

import com.myyak.web.dto.AdminDTO.IncidentResponseDTO;

public interface IncidentService {
    IncidentResponseDTO.Page getIncidents(String cursor);
    IncidentResponseDTO.Incident getIncident(String id);
}

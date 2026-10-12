package com.myyak.service.incidentService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.IncidentProperties;
import com.myyak.converter.IncidentConverter;
import com.myyak.web.dto.AdminDTO.IncidentResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {
    private final IncidentReadStore store;
    private final IncidentProperties properties;
    @Override
    public IncidentResponseDTO.Page getIncidents(String cursor) {
        if (!properties.isConfigured()) return new IncidentResponseDTO.Page(false, List.of(), null);
        var page = store.getIncidents(cursor);
        return new IncidentResponseDTO.Page(true, page.items().stream().map(IncidentConverter::toIncident).toList(), page.nextCursor());
    }
    @Override
    public IncidentResponseDTO.Incident getIncident(String id) {
        if (!DynamoIncidentReadStore.validId(id)) throw new GeneralException(ErrorStatus._BAD_REQUEST);
        if (!properties.isConfigured()) throw new GeneralException(ErrorStatus.ADMIN_UNAVAILABLE);
        return IncidentConverter.toIncident(store.findIncident(id).orElseThrow(() -> new GeneralException(ErrorStatus._NOT_FOUND)));
    }
}

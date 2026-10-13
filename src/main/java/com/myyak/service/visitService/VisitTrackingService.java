package com.myyak.service.visitService;

public interface VisitTrackingService {
    void recordVisit(Long userId);
    void recordVisitAfterCommit(Long userId);
}

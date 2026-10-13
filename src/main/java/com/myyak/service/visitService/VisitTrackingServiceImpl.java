package com.myyak.service.visitService;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.Set;

/** Optional activity statistics must not change authentication or business results. */
@Slf4j
@Service
public class VisitTrackingServiceImpl implements VisitTrackingService {
    private static final ZoneId VISIT_ZONE = ZoneId.of("Asia/Seoul");
    private static final long RETRY_DELAY_MS = 30_000;
    private final VisitService visits;
    private final Clock clock;
    private final Executor executor;
    private final Cache<Long, LocalDate> recordedDays = Caffeine.newBuilder()
            .maximumSize(20_000).expireAfterWrite(Duration.ofHours(24)).build();
    private final AtomicLong retryAfter = new AtomicLong();
    private final AtomicLong warningAfter = new AtomicLong();
    private final Set<Activity> pending = ConcurrentHashMap.newKeySet();

    private record Activity(Long userId, LocalDate date) {}

    @Autowired
    public VisitTrackingServiceImpl(VisitService visits, @Qualifier("visitStatisticsExecutor") Executor executor) {
        this(visits, Clock.systemUTC(), executor);
    }

    VisitTrackingServiceImpl(VisitService visits, Clock clock, Executor executor) {
        this.visits = visits;
        this.clock = clock.withZone(VISIT_ZONE);
        this.executor = executor;
    }

    @Override
    public void recordVisit(Long userId) {
        if (userId == null) return;
        LocalDate today = LocalDate.now(clock);
        if (today.equals(recordedDays.getIfPresent(userId)) || clock.millis() < retryAfter.get()) return;
        Activity activity = new Activity(userId, today);
        if (!pending.add(activity)) return;
        try {
            // The request/authentication transaction does not wait for a second database connection.
            executor.execute(() -> storeActivity(activity));
        } catch (RuntimeException exception) {
            pending.remove(activity);
            // Queue pressure must not discard work which has already been accepted.
            warnTrackingFailure();
        }
    }

    private void storeActivity(Activity activity) {
        try {
            if (activity.date().equals(recordedDays.getIfPresent(activity.userId()))) return;
            if (clock.millis() < retryAfter.get()) return;
            // Keep the request's server date even if the worker runs after midnight.
            visits.createVisit(activity.userId(), activity.date());
            recordedDays.asMap().merge(activity.userId(), activity.date(),
                    (previous, current) -> previous.isAfter(current) ? previous : current);
        } catch (GeneralException exception) {
            // An account may be deleted while its activity is queued. Do not pause other accounts.
            if (exception.getCode() != ErrorStatus.USER_NOT_FOUND) pauseTracking();
        } catch (RuntimeException exception) {
            pauseTracking();
        } finally {
            pending.remove(activity);
        }
    }

    private void pauseTracking() {
        long now = clock.millis();
        retryAfter.set(now + RETRY_DELAY_MS);
        warnTrackingFailure();
    }

    private void warnTrackingFailure() {
        long now = clock.millis();
        if (warningAfter.getAndSet(now + RETRY_DELAY_MS) <= now) {
            // No tokens, user identifiers, query parameters or SQL values in the log.
            log.warn("활성 계정 통계 저장에 실패했습니다. 다음 요청에서 재시도합니다.");
        }
    }

    @Override
    public void recordVisitAfterCommit(Long userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { recordVisit(userId); }
            });
        } else {
            recordVisit(userId);
        }
    }
}

package com.myyak.service.visitService;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.config.AsyncConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class VisitTrackingServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);
    private final VisitService visits = mock(VisitService.class);
    private final MutableClock clock = new MutableClock("2026-10-12T15:00:00Z");
    private final VisitTrackingServiceImpl tracking = new VisitTrackingServiceImpl(visits, clock, Runnable::run);

    @AfterEach
    void clearTransactionContext() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void cachesOnlySuccessfulUserDaysAndSkipsRepeatedRequests() {
        tracking.recordVisit(7L);
        tracking.recordVisit(7L);
        tracking.recordVisit(8L);
        tracking.recordVisit(8L);

        verify(visits).createVisit(7L, TODAY);
        verify(visits).createVisit(8L, TODAY);
    }

    @Test
    void recordsANewDayAtKoreanMidnightRatherThanUtcMidnight() {
        clock.setInstant("2026-10-12T14:59:59Z");
        tracking.recordVisit(7L);
        tracking.recordVisit(7L);
        verify(visits).createVisit(7L, TODAY.minusDays(1));
        clock.advance(Duration.ofSeconds(1));
        tracking.recordVisit(7L);
        tracking.recordVisit(7L);

        verify(visits).createVisit(7L, TODAY);
    }

    @Test
    void ignoresAMissingUserId() {
        tracking.recordVisit(null);
        tracking.recordVisitAfterCommit(null);
        verifyNoInteractions(visits);
    }

    @Test
    void failuresDoNotEscapeAndPauseAllUsersForThirtySecondsBeforeRetrying() {
        doThrow(new IllegalStateException("synthetic write failure")).doNothing()
                .when(visits).createVisit(7L, TODAY);

        assertThatCode(() -> tracking.recordVisit(7L)).doesNotThrowAnyException();
        tracking.recordVisit(7L);
        tracking.recordVisit(8L);
        clock.advance(Duration.ofMillis(29_999));
        tracking.recordVisit(7L);
        tracking.recordVisit(8L);
        verify(visits).createVisit(7L, TODAY);
        verify(visits, never()).createVisit(8L, TODAY);

        clock.advance(Duration.ofMillis(1));
        tracking.recordVisit(8L);
        tracking.recordVisit(7L);
        tracking.recordVisit(7L);
        tracking.recordVisit(8L);

        verify(visits, times(2)).createVisit(7L, TODAY);
        verify(visits).createVisit(8L, TODAY);
    }

    @Test
    void eachNewFailureRestartsTheCooldownWithoutCachingTheFailedUser() {
        doThrow(new IllegalStateException("first failure")).doNothing().when(visits).createVisit(7L, TODAY);
        doThrow(new GeneralException(ErrorStatus._INTERNAL_SERVER_ERROR)).when(visits).createVisit(8L, TODAY);

        tracking.recordVisit(7L);
        clock.advance(Duration.ofSeconds(30));
        tracking.recordVisit(8L);
        clock.advance(Duration.ofMillis(29_999));
        tracking.recordVisit(7L);
        verify(visits).createVisit(7L, TODAY);
        clock.advance(Duration.ofMillis(1));
        tracking.recordVisit(7L);

        verify(visits, times(2)).createVisit(7L, TODAY);
        verify(visits).createVisit(8L, TODAY);
    }

    @Test
    void failedCollectionIsRetriedOnTheActualNewDayAfterCooldownExpires() {
        clock.setInstant("2026-10-12T14:59:59Z");
        doThrow(new IllegalStateException("synthetic failure")).when(visits).createVisit(7L, TODAY.minusDays(1));

        tracking.recordVisit(7L);
        clock.advance(Duration.ofSeconds(1));
        tracking.recordVisit(7L);
        verify(visits, never()).createVisit(7L, TODAY);
        clock.advance(Duration.ofSeconds(29));
        tracking.recordVisit(7L);
        tracking.recordVisit(7L);

        verify(visits).createVisit(7L, TODAY.minusDays(1));
        verify(visits).createVisit(7L, TODAY);
    }

    @Test
    void failureWarningsDoNotIncludeExceptionDetailsOrUserIdentifiersAndAreThrottled() {
        Logger logger = (Logger) LoggerFactory.getLogger(VisitTrackingServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        doThrow(new IllegalStateException("synthetic-private-request-detail")).when(visits).createVisit(7L, TODAY);
        try {
            tracking.recordVisit(7L);
            tracking.recordVisit(7L);
            tracking.recordVisit(8L);

            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .doesNotContain("synthetic-private-request-detail", "userId", "7", "8");
            assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void queuesCollectionAndDeduplicatesWhileTheSameUserDayIsPending() {
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        queued.recordVisit(7L);
        queued.recordVisit(7L);

        assertThat(executor.jobs).hasSize(1);
        verifyNoInteractions(visits);
        executor.run(0);
        queued.recordVisit(7L);

        verify(visits).createVisit(7L, TODAY);
        assertThat(executor.jobs).isEmpty();
    }

    @Test
    void keepsTheOriginalServerDateEvenWhenTheWorkerRunsAfterMidnight() {
        clock.setInstant("2026-10-12T14:59:59Z");
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        queued.recordVisit(7L);
        clock.advance(Duration.ofSeconds(2));
        executor.run(0);

        verify(visits).createVisit(7L, TODAY.minusDays(1));
        verify(visits, never()).createVisit(7L, TODAY);
    }

    @Test
    void allowsDifferentPendingDaysAndLateOldCompletionDoesNotDowngradeTheSuccessCache() {
        clock.setInstant("2026-10-12T14:59:59Z");
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        queued.recordVisit(7L);
        clock.advance(Duration.ofSeconds(2));
        queued.recordVisit(7L);
        assertThat(executor.jobs).hasSize(2);
        executor.run(1);
        executor.run(0);
        queued.recordVisit(7L);

        verify(visits).createVisit(7L, TODAY.minusDays(1));
        verify(visits).createVisit(7L, TODAY);
        assertThat(executor.jobs).isEmpty();
    }

    @Test
    void queuedRequestsDoNotHammerAnUnavailableDatabaseDuringCooldownAndCanRetryLater() {
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        doThrow(new IllegalStateException("synthetic queued failure")).when(visits).createVisit(7L, TODAY);
        queued.recordVisit(7L);
        queued.recordVisit(8L);
        assertThatCode(() -> executor.run(0)).doesNotThrowAnyException();
        executor.run(0);
        verify(visits, never()).createVisit(8L, TODAY);
        clock.advance(Duration.ofSeconds(30));
        queued.recordVisit(8L);
        executor.run(0);

        verify(visits).createVisit(8L, TODAY);
    }

    @Test
    void executorRejectionDoesNotPauseAcceptedWorkAndTheRejectedKeyCanRetryImmediately() {
        AtomicBoolean reject = new AtomicBoolean(false);
        QueuedExecutor accepted = new QueuedExecutor();
        Executor executor = task -> {
            if (reject.get()) throw new RejectedExecutionException("synthetic full queue");
            accepted.execute(task);
        };
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);

        queued.recordVisit(7L);
        reject.set(true);
        assertThatCode(() -> queued.recordVisit(8L)).doesNotThrowAnyException();
        verifyNoInteractions(visits);
        accepted.run(0);
        verify(visits).createVisit(7L, TODAY);
        reject.set(false);
        queued.recordVisit(8L);
        queued.recordVisit(8L);
        assertThat(accepted.jobs).hasSize(1);
        accepted.run(0);

        verify(visits).createVisit(8L, TODAY);
    }

    @Test
    void aDeletedQueuedAccountDoesNotPauseOtherAccounts() {
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        doThrow(new GeneralException(ErrorStatus.USER_NOT_FOUND)).when(visits).createVisit(7L, TODAY);
        queued.recordVisit(7L);
        queued.recordVisit(8L);
        assertThatCode(() -> executor.run(0)).doesNotThrowAnyException();
        executor.run(0);

        verify(visits).createVisit(7L, TODAY);
        verify(visits).createVisit(8L, TODAY);
    }

    @Test
    void realStatisticsExecutorRejectsOverloadInsteadOfRunningWorkOnTheRequestThread() throws Exception {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new AsyncConfig().visitStatisticsExecutor();
        executor.initialize();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean ranOverflow = new AtomicBoolean();
        try {
            executor.execute(() -> {
                started.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            int queued = 0;
            boolean rejected = false;
            while (queued < 1024 && !rejected) {
                try {
                    executor.execute(() -> ranOverflow.set(true));
                    queued += 1;
                } catch (RejectedExecutionException expected) {
                    rejected = true;
                }
            }

            assertThat(rejected).isTrue();
            assertThat(queued).isBetween(1, 256);
            assertThat(ranOverflow).isFalse();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void recordsImmediatelyWhenThereIsNoTransactionSynchronization() {
        tracking.recordVisitAfterCommit(7L);
        verify(visits).createVisit(7L, TODAY);
    }

    @Test
    void waitsForCommitAndDeduplicatesRepeatedCallbacksForTheSameUser() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        queued.recordVisitAfterCommit(7L);
        queued.recordVisitAfterCommit(7L);
        assertThat(executor.jobs).isEmpty();
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        assertThat(executor.jobs).hasSize(1);
        verifyNoInteractions(visits);
        executor.run(0);

        verify(visits).createVisit(7L, TODAY);
    }

    @Test
    void rollbackDoesNotQueueOrCreateAVisit() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        QueuedExecutor executor = new QueuedExecutor();
        VisitTrackingServiceImpl queued = new VisitTrackingServiceImpl(visits, clock, executor);
        queued.recordVisitAfterCommit(7L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(executor.jobs).isEmpty();
        verifyNoInteractions(visits);
    }

    @Test
    void synchronizationWithoutAnActualTransactionRecordsImmediately() {
        TransactionSynchronizationManager.initSynchronization();
        tracking.recordVisitAfterCommit(7L);
        verify(visits).createVisit(7L, TODAY);
    }

    @Test
    void optionalCollectionFailureDoesNotBreakAnAlreadyCommittedAuthentication() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        doThrow(new IllegalStateException("synthetic post-commit failure")).when(visits).createVisit(7L, TODAY);
        tracking.recordVisitAfterCommit(7L);

        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().forEach(
                TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
        verify(visits).createVisit(7L, TODAY);
    }

    private static final class QueuedExecutor implements Executor {
        private final List<Runnable> jobs = new ArrayList<>();
        @Override public void execute(Runnable command) { jobs.add(command); }
        void run(int index) { jobs.remove(index).run(); }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;
        MutableClock(String instant) { this(new AtomicReference<>(Instant.parse(instant)), ZoneOffset.UTC); }
        private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }
        void setInstant(String instant) { current.set(Instant.parse(instant)); }
        void advance(Duration duration) { current.updateAndGet(instant -> instant.plus(duration)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(current, zone); }
        @Override public Instant instant() { return current.get(); }
    }
}

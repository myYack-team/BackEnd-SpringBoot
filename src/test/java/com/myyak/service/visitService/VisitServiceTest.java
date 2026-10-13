package com.myyak.service.visitService;

import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.domain.User;
import com.myyak.domain.UserVisit;
import com.myyak.repository.UserRepository;
import com.myyak.repository.UserVisitRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VisitServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserVisitRepository visits = mock(UserVisitRepository.class);

    VisitServiceImpl service(String instant) {
        return new VisitServiceImpl(users, visits, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }

    @Test void koreanMidnightUsesServerInstantAndRepeatedVisitDoesNotInsertAgain() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(User.builder().id(7L).build()));
        LocalDate before = LocalDate.of(2026, 10, 12), after = before.plusDays(1);
        when(visits.existsByUserIdAndVisitDate(7L, after)).thenReturn(false, true);
        service("2026-10-12T14:59:59Z").createVisit(7L, before);
        service("2026-10-12T15:00:00Z").createVisit(7L, after);
        service("2026-10-12T15:00:01Z").createVisit(7L, after);
        var records = org.mockito.ArgumentCaptor.forClass(UserVisit.class);
        verify(visits, times(2)).save(records.capture());
        assertThat(records.getAllValues()).extracting(UserVisit::getVisitDate).containsExactly(before, after);
        var order = inOrder(users, visits);
        order.verify(users).findByIdForUpdate(7L);
        order.verify(visits).existsByUserIdAndVisitDate(7L, before);
    }

    @Test void missingUserCannotCreateAVisit() {
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service("2026-10-12T15:00:00Z").createVisit(7L, LocalDate.of(2026, 10, 13)))
                .isInstanceOf(GeneralException.class);
        verifyNoInteractions(visits);
    }

    @Test void summaryFillsMissingDatesAndQueriesOnlyRecentKoreanDates() {
        LocalDate today = LocalDate.of(2026, 10, 13);
        UserVisitRepository.DailyCount count = mock(UserVisitRepository.DailyCount.class);
        when(count.getVisitDate()).thenReturn(today);
        when(count.getVisitors()).thenReturn(2L);
        when(visits.countDailyVisitors(today.minusDays(6), today)).thenReturn(List.of(count));
        var result = service("2026-10-12T15:00:00Z").getVisitSummary();
        assertThat(result.getToday()).isEqualTo(today);
        assertThat(result.getTimeZone()).isEqualTo("Asia/Seoul");
        assertThat(result.getTodayVisitors()).isEqualTo(2);
        assertThat(result.getYesterdayVisitors()).isZero();
        assertThat(result.getDaily()).extracting(day -> day.getDate()).containsExactly(
                today.minusDays(6), today.minusDays(5), today.minusDays(4), today.minusDays(3),
                today.minusDays(2), today.minusDays(1), today);
        assertThat(result.getDaily()).extracting(day -> day.getVisitors()).containsExactly(0L, 0L, 0L, 0L, 0L, 0L, 2L);
    }
}

package com.myyak.service.visitService;

import com.myyak.apiPayload.code.status.ErrorStatus;
import com.myyak.apiPayload.exception.GeneralException;
import com.myyak.converter.VisitConverter;
import com.myyak.domain.User;
import com.myyak.domain.UserVisit;
import com.myyak.repository.UserRepository;
import com.myyak.repository.UserVisitRepository;
import com.myyak.web.dto.VisitDTO.VisitResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;

@Service
@Transactional(readOnly = true)
public class VisitServiceImpl implements VisitService {
    private static final ZoneId VISIT_ZONE = ZoneId.of("Asia/Seoul");
    private final UserRepository users;
    private final UserVisitRepository visits;
    private final Clock clock;

    @Autowired
    public VisitServiceImpl(UserRepository users, UserVisitRepository visits) {
        this(users, visits, Clock.systemUTC());
    }

    VisitServiceImpl(UserRepository users, UserVisitRepository visits, Clock clock) {
        this.users = users;
        this.visits = visits;
        this.clock = clock.withZone(VISIT_ZONE);
    }

    @Override
    @Transactional
    public VisitResponseDTO.RecordResult createVisit(Long userId) {
        // Lock before the first consistent read: concurrent requests observe the committed visit.
        // Account deletion acquires the same parent lock before removing child rows.
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new GeneralException(ErrorStatus.USER_NOT_FOUND));
        ZonedDateTime now = ZonedDateTime.now(clock);
        LocalDate date = now.toLocalDate();
        if (!visits.existsByUserIdAndVisitDate(userId, date)) {
            visits.save(UserVisit.builder().user(user).visitDate(date).build());
        }
        // Round up so the app's relative timer does not fire before the server's next day.
        long remainingMillis = Duration.between(now, date.plusDays(1).atStartOfDay(VISIT_ZONE)).toMillis();
        long nextVisitInSeconds = Math.max(1, (remainingMillis + 999) / 1000);
        return VisitConverter.toRecordResult(date, nextVisitInSeconds);
    }

    @Override
    public VisitResponseDTO.Summary getVisitSummary() {
        LocalDate today = LocalDate.now(clock);
        return VisitConverter.toSummary(today, visits.countDailyVisitors(today.minusDays(6), today));
    }
}

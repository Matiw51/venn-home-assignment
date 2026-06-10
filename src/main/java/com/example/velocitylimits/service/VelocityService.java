package com.example.velocitylimits.service;

import com.example.velocitylimits.dto.LoadRequest;
import com.example.velocitylimits.dto.LoadResponse;
import com.example.velocitylimits.model.LoadAttempt;
import com.example.velocitylimits.repository.LoadAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;

/**
 * Enforces velocity limits on fund load attempts.
 *
 * <p>Each customer is subject to three limits:
 * <ul>
 *   <li>Maximum $5,000 loaded per day</li>
 *   <li>Maximum $20,000 loaded per week (Mon–Sun UTC)</li>
 *   <li>Maximum 3 accepted loads per day</li>
 * </ul>
 *
 * <p>Declined loads do not count against any limit. Days reset at midnight UTC;
 * weeks reset at Monday 00:00:00 UTC.
 */
@Service
public class VelocityService {

    private static final Logger log = LoggerFactory.getLogger(VelocityService.class);

    private static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("5000.00");
    private static final BigDecimal MAX_WEEKLY_AMOUNT = new BigDecimal("20000.00");
    private static final int MAX_DAILY_LOADS = 3;

    private final LoadAttemptRepository repository;

    public VelocityService(LoadAttemptRepository repository) {
        this.repository = repository;
    }

    /**
     * Processes a fund load attempt.
     *
     * <p><b>Concurrency note:</b> this method uses the default READ_COMMITTED isolation level.
     * Two concurrent requests for the same customer could theoretically both pass the velocity
     * check before either commits, resulting in a limit overshoot. The correct fix is
     * pessimistic per-customer locking (SELECT FOR UPDATE on a customer row), which would
     * require a dedicated Customer entity. Acceptable for this scope; should be addressed
     * before production use at scale.
     *
     * @return the result of the attempt, or an empty Optional if the load was a duplicate
     *         or had a zero/negative amount (no output should be produced for these cases)
     */
    @Transactional
    public Optional<LoadResponse> process(LoadRequest request) {
        String customerId = request.getCustomerId();
        String loadId = request.getId();

        // Ignore duplicate load IDs per customer (no response per spec)
        if (repository.existsByCustomerIdAndLoadId(customerId, loadId)) {
            log.debug("Duplicate ignored: customerId={}, loadId={}", customerId, loadId);
            return Optional.empty();
        }

        BigDecimal amount = request.getLoadAmount();

        // Zero-amount loads are a no-op — no response, no DB record
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Zero/negative amount ignored: customerId={}, loadId={}", customerId, loadId);
            return Optional.empty();
        }

        Instant loadTime = request.getTime();
        boolean accepted = checkVelocityLimits(customerId, amount, loadTime);

        repository.save(new LoadAttempt(loadId, customerId, amount, loadTime, accepted));

        log.info("Processed: customerId={}, loadId={}, amount={}, accepted={}",
                customerId, loadId, amount, accepted);

        return Optional.of(new LoadResponse(loadId, customerId, accepted));
    }

    private boolean checkVelocityLimits(String customerId, BigDecimal amount, Instant loadTime) {
        Instant dayStart = getDayStart(loadTime);
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        Instant weekStart = getWeekStart(loadTime);
        Instant weekEnd = weekStart.plus(7, ChronoUnit.DAYS);

        long dailyCount = repository.countAcceptedBetween(customerId, dayStart, dayEnd);
        if (dailyCount >= MAX_DAILY_LOADS) {
            log.debug("Declined (daily count): customerId={}, count={}", customerId, dailyCount);
            return false;
        }

        BigDecimal dailyTotal = repository.sumAcceptedAmountBetween(customerId, dayStart, dayEnd);
        if (dailyTotal.add(amount).compareTo(MAX_DAILY_AMOUNT) > 0) {
            log.debug("Declined (daily amount): customerId={}, current={}, requested={}", customerId, dailyTotal, amount);
            return false;
        }

        BigDecimal weeklyTotal = repository.sumAcceptedAmountBetween(customerId, weekStart, weekEnd);
        if (weeklyTotal.add(amount).compareTo(MAX_WEEKLY_AMOUNT) > 0) {
            log.debug("Declined (weekly amount): customerId={}, current={}, requested={}", customerId, weeklyTotal, amount);
            return false;
        }

        // all limits passed
        return true;
    }

    private Instant getDayStart(Instant time) {
        return time.truncatedTo(ChronoUnit.DAYS);
    }

    private Instant getWeekStart(Instant time) {
        ZonedDateTime zdt = time.atZone(ZoneOffset.UTC);
        return zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();
    }
}

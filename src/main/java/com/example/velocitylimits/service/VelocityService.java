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

@Service
public class VelocityService {

    private static final Logger log = LoggerFactory.getLogger(VelocityService.class);

    static final BigDecimal MAX_DAILY_AMOUNT = new BigDecimal("5000.00");
    static final BigDecimal MAX_WEEKLY_AMOUNT = new BigDecimal("20000.00");
    static final int MAX_DAILY_LOADS = 3;

    private final LoadAttemptRepository repository;

    public VelocityService(LoadAttemptRepository repository) {
        this.repository = repository;
    }

    /**
     * Processes a fund load attempt. Returns null if the load ID has already been seen
     * for this customer (duplicate), otherwise returns whether the load was accepted.
     */
    @Transactional
    public LoadResponse process(LoadRequest request) {
        String customerId = request.getCustomerId();
        String loadId = request.getId();

        // Ignore duplicate load IDs per customer (no response per spec)
        if (repository.existsByCustomerIdAndLoadId(customerId, loadId)) {
            log.debug("Duplicate ignored: customerId={}, loadId={}", customerId, loadId);
            return null;
        }

        Instant loadTime = Instant.parse(request.getTime());
        BigDecimal amount = parseAmount(request.getLoadAmount());
        boolean accepted = checkVelocityLimits(customerId, amount, loadTime);

        repository.save(new LoadAttempt(loadId, customerId, amount, loadTime, accepted));

        log.info("Processed: customerId={}, loadId={}, amount={}, accepted={}",
                customerId, loadId, amount, accepted);

        return new LoadResponse(loadId, customerId, accepted);
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

        BigDecimal dailyTotal = getAcceptedAmount(customerId, dayStart, dayEnd);
        if (dailyTotal.add(amount).compareTo(MAX_DAILY_AMOUNT) > 0) {
            log.debug("Declined (daily amount): customerId={}, current={}, requested={}", customerId, dailyTotal, amount);
            return false;
        }

        BigDecimal weeklyTotal = getAcceptedAmount(customerId, weekStart, weekEnd);
        if (weeklyTotal.add(amount).compareTo(MAX_WEEKLY_AMOUNT) > 0) {
            log.debug("Declined (weekly amount): customerId={}, current={}, requested={}", customerId, weeklyTotal, amount);
            return false;
        }

        return true;
    }

    private BigDecimal getAcceptedAmount(String customerId, Instant start, Instant end) {
        return Optional.ofNullable(repository.sumAcceptedAmountBetween(customerId, start, end))
                .orElse(BigDecimal.ZERO);
    }

    Instant getDayStart(Instant time) {
        return time.truncatedTo(ChronoUnit.DAYS);
    }

    Instant getWeekStart(Instant time) {
        ZonedDateTime zdt = time.atZone(ZoneOffset.UTC);
        return zdt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .truncatedTo(ChronoUnit.DAYS)
                .toInstant();
    }

    private BigDecimal parseAmount(String loadAmount) {
        return new BigDecimal(loadAmount.replace("$", "").trim());
    }
}

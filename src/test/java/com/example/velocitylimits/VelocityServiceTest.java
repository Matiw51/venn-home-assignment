package com.example.velocitylimits;

import com.example.velocitylimits.dto.LoadRequest;
import com.example.velocitylimits.dto.LoadResponse;
import com.example.velocitylimits.service.VelocityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice tests for VelocityService. Uses @DataJpaTest to load only the JPA layer
 * and the service itself — no full Spring Boot context, no web layer, no CommandLineRunner.
 * Each test runs in a transaction that is rolled back automatically.
 */
@DataJpaTest
@Import(VelocityService.class)
class VelocityServiceTest {

    @Autowired
    private VelocityService velocityService;

    private LoadRequest request(String id, String customerId, String amount, String time) {
        LoadRequest r = new LoadRequest();
        r.setId(id);
        r.setCustomerId(customerId);
        r.setLoadAmount(new BigDecimal(amount.replace("$", "").trim()));
        r.setTime(Instant.parse(time));
        return r;
    }

    @Test
    void shouldAcceptValidLoad() {
        Optional<LoadResponse> response = velocityService.process(
                request("1", "cust1", "$100.00", "2018-01-01T10:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenDailyAmountExceeded() {
        velocityService.process(request("1", "cust1", "$4500.00", "2018-01-01T00:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("2", "cust1", "$600.00", "2018-01-01T01:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isFalse();
    }

    @Test
    void shouldAcceptExactDailyLimit() {
        velocityService.process(request("1", "cust1", "$2000.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$2000.00", "2018-01-01T01:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("3", "cust1", "$1000.00", "2018-01-01T02:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenDailyLoadCountExceeded() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("4", "cust1", "$100.00", "2018-01-01T03:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isFalse();
    }

    @Test
    void shouldResetDailyLimitsNextDay() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("4", "cust1", "$100.00", "2018-01-02T00:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenWeeklyAmountExceeded() {
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z")); // Mon
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z")); // Tue
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z")); // Wed
        velocityService.process(request("4", "cust1", "$5000.00", "2018-01-04T00:00:00Z")); // Thu

        Optional<LoadResponse> response = velocityService.process(
                request("5", "cust1", "$0.01", "2018-01-05T00:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isFalse();
    }

    @Test
    void shouldAcceptExactWeeklyLimit() {
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z")); // Mon
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z")); // Tue
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z")); // Wed

        Optional<LoadResponse> response = velocityService.process(
                request("4", "cust1", "$5000.00", "2018-01-04T00:00:00Z")); // Thu — exactly $20k

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldResetWeeklyLimitsNextWeek() {
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z"));
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z"));
        velocityService.process(request("4", "cust1", "$5000.00", "2018-01-04T00:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("5", "cust1", "$5000.00", "2018-01-08T00:00:00Z")); // next Mon

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldResetDailyLimitsAtExactMidnightUTC() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T23:59:57Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T23:59:58Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T23:59:59Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("4", "cust1", "$100.00", "2018-01-02T00:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldResetWeeklyLimitsAtMondayMidnightUTC() {
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z")); // Mon
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z")); // Tue
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z")); // Wed
        velocityService.process(request("4", "cust1", "$5000.00", "2018-01-04T00:00:00Z")); // Thu

        Optional<LoadResponse> stillThisWeek = velocityService.process(
                request("5", "cust1", "$0.01", "2018-01-07T23:59:59Z")); // Sun
        assertThat(stillThisWeek).isPresent();
        assertThat(stillThisWeek.get().isAccepted()).isFalse();

        Optional<LoadResponse> newWeek = velocityService.process(
                request("6", "cust1", "$5000.00", "2018-01-08T00:00:00Z")); // Mon
        assertThat(newWeek).isPresent();
        assertThat(newWeek.get().isAccepted()).isTrue();
    }

    @Test
    void shouldReturnEmptyForDuplicateLoadId() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("1", "cust1", "$100.00", "2018-01-01T01:00:00Z"));

        assertThat(response).isEmpty();
    }

    @Test
    void shouldTreatSameLoadIdAsDuplicateOnlyForSameCustomer() {
        velocityService.process(request("42", "cust1", "$100.00", "2018-01-01T00:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("42", "cust2", "$100.00", "2018-01-01T00:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldNotCountDeclinedLoadsAgainstLimits() {
        velocityService.process(request("1", "cust1", "$4500.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$600.00", "2018-01-01T01:00:00Z")); // declined

        Optional<LoadResponse> response = velocityService.process(
                request("3", "cust1", "$400.00", "2018-01-01T02:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldNotCountDeclinedLoadsAgainstWeeklyAmount() {
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z")); // Mon
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z")); // Tue
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z")); // Wed
        velocityService.process(request("4", "cust1", "$6000.00", "2018-01-04T00:00:00Z")); // Thu — declined (daily)

        Optional<LoadResponse> response = velocityService.process(
                request("5", "cust1", "$5000.00", "2018-01-04T01:00:00Z")); // weekly total still $15k

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenWeeklyLimitExceededWithoutHittingDailyLimit() {
        velocityService.process(request("1", "cust1", "$4999.00", "2018-01-01T00:00:00Z")); // Mon
        velocityService.process(request("2", "cust1", "$4999.00", "2018-01-02T00:00:00Z")); // Tue
        velocityService.process(request("3", "cust1", "$4999.00", "2018-01-03T00:00:00Z")); // Wed
        velocityService.process(request("4", "cust1", "$4999.00", "2018-01-04T00:00:00Z")); // Thu — $19,996

        Optional<LoadResponse> response = velocityService.process(
                request("5", "cust1", "$5.00", "2018-01-05T00:00:00Z")); // Fri — only $4 left

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isFalse();
    }

    @Test
    void shouldTrackLimitsSeparatelyPerCustomer() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));

        Optional<LoadResponse> response = velocityService.process(
                request("1", "cust2", "$100.00", "2018-01-01T00:00:00Z"));

        assertThat(response).isPresent();
        assertThat(response.get().isAccepted()).isTrue();
    }

    @Test
    void shouldReturnEmptyForZeroAmountLoad() {
        Optional<LoadResponse> response = velocityService.process(
                request("1", "cust1", "$0.00", "2018-01-01T00:00:00Z"));

        assertThat(response).isEmpty();

        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));
        velocityService.process(request("4", "cust1", "$100.00", "2018-01-01T03:00:00Z"));

        // 3 real loads accepted — zero load didn't consume a slot
        Optional<LoadResponse> fourth = velocityService.process(
                request("5", "cust1", "$100.00", "2018-01-01T04:00:00Z"));
        assertThat(fourth).isPresent();
        assertThat(fourth.get().isAccepted()).isFalse();
    }
}

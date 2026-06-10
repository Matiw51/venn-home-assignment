package com.example.velocitylimits;

import com.example.velocitylimits.dto.LoadRequest;
import com.example.velocitylimits.dto.LoadResponse;
import com.example.velocitylimits.processor.LoadProcessor;
import com.example.velocitylimits.service.VelocityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VelocityServiceTest {

    // Prevent CommandLineRunner from processing input.txt during tests
    @MockBean
    private LoadProcessor loadProcessor;

    @Autowired
    private VelocityService velocityService;

    // --- Helpers ---

    private LoadRequest request(String id, String customerId, String amount, String time) {
        LoadRequest r = new LoadRequest();
        r.setId(id);
        r.setCustomerId(customerId);
        r.setLoadAmount(amount);
        r.setTime(time);
        return r;
    }

    // --- Tests ---

    @Test
    void shouldAcceptValidLoad() {
        LoadResponse response = velocityService.process(
                request("1", "cust1", "$100.00", "2018-01-01T10:00:00Z"));

        assertThat(response).isNotNull();
        assertThat(response.isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenDailyAmountExceeded() {
        // $4500 accepted, then $600 would push over $5000
        velocityService.process(request("1", "cust1", "$4500.00", "2018-01-01T00:00:00Z"));

        LoadResponse response = velocityService.process(
                request("2", "cust1", "$600.00", "2018-01-01T01:00:00Z"));

        assertThat(response.isAccepted()).isFalse();
    }

    @Test
    void shouldAcceptExactDailyLimit() {
        velocityService.process(request("1", "cust1", "$2000.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$2000.00", "2018-01-01T01:00:00Z"));

        LoadResponse response = velocityService.process(
                request("3", "cust1", "$1000.00", "2018-01-01T02:00:00Z"));

        assertThat(response.isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenDailyLoadCountExceeded() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));

        // 4th load same day
        LoadResponse response = velocityService.process(
                request("4", "cust1", "$100.00", "2018-01-01T03:00:00Z"));

        assertThat(response.isAccepted()).isFalse();
    }

    @Test
    void shouldResetDailyLimitsNextDay() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));

        // Next day — count resets
        LoadResponse response = velocityService.process(
                request("4", "cust1", "$100.00", "2018-01-02T00:00:00Z"));

        assertThat(response.isAccepted()).isTrue();
    }

    @Test
    void shouldDeclineWhenWeeklyAmountExceeded() {
        // Load $5000/day for 4 days (Mon-Thu) = $20,000
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z")); // Mon
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z")); // Tue
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z")); // Wed
        velocityService.process(request("4", "cust1", "$5000.00", "2018-01-04T00:00:00Z")); // Thu

        // Friday — weekly total already $20,000
        LoadResponse response = velocityService.process(
                request("5", "cust1", "$0.01", "2018-01-05T00:00:00Z"));

        assertThat(response.isAccepted()).isFalse();
    }

    @Test
    void shouldResetWeeklyLimitsNextWeek() {
        velocityService.process(request("1", "cust1", "$5000.00", "2018-01-01T00:00:00Z")); // Mon week 1
        velocityService.process(request("2", "cust1", "$5000.00", "2018-01-02T00:00:00Z"));
        velocityService.process(request("3", "cust1", "$5000.00", "2018-01-03T00:00:00Z"));
        velocityService.process(request("4", "cust1", "$5000.00", "2018-01-04T00:00:00Z")); // $20k total

        // Next Monday — new week
        LoadResponse response = velocityService.process(
                request("5", "cust1", "$5000.00", "2018-01-08T00:00:00Z"));

        assertThat(response.isAccepted()).isTrue();
    }

    @Test
    void shouldReturnNullForDuplicateLoadId() {
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));

        // Same ID again
        LoadResponse response = velocityService.process(
                request("1", "cust1", "$100.00", "2018-01-01T01:00:00Z"));

        assertThat(response).isNull();
    }

    @Test
    void shouldNotCountDeclinedLoadsAgainstLimits() {
        // First load accepted
        velocityService.process(request("1", "cust1", "$4500.00", "2018-01-01T00:00:00Z"));
        // Second declined (would exceed daily)
        velocityService.process(request("2", "cust1", "$600.00", "2018-01-01T01:00:00Z"));
        // Third — still only $4500 accepted, so $400 is fine
        LoadResponse response = velocityService.process(
                request("3", "cust1", "$400.00", "2018-01-01T02:00:00Z"));

        assertThat(response.isAccepted()).isTrue();
    }

    @Test
    void shouldTrackLimitsSeparatelyPerCustomer() {
        // Customer 1 hits daily count limit
        velocityService.process(request("1", "cust1", "$100.00", "2018-01-01T00:00:00Z"));
        velocityService.process(request("2", "cust1", "$100.00", "2018-01-01T01:00:00Z"));
        velocityService.process(request("3", "cust1", "$100.00", "2018-01-01T02:00:00Z"));

        // Customer 2 is unaffected
        LoadResponse response = velocityService.process(
                request("1", "cust2", "$100.00", "2018-01-01T00:00:00Z"));

        assertThat(response.isAccepted()).isTrue();
    }
}

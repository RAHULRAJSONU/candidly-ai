package ai.candidly.career.emailintake;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

class HeuristicDateTimeExtractorTest {

    @Test
    void extractsDateAndTimeWithMeridiem() {
        var result = HeuristicDateTimeExtractor.extract("We'd like to meet on Sep 24, 2026 at 2:00 PM IST.");

        assertThat(result).isPresent();
        String formatted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC).format(result.get());
        assertThat(formatted).isEqualTo("2026-09-24 14:00");
    }

    @Test
    void fallsBackToNoonWhenOnlyADateIsPresent() {
        var result = HeuristicDateTimeExtractor.extract("Let's plan for Oct 2, 2026.");

        assertThat(result).isPresent();
    }

    @Test
    void returnsEmptyWhenNoDateFound() {
        assertThat(HeuristicDateTimeExtractor.extract("Thanks for applying, we'll be in touch soon.")).isEmpty();
        assertThat(HeuristicDateTimeExtractor.extract(null)).isEmpty();
    }

}

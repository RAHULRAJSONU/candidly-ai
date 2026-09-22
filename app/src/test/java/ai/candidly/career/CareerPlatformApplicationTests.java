package ai.candidly.career;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Deliberately just a context-load smoke test with no live provider keys needed:
 * SkillNormalizationService lazily embeds the taxonomy on first use rather than at
 * startup, and the RestClient beans only need base-url/key values (blank is fine) to
 * construct, not a live network call.
 */
@SpringBootTest
class CareerPlatformApplicationTests {

    @Test
    void contextLoads() {
    }
}

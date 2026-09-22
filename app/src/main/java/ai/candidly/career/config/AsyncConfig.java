package ai.candidly.career.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * docs/01 §1: "Use Executors.newVirtualThreadPerTaskExecutor() for I/O-bound ATS
 * polling" (and, here, tailoring - which is dominated by waiting on Groq/TypeSafe HTTP
 * calls, not CPU work). {@code @EnableScheduling} backs the discovery poller.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean
    public TaskExecutor tailoringTaskExecutor() {
        return new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor());
    }
}

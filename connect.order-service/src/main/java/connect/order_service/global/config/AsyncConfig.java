package connect.order_service.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig {

    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    // 알림 전용 스레드풀, 비동기 스레드에서도 Zipkin trace가 이어지도록 컨텍스트 전파
    @Bean(name = NOTIFICATION_EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("notify-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        // 풀/큐 포화 시 주문 요청이 실패하지 않도록 알림은 로그만 남기고 버림
        executor.setRejectedExecutionHandler((r, ex) ->
                log.warn("알림 스레드풀 포화로 알림 작업이 버려졌습니다. active={}, queue={}",
                        ex.getActiveCount(), ex.getQueue().size()));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}

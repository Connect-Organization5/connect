package connect.order_service.global.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class SlackWebhookSender {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackWebhookSender(RestClient.Builder restClientBuilder,
                              @Value("${slack.webhook-url}") String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookUrl = webhookUrl;
    }

    public void send(String text) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.info("[Slack 미설정 - 로그로 대체]\n{}", text);
            return;
        }
        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("text", text))
                .retrieve()
                .toBodilessEntity();
    }
}

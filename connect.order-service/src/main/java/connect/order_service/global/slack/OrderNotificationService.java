package connect.order_service.global.slack;

import connect.order_service.domain.client.clientInterface.UserClient;
import connect.order_service.domain.client.dto.response.UserInfo;
import connect.order_service.global.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationService {
    private final UserClient userClient;
    private final SlackWebhookSender slackWebhookSender;

    // 주문자와 공급 업체 담당자를 멘션해서 알림 채널에 발송 (주문 응답과 분리해 비동기 처리)
    @Async(AsyncConfig.NOTIFICATION_EXECUTOR)
    public void notifyOrderCreated(OrderCreatedNotification notification) {
        try {
            UserInfo orderer = userClient.getUser(notification.ordererId());
            UserInfo supplierManager = userClient.getUser(notification.supplierManagerId());
            slackWebhookSender.send(buildOrderCreatedMessage(notification, orderer.slackId(), supplierManager.slackId()));
        } catch (Exception e) {
            log.warn("주문 생성 Slack 알림 실패 - orderId={}", notification.orderId(), e);
        }
    }

    private String buildOrderCreatedMessage(OrderCreatedNotification notification, String ordererSlackId, String managerSlackId) {
        return """
                [주문 생성 알림] %s %s
                주문번호: %s
                상품 ID: %s
                수량: %d
                수령인: %s
                요청사항: %s
                주문일시: %s
                """.formatted(
                mention(ordererSlackId),
                mention(managerSlackId),
                notification.orderId(),
                notification.productId(),
                notification.quantity(),
                notification.receiver(),
                notification.requestMessage() == null ? "-" : notification.requestMessage(),
                notification.createdAt());
    }

    // slackId가 없으면 멘션 생략
    private String mention(String slackId) {
        return (slackId == null || slackId.isBlank()) ? "" : "<@" + slackId + ">";
    }
}

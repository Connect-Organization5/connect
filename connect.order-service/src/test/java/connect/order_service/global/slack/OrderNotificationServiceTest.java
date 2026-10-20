package connect.order_service.global.slack;

import connect.order_service.domain.client.clientInterface.UserClient;
import connect.order_service.domain.client.dto.response.UserInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderNotificationServiceTest {

    @Mock
    UserClient userClient;
    @Mock
    SlackWebhookSender slackWebhookSender;

    @InjectMocks
    OrderNotificationService orderNotificationService;

    private final UUID ordererId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();

    @Test
    @DisplayName("주문자와 담당자를 멘션해서 Slack 으로 발송한다")
    void notifyOrderCreated_success() {
        when(userClient.getUser(ordererId)).thenReturn(new UserInfo(ordererId, "slack-orderer", "COMPANY", UUID.randomUUID()));
        when(userClient.getUser(managerId)).thenReturn(new UserInfo(managerId, "slack-manager", "COMPANY", UUID.randomUUID()));

        orderNotificationService.notifyOrderCreated(notification());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(slackWebhookSender).send(captor.capture());
        assertThat(captor.getValue()).contains("<@slack-orderer>", "<@slack-manager>", "수량: 3");
    }

    @Test
    @DisplayName("담당자 조회가 실패해도 예외가 밖으로 전파되지 않는다")
    void notifyOrderCreated_userLookupFailed() {
        when(userClient.getUser(ordererId)).thenReturn(new UserInfo(ordererId, null, "COMPANY", UUID.randomUUID()));
        when(userClient.getUser(managerId)).thenThrow(new RuntimeException());

        assertThatCode(() -> orderNotificationService.notifyOrderCreated(notification())).doesNotThrowAnyException();
        verify(slackWebhookSender, never()).send(anyString());
    }

    @Test
    @DisplayName("Slack 전송이 실패해도 예외가 밖으로 전파되지 않는다")
    void notifyOrderCreated_slackFailed() {
        when(userClient.getUser(ordererId)).thenReturn(new UserInfo(ordererId, "slack-orderer", "COMPANY", UUID.randomUUID()));
        when(userClient.getUser(managerId)).thenReturn(new UserInfo(managerId, "slack-manager", "COMPANY", UUID.randomUUID()));
        doThrow(new RuntimeException()).when(slackWebhookSender).send(anyString());

        assertThatCode(() -> orderNotificationService.notifyOrderCreated(notification())).doesNotThrowAnyException();
    }

    private OrderCreatedNotification notification() {
        return new OrderCreatedNotification(UUID.randomUUID(), UUID.randomUUID(), 3, "수령인", null,
                LocalDateTime.now(), ordererId, managerId);
    }
}

package connect.order_service.domain.service;


import connect.order_service.domain.client.clientInterface.CompanyClient;
import connect.order_service.domain.client.clientInterface.DeliveryClient;
import connect.order_service.domain.client.clientInterface.HubClient;
import connect.order_service.domain.client.clientInterface.UserClient;
import connect.order_service.domain.client.dto.response.CompanyInfo;
import connect.order_service.domain.client.dto.response.DeliveryInfo;
import connect.order_service.domain.client.dto.response.UserInfo;
import connect.order_service.domain.dto.CreateOrderRequest;
import connect.order_service.domain.dto.OrderResponse;
import connect.order_service.domain.dto.UpdateOrderRequest;
import connect.order_service.domain.entity.Order;
import connect.order_service.domain.entity.OrderStatus;
import connect.order_service.domain.repository.OrderRepository;
import connect.order_service.global.exception.BaseException;
import connect.order_service.global.exception.ErrorCode;
import connect.order_service.global.slack.SlackWebhookSender;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    UserClient userClient;
    @Mock
    CompanyClient companyClient;
    @Mock
    HubClient hubClient;
    @Mock
    DeliveryClient deliveryClient;
    @Mock
    SlackWebhookSender slackWebhookSender;

    @InjectMocks
    OrderService orderService;

    private final UUID productId = UUID.randomUUID();
    private final UUID requesterCompanyId = UUID.randomUUID();
    private final UUID supplierHubId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID supplierCompanyId = UUID.randomUUID();
    private final UUID deliveryId = UUID.randomUUID();

    @Test
    @DisplayName("소속 타입을 알 수 없으면 주문 불가 AFFILIATION_NOT_FOUND")
    void createOrder_unknownAffiliation() {
        UUID myId = UUID.randomUUID();
        when(userClient.getUser(myId)).thenReturn(new UserInfo(myId, null, "NONE", null));

        assertThatThrownBy(() -> orderService.createOrder(myId, createRequest(3)))
                .hasMessage(ErrorCode.AFFILIATION_NOT_FOUND.getMessage());  // 에러 메시지 확인
        verify(orderRepository, never()).save(any());   // 주문이 저장되지 않음
    }

    @Test
    @DisplayName("재고가 부족하면 주문은 FAILED로 저장 OUT_OF_STOCK")
    void createOrder_outOfStock() {
        UUID myId = UUID.randomUUID();
        givenOrdererAndProduct(myId);
        when(hubClient.decreaseStock(any(), any(), any(), anyInt())).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(myId, createRequest(3)))
                .hasMessage(ErrorCode.OUT_OF_STOCK.getMessage());               // 에러 메시지 확인
        verify(deliveryClient, never()).createDelivery(any());                  // 배송 생성 안 함
        assertThat(lastSavedOrder().getStatus()).isEqualTo(OrderStatus.FAILED); // 주문은 생성되고 상태가 실패
    }

    @Test
    @DisplayName("배송 생성이 실패하면 재고를 복구하고 주문은 FAILED DELIVERY_CREATE_FAILED")
    void createOrder_deliveryCreateFailed() {
        UUID myId = UUID.randomUUID();
        givenOrdererAndProduct(myId);
        when(hubClient.decreaseStock(any(), any(), any(), anyInt())).thenReturn(true);
        when(deliveryClient.createDelivery(any())).thenThrow(new RuntimeException());

        assertThatThrownBy(() -> orderService.createOrder(myId, createRequest(3)))
                .hasMessage(ErrorCode.DELIVERY_CREATE_FAILED.getMessage());         // 오류 메시지 확인
        verify(hubClient).restoreStock(any(), eq(supplierHubId), eq(productId));  // 주문 실패 시 재고 복구 로직 실행
        assertThat(lastSavedOrder().getStatus()).isEqualTo(OrderStatus.FAILED);     // 주문은 생성되고 상태가 실패
    }

    @Test
    @DisplayName("Slack 알림이 실패해도 주문은 생성된다")
    void createOrder_slackFailed() {
        UUID myId = UUID.randomUUID();
        givenOrdererAndProduct(myId);
        when(hubClient.decreaseStock(any(), any(), any(), anyInt())).thenReturn(true);
        when(deliveryClient.createDelivery(any())).thenReturn(deliveryId);
        when(userClient.getUser(managerId)).thenThrow(new RuntimeException());   // 알림 전송 작업 실패

        orderService.createOrder(myId, createRequest(3));                         // 주문 작업은 정상  동작

        Order saved = lastSavedOrder();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.CREATED);   // 주문은 정상 생성
        assertThat(saved.getDeliveryId()).isEqualTo(deliveryId);
    }

    @Test
    @DisplayName("없는 주문을 조회하는 경우 ORDER_NOT_FOUND")
    void searchOrder_notFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.searchOrder(UUID.randomUUID(), "MASTER", orderId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.ORDER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("본인의 주문이 아닌 주문을 조회하는 경우 ORDER_ACCESS_DENIED")
    void searchOrder_notMine() {
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder().build();
        ReflectionTestUtils.setField(order, "createdBy", otherId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.searchOrder(myId, "USER", orderId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.ORDER_ACCESS_DENIED.getMessage());
    }

    @Test
    @DisplayName("본인의 주문이 아니여도 마스터는 조회가 가능")
    void searchOrder_Master() {
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        Order order = Order.builder().quantity(3).receiver("user").build();
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "createdBy", otherId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.searchOrder(myId, "MASTER", orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.quantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("일반 사용자는 본인이 주문한 목록만 조회")
    void searchPageOrders_user() {
        UUID myId = UUID.randomUUID();
        when(orderRepository.findAllByCreatedByAndDeletedAtIsNull(eq(myId), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.searchPageOrders(myId, "USER", 0, 10);

        verify(orderRepository).findAllByCreatedByAndDeletedAtIsNull(eq(myId), any(Pageable.class));    // 유저 목록 조회 실행
        verify(orderRepository, never()).findAllByDeletedAtIsNull(any());   // master 목록 조회 메소드는 실행하지 않음
    }

    @Test
    @DisplayName("master 사용자는 주문한 모든 목록을 조회")
    void searchPageOrders_master() {
        UUID myId = UUID.randomUUID();
        when(orderRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.searchPageOrders(myId, "MASTER", 0, 10);

        verify(orderRepository).findAllByDeletedAtIsNull(any(Pageable.class));     // master 목록 조회 메소드 실행
        verify(orderRepository, never()).findAllByCreatedByAndDeletedAtIsNull(any(), any());    // 유저 목록 조회 메소드는 실행하지 않음
    }

    @Test
    @DisplayName("공급 업체 담당자가 아니면 받은 주문을 조회하는 경우 RECEIVED_ORDER_ACCESS_DENIED")
    void searchReceivedOrder_notManager() {
        UUID myId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID supplierCompanyId = UUID.randomUUID();

        Order order = Order.builder()
                .supplierCompanyId(supplierCompanyId)   // 서비스가 이 값으로 공급 업체를 조회함
                .build();
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        // 공급 업체의 담당자는 managerId (내가 아님)
        CompanyInfo supplier = new CompanyInfo(supplierCompanyId, managerId, UUID.randomUUID(), "회사 이름", "주소");
        when(companyClient.getSupplyCompany(supplierCompanyId)).thenReturn(supplier);

        assertThatThrownBy(() -> orderService.searchReceivedOrder(myId, "USER", orderId))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED.getMessage());
    }

    @Test
    @DisplayName("공급 업체 담당자가 받은 주문을 조회하는 경우")
    void searchReceivedOrder_Manager() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID supplierCompanyId = UUID.randomUUID();

        Order order = Order.builder()
                .supplierCompanyId(supplierCompanyId)   // 서비스가 이 값으로 공급 업체를 조회함
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        // 공급 업체의 담당자는 myId
        CompanyInfo supplier = new CompanyInfo(supplierCompanyId, myId, UUID.randomUUID(), "회사 이름", "주소");
        when(companyClient.getSupplyCompany(supplierCompanyId)).thenReturn(supplier);

        OrderResponse response = orderService.searchReceivedOrder(myId, "USER", orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.supplierCompanyId()).isEqualTo(supplierCompanyId);
    }

    @Test
    @DisplayName("MASTER가 받은 주문을 조회하는 경우는 가능")
    void searchReceivedOrder_Master() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID supplierCompanyId = UUID.randomUUID();

        Order order = Order.builder()
                .supplierCompanyId(supplierCompanyId)
                .build();
        ReflectionTestUtils.setField(order, "id", orderId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.searchReceivedOrder(myId, "MASTER", orderId);

        assertThat(response.id()).isEqualTo(orderId);
        verify(companyClient, never()).getSupplyCompany(any()); // 담당자인지 확인하는 과정을 거치지 않음
    }

    @Test
    @DisplayName("master는 받은 주문 전체 목록을 조회")
    void searchReceivedPageOrders_master() {
        UUID myId = UUID.randomUUID();
        when(orderRepository.findAllByDeletedAtIsNull(any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.searchReceivedPageOrders(myId, "MASTER", 0, 10);

        verify(orderRepository).findAllByDeletedAtIsNull(any(Pageable.class));  // 목록 조회가 정상적으로 실행
        verify(userClient, never()).getUser(any());    // master는 관리자인지 확인하는 과정을 거치지 않음
    }

    @Test
    @DisplayName("담당자는 본인 업체가 공급한 주문 중 실패한 주문을 제외하고 조회")
    void searchReceivedPageOrders_manager() {
        UUID myId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        when(userClient.getUser(myId)).thenReturn(new UserInfo(myId, null, "COMPANY", companyId));
        // company-service: myId가 담당하는 업체는 companyId
        CompanyInfo company = new CompanyInfo(companyId, myId, UUID.randomUUID(), "회사 이름", "주소" );
        when(companyClient.getSupplyCompany(companyId)).thenReturn(company);                                                                                                                                                                when(orderRepository.findAllBySupplierCompanyIdAndStatusNotAndDeletedAtIsNull(
                eq(companyId), eq(OrderStatus.FAILED), any(Pageable.class)))
                .thenReturn(Page.empty());

        orderService.searchReceivedPageOrders(myId, "USER", 0, 10);

        verify(orderRepository).findAllBySupplierCompanyIdAndStatusNotAndDeletedAtIsNull(
                eq(companyId), eq(OrderStatus.FAILED), any(Pageable.class));    // 메소드가 정상적으로 실행
        verify(orderRepository, never()).findAllByDeletedAtIsNull(any());   // master 조회 메소드가 실행되지 않음
    }

    @Test
    @DisplayName("담당자가 다른 사람의 경우 주문 목록을 조회 불가능 RECEIVED_ORDER_ACCESS_DENIED")
    void searchReceivedPageOrders_notManager() {
        UUID myId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(userClient.getUser(myId)).thenReturn(new UserInfo(myId, null, "COMPANY", companyId));   // 소속은 맞음
        when(companyClient.getSupplyCompany(companyId))
                .thenReturn(new CompanyInfo(companyId, UUID.randomUUID(), UUID.randomUUID(), "이름", "주소")); // 담당자는 다른 사람

        assertThatThrownBy(() -> orderService.searchReceivedPageOrders(myId, "USER", 0, 10))
                .isInstanceOf(BaseException.class)
                .hasMessage(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED.getMessage());   // 예외 메시지 확인

        verify(orderRepository, never())
                .findAllBySupplierCompanyIdAndStatusNotAndDeletedAtIsNull(any(), any(), any()); // 유저가 검색하는 메소드가 실행되지 않음
    }

    @Test
    @DisplayName("허브 소속 유저는 받은 주문 목록 조회 불가 RECEIVED_ORDER_ACCESS_DENIED")
    void searchReceivedPageOrders_hubUser() {
        UUID myId = UUID.randomUUID();
        when(userClient.getUser(myId)).thenReturn(new UserInfo(myId, null, "HUB", UUID.randomUUID()));

        assertThatThrownBy(() -> orderService.searchReceivedPageOrders(myId, "USER", 0, 10))
                .hasMessage(ErrorCode.RECEIVED_ORDER_ACCESS_DENIED.getMessage());
        verify(companyClient, never()).getSupplyCompany(any());   // 업체 조회까지 가지 않음
    }

    @Test
    @DisplayName("주문 수정 불가능한 사람의 수정 요청 ORDER_MODIFY_DENIED")
    void updateOrder_denied() {
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, otherId);    // 주문자 - 다른 사람

        assertThatThrownBy(() -> orderService.updateOrder(myId, "USER", orderId, updateRequest(5)))
                .hasMessage(ErrorCode.ORDER_MODIFY_DENIED.getMessage());    // 오류 메시지 확인
        assertThat(order.getQuantity()).isEqualTo(3);   // 수량이 변하지 않음
    }

    @Test
    @DisplayName("실패한 주문은 수정 불가 ORDER_NOT_MODIFIABLE")
    void updateOrder_notModifiable() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        order.fail();                                                  // 주문 상태 - FAILED

        assertThatThrownBy(() -> orderService.updateOrder(myId, "USER", orderId, updateRequest(5)))
                .hasMessage(ErrorCode.ORDER_NOT_MODIFIABLE.getMessage());
        verify(deliveryClient, never()).getDelivery(any(), any(), any());            // 배송 확인 메소드가 실행되지 않음
    }

    @Test
    @DisplayName("배송이 시작된 주문은 수정 불가 ORDER_DELIVERY_STARTED")
    void updateOrder_deliveryStarted() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_MOVING"));

        assertThatThrownBy(() -> orderService.updateOrder(myId, "USER", orderId, updateRequest(5)))
                .hasMessage(ErrorCode.ORDER_DELIVERY_STARTED.getMessage());     // 오류 메시지 확인
        verify(hubClient, never()).adjustStock(any(), any(), any(), anyInt());  // 재고 차감 메소드가 실행되지 않음
        assertThat(order.getQuantity()).isEqualTo(3);   // 수량이 변하지 않음
    }

    @Test
    @DisplayName("배송 정보가 없으면 수정 불가 DELIVERY_NOT_FOUND")
    void updateOrder_deliveryNotFound() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenThrow(FeignException.NotFound.class);  // 배송 조회 404

        assertThatThrownBy(() -> orderService.updateOrder(myId, "USER", orderId, updateRequest(5)))
                .hasMessage(ErrorCode.DELIVERY_NOT_FOUND.getMessage());         // 오류 메시지 확인
        verify(hubClient, never()).adjustStock(any(), any(), any(), anyInt());  // 재고 조정 메소드가 실행되지 않음
        assertThat(order.getQuantity()).isEqualTo(3);   // 수량이 변하지 않음
    }

    @Test
    @DisplayName("배송 조회에 실패하면 취소 불가 DELIVERY_FETCH_FAILED")
    void deleteOrder_deliveryFetchFailed() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenThrow(new RuntimeException());  // 배송 서비스 오류

        assertThatThrownBy(() -> orderService.deleteOrder(myId, "USER", orderId))
                .hasMessage(ErrorCode.DELIVERY_FETCH_FAILED.getMessage());   // 오류 메시지 확인
        verify(deliveryClient, never()).cancelDelivery(any());               // 배송 취소 메소드가 실행되지 않음
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);        // 주문 상태 유지
    }

    @Test
    @DisplayName("늘린 수량만큼 재고가 없으면 수정 불가 OUT_OF_STOCK_FOR_UPDATE")
    void updateOrder_outOfStock() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_WAITING"));
        when(companyClient.getSupplyCompany(supplierCompanyId))
                .thenReturn(new CompanyInfo(supplierCompanyId, UUID.randomUUID(), UUID.randomUUID(), "회사 이름", "주소"));
        when(hubClient.adjustStock(any(), any(), any(), eq(2))).thenReturn(false);   // 재고 차감 실패

        assertThatThrownBy(() -> orderService.updateOrder(myId, "USER", orderId, updateRequest(5)))
                .hasMessage(ErrorCode.OUT_OF_STOCK_FOR_UPDATE.getMessage());    // 오류 메시지 확인
        verify(deliveryClient, never()).changeReceiver(any(), any(), any(), any());  // 배송 수정 메소드 실행하지 않음
        assertThat(order.getQuantity()).isEqualTo(3);   // 수량이 변하지 않음
    }

    @Test
    @DisplayName("배송 수정이 실패하면 재고를 되돌리고 주문은 유지 DELIVERY_UPDATE_FAILED")
    void updateOrder_deliveryUpdateFailed() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_WAITING"));
        when(companyClient.getSupplyCompany(supplierCompanyId))
                .thenReturn(new CompanyInfo(supplierCompanyId, UUID.randomUUID(), UUID.randomUUID(), "회사 이름", "주소"));
        when(hubClient.adjustStock(any(), any(), any(), eq(2))).thenReturn(true);    // 수량 차감 성공
        doThrow(new RuntimeException()).when(deliveryClient).changeReceiver(any(), any(), any(), any());  // 배송 수정에 오류

        assertThatThrownBy(() -> orderService.updateOrder(myId, "USER", orderId, updateRequest(5)))
                .hasMessage(ErrorCode.DELIVERY_UPDATE_FAILED.getMessage());
        verify(hubClient).adjustStock(any(), any(), any(), eq(-2));   // 재고 롤백 진행
        assertThat(order.getQuantity()).isEqualTo(3);   // 주문 수량이 변경되지 않음
        verify(orderRepository, never()).save(any());   // 수정 주문 저장이 이루어지지 않음
    }

    @Test
    @DisplayName("주문 취소 성공 흐름")
    void deleteOrder_owner() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_WAITING"));
        when(companyClient.getSupplyCompany(supplierCompanyId))
                .thenReturn(new CompanyInfo(supplierCompanyId, UUID.randomUUID(), UUID.randomUUID(), "회사 이름", "주소"));

        orderService.deleteOrder(myId, "USER", orderId);

        verify(deliveryClient).cancelDelivery(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("주문 취소 불가능한 사람의 취소 요청 ORDER_DELETE_DENIED")
    void deleteOrder_denied() {
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        givenCreatedOrder(orderId, otherId);    // 주문자 - 다른 사람
        when(companyClient.getSupplyCompany(supplierCompanyId))
                .thenReturn(new CompanyInfo(supplierCompanyId, otherId, UUID.randomUUID(), "회사 이름", "주소"));  // 담당자 - 다른 사람

        assertThatThrownBy(() -> orderService.deleteOrder(myId, "USER", orderId))
                .hasMessage(ErrorCode.ORDER_DELETE_DENIED.getMessage());    // 예외 메시지 확인
        verify(deliveryClient, never()).cancelDelivery(any());      // 배송 취소 메소드가 실행 되지 않음
    }

    @Test
    @DisplayName("배송이 시작된 주문은 취소 불가 ORDER_DELIVERY_STARTED")
    void deleteOrder_deliveryStarted() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_MOVING"));

        assertThatThrownBy(() -> orderService.deleteOrder(myId, "USER", orderId))
                .hasMessage(ErrorCode.ORDER_DELIVERY_STARTED.getMessage());  // 오류 메시지 확인
        verify(deliveryClient, never()).cancelDelivery(any());               // 배송 취소 메소드가 실행되지 않음
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);        // 주문 상태 유지
    }

    @Test
    @DisplayName("배송 취소가 실패하면 주문은 유지 DELIVERY_DELETE_FAILED")
    void deleteOrder_cancelDeliveryFailed() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_WAITING"));
        doThrow(new RuntimeException()).when(deliveryClient).cancelDelivery(orderId);

        assertThatThrownBy(() -> orderService.deleteOrder(myId, "USER", orderId))
                .hasMessage(ErrorCode.DELIVERY_DELETE_FAILED.getMessage());  // 오류 메시지 확인
        verify(hubClient, never()).restoreStock(any(), any(), any()); // 재고 복구 안 함
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);        // 주문 상태 유지
        verify(orderRepository, never()).save(any());                        // 주문 취소 상태가 저장 안 됨
    }

    @Test
    @DisplayName("배송 취소 후 재고 복구가 실패해도 주문은 취소된다")
    void deleteOrder_stockRestoreFailed() {
        UUID myId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = givenCreatedOrder(orderId, myId);
        when(deliveryClient.getDelivery(eq(deliveryId), any(), any())).thenReturn(new DeliveryInfo(deliveryId, "HUB_WAITING"));
        when(companyClient.getSupplyCompany(supplierCompanyId))
                .thenReturn(new CompanyInfo(supplierCompanyId, UUID.randomUUID(), UUID.randomUUID(), "회사 이름", "주소"));
        doThrow(new RuntimeException()).when(hubClient).restoreStock(any(), any(), any());

        orderService.deleteOrder(myId, "USER", orderId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);  // 주문 상태가 취소 상태인지
        verify(orderRepository).save(order);   // 주문 취소가 저장되었는지
    }

//    ------------------------------ Helper Method -----------------------------------

    private void givenOrdererAndProduct(UUID myId) {
        when(userClient.getUser(myId))
                .thenReturn(new UserInfo(myId, null, "COMPANY", requesterCompanyId));
        when(companyClient.getSupplyCompany(requesterCompanyId))
                .thenReturn(new CompanyInfo(requesterCompanyId, UUID.randomUUID(), UUID.randomUUID(), "회사 이름", "요청 업체 주소"));
        when(companyClient.getSupplyCompany(supplierCompanyId))
                .thenReturn(new CompanyInfo(supplierCompanyId, managerId, supplierHubId, "회사 이름", "공급 업체 주소"));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Order lastSavedOrder() {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private CreateOrderRequest createRequest(int quantity) {
        CreateOrderRequest request = new CreateOrderRequest();
        ReflectionTestUtils.setField(request, "supplierCompanyId", supplierCompanyId);
        ReflectionTestUtils.setField(request, "productId", productId);
        ReflectionTestUtils.setField(request, "quantity", quantity);
        ReflectionTestUtils.setField(request, "receiver", "수령인");
        return request;
    }

    private Order givenCreatedOrder(UUID orderId, UUID ownerId) {
        Order order = Order.builder()
                .supplierCompanyId(supplierCompanyId)
                .productId(UUID.randomUUID())
                .quantity(3)
                .build();
        order.complete(deliveryId);
        ReflectionTestUtils.setField(order, "id", orderId);
        ReflectionTestUtils.setField(order, "createdBy", ownerId);
        when(orderRepository.findByIdAndDeletedAtIsNull(orderId)).thenReturn(Optional.of(order));
        return order;
    }

    private UpdateOrderRequest updateRequest(int quantity) {
        UpdateOrderRequest request = new UpdateOrderRequest();
        ReflectionTestUtils.setField(request, "quantity", quantity);
        ReflectionTestUtils.setField(request, "receiver", "수령인");
        return request;
    }
}
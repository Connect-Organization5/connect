package connect.order_service.domain.client;

import connect.order_service.domain.client.clientInterface.HubClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

//@FeignClient(name = "hub-service")
@Component
public class HubFeignClient implements HubClient {
    private static final int DEFAULT_STOCK = 100;
    private final Map<UUID, Integer> stock = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean decreaseStock(UUID hubId, UUID productId, int quantity, UUID orderId) {
        int current = stock.getOrDefault(productId, DEFAULT_STOCK);
        if (current < quantity) {
            return false;
        }
        stock.put(productId, current - quantity);
        return true;
    }

    @Override
    public synchronized void increaseStock(UUID hubId, UUID productId, int quantity, UUID orderId) {
        stock.merge(productId, quantity, Integer::sum);
    }
}

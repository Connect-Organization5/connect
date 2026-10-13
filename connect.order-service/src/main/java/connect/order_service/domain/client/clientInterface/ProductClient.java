package connect.order_service.domain.client.clientInterface;

import connect.order_service.domain.client.dto.ProductInfo;

import java.util.UUID;

public interface ProductClient {
    ProductInfo getProduct(UUID productId);
}

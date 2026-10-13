package connect.order_service.domain.client;

import connect.order_service.domain.client.dto.ProductInfo;
import connect.order_service.domain.client.clientInterface.ProductClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

//@FeignClient(name = "product-service")
@Component
public class ProductFeignClient implements ProductClient {
    @Override
    public ProductInfo getProduct(UUID productId) {
        return new ProductInfo(
                productId,
                UUID.fromString("aaaaaaaa-0000-0000-0000-000000000003"),   // 공급 업체
                UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));  // 출발 허브
    }
}

package connect.hub_service.client.company.dto;

import java.util.UUID;

// Company GET /companies/{companyId}/products/{productId} 응답의 data 중 상품 ID만 받는다.
public record ProductInfo(UUID id) {
}

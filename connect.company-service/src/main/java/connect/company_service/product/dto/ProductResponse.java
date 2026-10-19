package connect.company_service.product.dto;

import connect.company_service.product.entity.Product;

import java.math.BigDecimal;
import java.util.UUID;

// companyId는 URL(/companies/{companyId})에 이미 있으므로 응답에서 제외
public record ProductResponse(
        UUID id,
        String name,
        String description,
        BigDecimal price
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
        );
    }
}

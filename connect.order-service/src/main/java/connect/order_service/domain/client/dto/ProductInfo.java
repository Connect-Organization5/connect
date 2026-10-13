package connect.order_service.domain.client.dto;

import java.util.UUID;

public record ProductInfo (
        UUID productId,
        UUID supplierCompanyId,
        UUID hubId
){

}

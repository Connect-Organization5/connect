package connect.hub_service.client.company.dto;

import java.util.UUID;

// Company GET /companies/{companyId} 응답의 data 중 재고 등록 검증에 필요한 필드만 받는다.
public record CompanyInfo(UUID id, UUID hubId) {
}

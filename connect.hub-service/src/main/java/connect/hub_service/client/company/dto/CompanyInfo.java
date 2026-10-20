package connect.hub_service.client.company.dto;

import java.util.UUID;

// Company GET /companies/{companyId} 응답의 data 중 재고 등록·입고 검증에 필요한 필드만 받는다.
// userId는 업체 관리자이며, 관리자가 지정되지 않은 업체면 null이다.
public record CompanyInfo(UUID id, UUID userId, UUID hubId) {
}

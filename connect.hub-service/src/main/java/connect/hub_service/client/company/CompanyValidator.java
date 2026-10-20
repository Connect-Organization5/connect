package connect.hub_service.client.company;

import connect.hub_service.client.company.dto.CompanyInfo;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 재고 등록·입고 시 업체·상품이 실제로 있고, 요청자가 다룰 수 있으며, 허브가 업체의 소속 허브인지 Company API로 확인한다.
@Component
@RequiredArgsConstructor
public class CompanyValidator {

    // Gateway가 JWT 검증 후 전달한 X-User-Role 값
    private static final String MASTER = "MASTER";

    private final CompanyClient companyClient;

    // 확인 순서: 업체 존재(404) → 권한(403) → 소속 허브(409) → 상품(404).
    // 권한을 먼저 확인해 관리자가 아닌 요청자에게 다른 업체의 소속 허브 정보가 드러나지 않게 한다.
    public void validateStockTarget(UUID companyId, UUID hubId, UUID productId, UUID userId, String role) {
        CompanyInfo company = getCompany(companyId);
        // MASTER는 모든 업체, 그 외에는 업체 관리자(Company.userId)만 허용한다. 상품 생성 권한과 같은 기준이다.
        if (!MASTER.equals(role) && !userId.equals(company.userId())) {
            throw new BaseException(ErrorCode.FORBIDDEN);
        }
        if (!hubId.equals(company.hubId())) {
            throw new BaseException(ErrorCode.STOCK_HUB_MISMATCH);
        }
        validateProduct(companyId, productId);
    }

    private CompanyInfo getCompany(UUID companyId) {
        try {
            CompanyInfo company = companyClient.getCompany(companyId).getData();
            if (company == null) {
                throw new BaseException(ErrorCode.COMPANY_SERVICE_UNAVAILABLE);
            }
            return company;
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.COMPANY_NOT_FOUND);
        } catch (FeignException e) {
            // 연결 실패·타임아웃(RetryableException 포함), 5xx 등은 결과를 확인할 수 없으므로 503
            throw new BaseException(ErrorCode.COMPANY_SERVICE_UNAVAILABLE);
        }
    }

    private void validateProduct(UUID companyId, UUID productId) {
        try {
            companyClient.getProduct(companyId, productId);
        } catch (FeignException.NotFound e) {
            throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND);
        } catch (FeignException e) {
            throw new BaseException(ErrorCode.COMPANY_SERVICE_UNAVAILABLE);
        }
    }
}

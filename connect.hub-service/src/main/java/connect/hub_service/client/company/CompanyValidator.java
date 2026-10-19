package connect.hub_service.client.company;

import connect.hub_service.client.company.dto.CompanyInfo;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 재고 등록 시 업체·상품이 실제로 있고, 등록할 허브가 업체의 소속 허브인지 Company API로 확인한다.
@Component
@RequiredArgsConstructor
public class CompanyValidator {

    private final CompanyClient companyClient;

    public void validateStockTarget(UUID companyId, UUID hubId, UUID productId) {
        CompanyInfo company = getCompany(companyId);
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

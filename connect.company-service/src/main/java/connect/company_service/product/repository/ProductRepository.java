package connect.company_service.product.repository;

import connect.company_service.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // 삭제되지 않았고, URL의 companyId에 속한 상품만 단건 조회
    Optional<Product> findByIdAndCompany_IdAndDeletedAtIsNull(UUID id, UUID companyId);

    // 특정 Company의 삭제되지 않은 상품 목록
    Page<Product> findByCompany_IdAndDeletedAtIsNull(UUID companyId, Pageable pageable);

    // 특정 Company 내부에서 상품명 부분 일치 검색
    Page<Product> findByCompany_IdAndNameContainingAndDeletedAtIsNull(UUID companyId, String name, Pageable pageable);

    // 업체 삭제 시 일괄 soft delete 대상: 특정 Company의 삭제되지 않은 상품 전체
    List<Product> findAllByCompany_IdAndDeletedAtIsNull(UUID companyId);
}

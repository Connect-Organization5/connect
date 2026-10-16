package connect.company_service.company.repository;

import connect.company_service.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    // 삭제되지 않은 업체 단건 조회
    Optional<Company> findByIdAndDeletedAtIsNull(UUID id);
}

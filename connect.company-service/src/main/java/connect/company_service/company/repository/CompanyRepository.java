package connect.company_service.company.repository;

import connect.company_service.company.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByNameAndDeletedAtIsNull(String name);

    Optional<Company> findByIdAndDeletedAtIsNull(UUID id);

    // 관리자 지정 시 동시 요청이 둘 다 지정되지 않도록 행 잠금(SELECT ... FOR UPDATE)으로 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Company> findWithLockByIdAndDeletedAtIsNull(UUID id);

    // 삭제되지 않은 업체 목록
    Page<Company> findByDeletedAtIsNull(Pageable pageable);

    // 삭제되지 않은 업체 중 업체명 또는 주소에 keyword가 포함된 업체 (AND가 OR보다 먼저 묶임)
    Page<Company> findByNameContainingAndDeletedAtIsNullOrAddressContainingAndDeletedAtIsNull(
            String name, String address, Pageable pageable);
}

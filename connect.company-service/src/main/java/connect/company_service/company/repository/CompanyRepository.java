package connect.company_service.company.repository;

import connect.company_service.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsByNameAndDeletedAtIsNull(String name);
}

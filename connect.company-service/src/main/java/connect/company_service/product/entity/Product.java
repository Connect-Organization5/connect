package connect.company_service.product.entity;

import connect.company_service.company.entity.Company;
import connect.company_service.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Entity
@Table(name = "p_company_products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // 같은 Company Service 내부 Entity이므로 실제 FK(company_id)로 연결 (Product → Company 단방향)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    private Product(Company company, String name, String description, BigDecimal price) {
        this.company = company;
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public static Product create(Company company, String name, String description, BigDecimal price) {
        return new Product(company, name, description, price);
    }

    // 소속 Company는 변경하지 않는다
    public void update(String name, String description, BigDecimal price) {
        this.name = name;
        this.description = description;
        this.price = price;
    }

    public void delete(UUID deletedBy) {
        softDelete(deletedBy);
    }
}

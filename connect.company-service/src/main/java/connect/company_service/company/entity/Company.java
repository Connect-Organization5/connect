package connect.company_service.company.entity;

import connect.company_service.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "p_companies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    @Column(nullable = false)
    private UUID hubId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    private Company(UUID userId, UUID hubId, String name, String address) {
        this.userId = userId;
        this.hubId = hubId;
        this.name = name;
        this.address = address;
    }

    public static Company create(UUID userId, UUID hubId, String name, String address) {
        return new Company(userId, hubId, name, address);
    }

    public void update(UUID hubId, String name, String address) {
        this.hubId = hubId;
        this.name = name;
        this.address = address;
    }

    // MASTER가 지정한 사용자를 관리자로 지정 (기존 관리자가 있으면 변경)
    public void assignManager(UUID userId) {
        this.userId = userId;
    }

    public void delete(UUID deletedBy) {
        softDelete(deletedBy);
    }
}

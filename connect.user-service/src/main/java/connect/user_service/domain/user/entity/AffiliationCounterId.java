package connect.user_service.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

// 소속별 가입자 카운터의 기본 키 (affiliationType + affiliationId)
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AffiliationCounterId implements Serializable {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AffiliationType affiliationType;

    @Column(nullable = false)
    private Long affiliationId;
}

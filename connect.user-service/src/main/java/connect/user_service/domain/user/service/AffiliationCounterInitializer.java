package connect.user_service.domain.user.service;

import connect.user_service.domain.user.entity.AffiliationCounter;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.repository.AffiliationCounterRepository;
import connect.user_service.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 소속의 첫 가입 때 카운터 행을 만든다
// 자체 트랜잭션으로 바로 커밋하고, 동시에 만들려는 다른 요청은 기본 키 충돌(DataIntegrityViolationException)로 실패
// → 호출하는 AffiliationCounterService.ensureCounter 가 이 예외를 무시 (트랜잭션 경계 밖에서 잡아야 해서 별도 빈으로 분리)
@Component
@RequiredArgsConstructor
public class AffiliationCounterInitializer {

    private final AffiliationCounterRepository affiliationCounterRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(AffiliationType affiliationType, Long affiliationId) {
        // 카운터 도입 전에 가입한 기존 회원(삭제된 회원 포함) 수부터 시작
        long existingMembers = userRepository.countAllIncludingDeleted(affiliationType.name(), affiliationId);
        affiliationCounterRepository.saveAndFlush(new AffiliationCounter(affiliationType, affiliationId, existingMembers));
    }
}

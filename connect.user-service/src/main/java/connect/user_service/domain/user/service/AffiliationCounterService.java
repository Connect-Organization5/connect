package connect.user_service.domain.user.service;

import connect.user_service.domain.user.entity.AffiliationCounter;
import connect.user_service.domain.user.entity.AffiliationCounterId;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.repository.AffiliationCounterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AffiliationCounterService {

    private final AffiliationCounterRepository affiliationCounterRepository;
    private final AffiliationCounterInitializer affiliationCounterInitializer;

    // 소속 카운터 행이 없으면 생성 (자체 트랜잭션으로 바로 커밋)
    // 회원가입 트랜잭션을 시작하기 전에 호출해서, 한 요청이 커넥션을 동시에 2개 잡지 않게 한다
    public void ensureCounter(AffiliationType affiliationType, Long affiliationId) {
        if (affiliationCounterRepository.existsById(new AffiliationCounterId(affiliationType, affiliationId))) {
            return;
        }
        try {
            affiliationCounterInitializer.create(affiliationType, affiliationId);
        } catch (DataIntegrityViolationException e) {
            // 동시에 들어온 다른 요청이 먼저 만든 경우: 이미 있으므로 그대로 진행
        }
    }

    // 소속의 가입자 수를 1 늘리고 이번 가입자의 순서(1부터)를 반환
    // 회원가입 트랜잭션 안에서만 호출: 행 잠금이 회원 저장까지 유지되고, 회원 저장이 실패하면 증가도 함께 롤백됨
    @Transactional(propagation = Propagation.MANDATORY)
    public long increaseAndGet(AffiliationType affiliationType, Long affiliationId) {
        // 행 잠금 후 증가 (같은 소속의 다른 가입 요청은 이 트랜잭션이 끝날 때까지 대기)
        AffiliationCounter counter = affiliationCounterRepository
                .findByIdForUpdate(new AffiliationCounterId(affiliationType, affiliationId))
                .orElseThrow(() -> new IllegalStateException(
                        "소속 카운터가 없습니다. ensureCounter 를 먼저 호출해야 합니다: " + affiliationType + " " + affiliationId));
        return counter.increase();
    }
}

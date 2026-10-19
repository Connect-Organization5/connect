package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponse;
import connect.user_service.domain.user.entity.AffiliationType;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.dto.PageResponse;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final UserRole ROLE_MASTER = UserRole.MASTER;
    // 소속별로 처음 가입한 이 인원까지 MASTER 권한 부여
    private static final int MASTER_LIMIT = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AffiliationCounterService affiliationCounterService;
    private final TransactionTemplate transactionTemplate;

    // 메서드 전체를 하나의 트랜잭션으로 묶지 않음:
    // 소속 카운터 생성(자체 트랜잭션)이 회원가입 트랜잭션보다 먼저 끝나야 한 요청이 커넥션을 동시에 2개 잡지 않음
    public void signup(SignUpRequestDto requestDto) {
        String username = requestDto.getUsername();
        String email = requestDto.getEmail();

        // 회원 중복 확인
        if (userRepository.existsByUsername(username)) {
            throw new BaseException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // email 중복확인
        if (userRepository.existsByEmail(email)) {
            throw new BaseException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // slackId 중복확인
        if (userRepository.existsBySlackId(requestDto.getSlackId())) {
            throw new BaseException(ErrorCode.SLACK_ID_ALREADY_EXISTS);
        }

        // 중복 확인을 통과한 경우에만 비밀번호 암호화
        // (소속 카운터 잠금을 짧게 유지하려고 잠금 전에 암호화)
        String password = passwordEncoder.encode(requestDto.getPassword());

        AffiliationType affiliationType = requestDto.getAffiliationType();
        Long affiliationId = requestDto.getAffiliationId();

        // 소속의 첫 가입이면 소속 카운터 생성
        affiliationCounterService.ensureCounter(affiliationType, affiliationId);

        // 권한 부여와 사용자 등록은 한 트랜잭션 (등록이 실패하면 가입 순서 증가도 롤백)
        transactionTemplate.executeWithoutResult(status -> {
            // 유저 권한 부여: 같은 소속(affiliationType + affiliationId)에서 처음 가입한 5명은 MASTER, 이후는 USER
            UserRole role = determineRole(affiliationType, affiliationId);

            // 사용자 등록
            User user = new User(username, password, email, role, affiliationType, affiliationId, requestDto.getSlackId());
            userRepository.save(user);
        });
    }

    // 소속별 가입 순서로 권한 결정 (삭제된 회원도 순서에 포함, 동시 가입은 소속 카운터 행 잠금으로 한 명씩 처리)
    private UserRole determineRole(AffiliationType affiliationType, Long affiliationId) {
        long signupOrder = affiliationCounterService.increaseAndGet(affiliationType, affiliationId);
        return signupOrder <= MASTER_LIMIT ? UserRole.MASTER : UserRole.USER;
    }

    // 아이디가 없거나 비밀번호가 틀려도 같은 에러를 반환해서 아이디 존재 여부를 노출하지 않음
    public User login(LoginRequestDto request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BaseException(ErrorCode.INVALID_CREDENTIALS));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }

        return user;
    }

    // 회원 단건 조회 (USER 는 본인만, MASTER 는 같은 소속 회원만)
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId, User requester) {
        // 1. USER 가 다른 회원을 조회하면 거부 (대상 회원 조회 전에 거부해서 회원 존재 여부를 노출하지 않음)
        if (requester.getRole() != ROLE_MASTER && !Objects.equals(requester.getId(), userId)) {
            throw new BaseException(ErrorCode.USER_ACCESS_DENIED);
        }

        // 2. 대상 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 3. MASTER 는 같은 소속 회원만 조회 가능 (본인은 항상 같은 소속)
        validateSameAffiliation(requester, user);

        return UserResponse.from(user);
    }

    // 회원 목록 조회 (MASTER 만 가능, 요청자와 같은 소속의 모든 회원)
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAllUsers(User requester, int page, int size) {
        // 1. 권한 확인
        validateMaster(requester);

        // 2. 요청자의 소속으로 조회 (정렬은 Repository 쿼리에 고정: MASTER 먼저 → createdAt 내림차순)
        Page<User> users = userRepository.findAllByAffiliation(
                requester.getAffiliationType(),
                requester.getAffiliationId(),
                ROLE_MASTER,
                PageRequest.of(page, size)
        );

        return PageResponse.from(users.map(UserResponse::from));
    }

    // 회원 정보 수정 (MASTER 이고 대상 회원과 소속이 같을 때만 가능, MASTER 는 본인 수정 가능)
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequestDto requestDto, User requester) {
        // 1. 권한 확인 (대상 회원 조회 전에 거부해서 회원 존재 여부를 노출하지 않음)
        validateMaster(requester);

        // 2. 대상 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 3. 소속 확인
        validateSameAffiliation(requester, user);

        // 4. 자기 자신을 제외한 email, slackId 중복 확인
        if (userRepository.existsByEmailAndIdNot(requestDto.getEmail(), userId)) {
            throw new BaseException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsBySlackIdAndIdNot(requestDto.getSlackId(), userId)) {
            throw new BaseException(ErrorCode.SLACK_ID_ALREADY_EXISTS);
        }

        // 5. 수정 (updatedAt, updatedBy 는 JPA Auditing 이 기록)
        user.update(
                requestDto.getEmail(),
                passwordEncoder.encode(requestDto.getPassword()),
                requestDto.getSlackId(),
                requestDto.getRole(),
                requestDto.getAffiliationType(),
                requestDto.getAffiliationId()
        );

        return UserResponse.from(user);
    }

    // 회원 삭제 (논리적 삭제, MASTER 이고 대상 회원과 소속이 같을 때만 가능, MASTER 는 본인 삭제 가능)
    @Transactional
    public void deleteUser(Long userId, User requester) {
        // 1. 권한 확인 (대상 회원 조회 전에 거부해서 회원 존재 여부를 노출하지 않음)
        validateMaster(requester);

        // 2. 대상 회원 조회 (이미 삭제된 회원은 @SQLRestriction 으로 조회되지 않음)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 3. 소속 확인
        validateSameAffiliation(requester, user);

        // 4. 논리적 삭제 (updatedAt, updatedBy 는 변경하지 않음)
        int deleted = userRepository.softDeleteById(userId, requester.getId(), LocalDateTime.now());
        if (deleted == 0) {
            // 조회 이후 다른 요청이 먼저 삭제한 경우
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void validateMaster(User requester) {
        if (requester.getRole() != ROLE_MASTER) {
            throw new BaseException(ErrorCode.USER_ACCESS_DENIED);
        }
    }

    // affiliationId 는 Long 이므로 == 가 아닌 equals 로 비교
    private void validateSameAffiliation(User requester, User target) {
        if (!Objects.equals(requester.getAffiliationType(), target.getAffiliationType())
                || !Objects.equals(requester.getAffiliationId(), target.getAffiliationId())) {
            throw new BaseException(ErrorCode.USER_ACCESS_DENIED);
        }
    }


}




package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UpdateUserResponse;
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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final UserRole ROLE_MASTER = UserRole.MASTER;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // 회원가입 (로그인 없이 누구나 가입 가능, 권한은 항상 USER)
    @Transactional
    public UpdateUserResponse signup(SignUpRequestDto requestDto) {
        String username = requestDto.getUsername();
        String email = requestDto.getEmail();

        // 1. 회원 중복 확인
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
        String password = passwordEncoder.encode(requestDto.getPassword());

        // 2. 사용자 등록 (로그인 전 요청이라 createdBy 는 null)
        User user = new User(username, password, email, UserRole.USER, requestDto.getAffiliationType(), requestDto.getAffiliationId(), requestDto.getSlackId());
        userRepository.save(user);

        return UpdateUserResponse.from(user);
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
    public UpdateUserResponse getUser(UUID userId, User requester) {
        // 1. USER 가 다른 회원을 조회하면 거부 (대상 회원 조회 전에 거부해서 회원 존재 여부를 노출하지 않음)
        if (requester.getRole() != ROLE_MASTER && !Objects.equals(requester.getId(), userId)) {
            throw new BaseException(ErrorCode.USER_ACCESS_DENIED);
        }

        // 2. 대상 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 3. MASTER 는 같은 소속 회원만 조회 가능 (본인은 항상 같은 소속)
        validateSameAffiliation(requester, user);

        return UpdateUserResponse.from(user);
    }

    // 회원 목록 조회 (MASTER 만 가능, 요청자와 같은 소속의 모든 회원)
    @Transactional(readOnly = true)
    public PageResponse<UpdateUserResponse> getAllUsers(User requester, int page, int size) {
        // 1. 권한 확인
        validateMaster(requester);

        // 2. 요청자의 소속으로 조회 (정렬은 Repository 쿼리에 고정: MASTER 먼저 → createdAt 내림차순)
        Page<User> users = userRepository.findAllByAffiliation(
                requester.getAffiliationType(),
                requester.getAffiliationId(),
                ROLE_MASTER,
                PageRequest.of(page, size)
        );

        return PageResponse.from(users.map(UpdateUserResponse::from));
    }

    // 회원 정보 수정 (MASTER 이고 대상 회원과 소속이 같을 때만 가능, MASTER 는 본인 수정 가능)
    @Transactional
    public UpdateUserResponse updateUser(UUID userId, UpdateUserRequestDto requestDto, User requester) {
        // 1. 권한 확인 (대상 회원 조회 전에 거부해서 회원 존재 여부를 노출하지 않음)
        validateMaster(requester);

        // 2. 대상 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 3. 소속 확인
        validateSameAffiliation(requester, user);

        // 4. 소속의 마지막 MASTER 는 USER 로 강등 불가 (강등하면 그 소속에서 회원을 관리할 수 없음)
        if (requestDto.getRole() != ROLE_MASTER && isLastMaster(user)) {
            throw new BaseException(ErrorCode.LAST_MASTER_CANNOT_BE_DEMOTED);
        }

        // 5. 자기 자신을 제외한 email, slackId 중복 확인
        if (userRepository.existsByEmailAndIdNot(requestDto.getEmail(), userId)) {
            throw new BaseException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsBySlackIdAndIdNot(requestDto.getSlackId(), userId)) {
            throw new BaseException(ErrorCode.SLACK_ID_ALREADY_EXISTS);
        }

        // 6. 수정 (updatedAt, updatedBy 는 JPA Auditing 이 기록)
        user.update(
                requestDto.getEmail(),
                passwordEncoder.encode(requestDto.getPassword()),
                requestDto.getSlackId(),
                requestDto.getRole()
        );

        return UpdateUserResponse.from(user);
    }

    // 회원 삭제 (논리적 삭제, MASTER 이고 대상 회원과 소속이 같을 때만 가능, MASTER 는 본인 삭제 가능)
    @Transactional
    public void deleteUser(UUID userId, User requester) {
        // 1. 권한 확인 (대상 회원 조회 전에 거부해서 회원 존재 여부를 노출하지 않음)
        validateMaster(requester);

        // 2. 대상 회원 조회 (이미 삭제된 회원은 @SQLRestriction 으로 조회되지 않음)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        // 3. 소속 확인
        validateSameAffiliation(requester, user);

        // 4. 소속의 마지막 MASTER 는 삭제 불가 (삭제하면 그 소속에서 회원을 관리할 수 없음)
        if (isLastMaster(user)) {
            throw new BaseException(ErrorCode.LAST_MASTER_CANNOT_BE_DELETED);
        }

        // 5. 논리적 삭제 (updatedAt, updatedBy 는 변경하지 않음)
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

    // 대상이 MASTER 이고 같은 소속에 다른 MASTER 가 없으면 true (대상이 USER 면 MASTER 를 조회하지 않음)
    // 소속의 MASTER 행을 잠그고 세므로, 같은 소속 MASTER 를 동시에 삭제/강등해도 둘 다 통과하지 않음
    // (잠금은 호출한 @Transactional 메서드가 끝날 때 풀림)
    private boolean isLastMaster(User target) {
        return target.getRole() == ROLE_MASTER
                && userRepository.findAllByAffiliationAndRoleForUpdate(
                        target.getAffiliationType(), target.getAffiliationId(), ROLE_MASTER).size() <= 1;
    }

    // affiliationId 는 UUID(객체) 이므로 == 가 아닌 equals 로 비교
    private void validateSameAffiliation(User requester, User target) {
        validateSameAffiliation(requester, target.getAffiliationType(), target.getAffiliationId());
    }

    // 회원 생성처럼 대상 회원이 아직 없을 때는 요청 값의 소속으로 비교
    private void validateSameAffiliation(User requester, AffiliationType affiliationType, UUID affiliationId) {
        if (!Objects.equals(requester.getAffiliationType(), affiliationType)
                || !Objects.equals(requester.getAffiliationId(), affiliationId)) {
            throw new BaseException(ErrorCode.USER_ACCESS_DENIED);
        }
    }


    @Transactional(readOnly = true)
    public UpdateUserResponse sendUser(UUID userId) {
        // 대상 회원 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        return UpdateUserResponse.from(user);
    }
}




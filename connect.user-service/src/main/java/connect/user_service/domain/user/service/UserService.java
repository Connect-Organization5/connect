package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
import connect.user_service.domain.user.dto.request.UpdateUserRequestDto;
import connect.user_service.domain.user.dto.response.UserResponse;
import connect.user_service.domain.user.entity.User;
import connect.user_service.domain.user.entity.UserRole;
import connect.user_service.domain.user.repository.UserRepository;
import connect.user_service.global.exception.BaseException;
import connect.user_service.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final UserRole ROLE_MASTER = UserRole.MASTER;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
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
        String password = passwordEncoder.encode(requestDto.getPassword());

        // 유저 권한 부여 by checking Company ppl

        // 사용자 등록
        User user = new User(username, password, email, UserRole.USER, requestDto.getAffiliationType(), requestDto.getAffiliationId(), requestDto.getSlackId());
        userRepository.save(user);
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

    // 회원 단건 조회
    @Transactional(readOnly = true)
    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));

        return UserResponse.from(user);
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




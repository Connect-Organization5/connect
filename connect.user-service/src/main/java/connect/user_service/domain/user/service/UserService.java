package connect.user_service.domain.user.service;

import connect.user_service.domain.user.dto.request.LoginRequestDto;
import connect.user_service.domain.user.dto.request.SignUpRequestDto;
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

    private void validateUserAccess(User user) {
        if (user.getRole() != ROLE_MASTER) {
            throw new BaseException(ErrorCode.USER_ACCESS_DENIED);
        }
    }


}




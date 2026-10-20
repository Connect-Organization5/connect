package connect.user_service.domain.user.entity;

public enum UserRole {
    USER(UserRole.Authority.USER),  // 사용자 권한
    MASTER(UserRole.Authority.MASTER);  // 관리자 권한

    private final String authority;

    UserRole(String authority) {
        this.authority = authority;
    }

    public String getAuthority() {
        return this.authority;
    }

    public static class Authority {
        public static final String USER = "ROLE_USER";
        public static final String MASTER = "ROLE_MASTER";
    }
}
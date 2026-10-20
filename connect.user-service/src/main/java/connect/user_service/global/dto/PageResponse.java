package connect.user_service.global.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// 페이지 조회 응답 형식 (Spring Data 의 Page 를 그대로 직렬화하지 않고 고정된 형식으로 반환)
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

package connect.hub_service.global.dto;

import org.springframework.data.domain.Page;

import java.util.List;

// hub-api.md의 목록 응답 구조: content, page, size, totalElements, totalPages
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}

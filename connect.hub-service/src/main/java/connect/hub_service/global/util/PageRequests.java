package connect.hub_service.global.util;

import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    // 페이지 번호는 0 이상, 크기는 1~MAX_SIZE. 벗어나면 400
    public static PageRequest of(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new BaseException(ErrorCode.INVALID_PAGE);
        }
        return PageRequest.of(page, size, sort);
    }
}

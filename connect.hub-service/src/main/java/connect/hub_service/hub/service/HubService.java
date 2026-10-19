package connect.hub_service.hub.service;

import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.global.exception.BaseException;
import connect.hub_service.global.exception.ErrorCode;
import connect.hub_service.global.util.PageRequests;
import connect.hub_service.hub.dto.HubResponse;
import connect.hub_service.hub.entity.Hub;
import connect.hub_service.hub.repository.HubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HubService {

    private static final Sort DEFAULT_SORT = Sort.by("name").and(Sort.by("id"));

    private final HubRepository hubRepository;

    public PagedResponse<HubResponse> getHubs(String keyword, int page, int size) {
        PageRequest pageRequest = PageRequests.of(page, size, DEFAULT_SORT);

        Page<Hub> hubs = (keyword == null || keyword.isBlank())
                ? hubRepository.findAll(pageRequest)
                : hubRepository.findByNameContainingOrAddressContaining(keyword.trim(), keyword.trim(), pageRequest);

        return PagedResponse.from(hubs.map(HubResponse::from));
    }

    public HubResponse getHub(UUID hubId) {
        Hub hub = hubRepository.findById(hubId)
                .orElseThrow(() -> new BaseException(ErrorCode.HUB_NOT_FOUND));
        return HubResponse.from(hub);
    }
}

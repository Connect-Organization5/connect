package connect.hub_service.hub.controller;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.hub.controller.docs.HubControllerDocs;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.hub.dto.HubResponse;
import connect.hub_service.hub.service.HubService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/hubs")
@RequiredArgsConstructor
public class HubController implements HubControllerDocs {

    private final HubService hubService;

    @GetMapping
    public ApiResponse<PagedResponse<HubResponse>> getHubs(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok("허브 목록 조회 성공", hubService.getHubs(keyword, page, size));
    }

    @GetMapping("/{hubId}")
    public ApiResponse<HubResponse> getHub(@PathVariable UUID hubId) {
        return ApiResponse.ok("허브 조회 성공", hubService.getHub(hubId));
    }
}

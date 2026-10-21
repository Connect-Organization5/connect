package connect.hub_service.hub.controller;

import connect.hub_service.global.dto.ApiResponse;
import connect.hub_service.hub.controller.docs.HubRouteControllerDocs;
import connect.hub_service.global.dto.PagedResponse;
import connect.hub_service.hub.dto.HubRouteResponse;
import connect.hub_service.hub.service.HubRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/hubs/routes")
@RequiredArgsConstructor
public class HubRouteController implements HubRouteControllerDocs {

    private final HubRouteService hubRouteService;

    @GetMapping
    public ApiResponse<PagedResponse<HubRouteResponse>> getRoutes(
            @RequestParam(required = false) UUID departureHubId,
            @RequestParam(required = false) UUID arrivalHubId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok("이동 경로 목록 조회 성공",
                hubRouteService.getRoutes(departureHubId, arrivalHubId, page, size));
    }

    @GetMapping("/{departureHubId}/{arrivalHubId}")
    public ApiResponse<HubRouteResponse> getRoute(
            @PathVariable UUID departureHubId,
            @PathVariable UUID arrivalHubId) {
        return ApiResponse.ok("이동 경로 조회 성공", hubRouteService.getRoute(departureHubId, arrivalHubId));
    }
}

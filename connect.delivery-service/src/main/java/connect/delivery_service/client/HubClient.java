package connect.delivery_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

//@FeignClient(name = "hub-service")
//public interface HubClient {
//    @GetMapping("/hubs/routes/{departureHubId}/{arrivalHubId}")
//    HubRouteResponse getRoute(@PathVariable("departureHubId") UUID departureHubId, @PathVariable("arrivalHubId") UUID arrivalHubId);
//
//}

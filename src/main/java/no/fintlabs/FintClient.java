package no.fintlabs;

import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FintClient {
    private final RestClient restClient;

    private final Map<String, Long> sinceTimestamp = new ConcurrentHashMap<>();

    public FintClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<Object> getResourcesLastUpdated(String endpoint) {
        return getLastUpdated(endpoint).getContent();
    }

    public void resetLastUpdatedTimestamps() {
        this.sinceTimestamp.clear();
    }

    private ObjectResources getLastUpdated(String endpoint) {
        LastUpdated lastUpdated = restClient.get()
                .uri(endpoint.concat("/last-updated"))
                .retrieve()
                .body(LastUpdated.class);

        ObjectResources objectResources = restClient.get()
                .uri(endpoint, uriBuilder -> uriBuilder.queryParam("sinceTimeStamp", sinceTimestamp.getOrDefault(endpoint, 0L)).build())
                .retrieve()
                .body(ObjectResources.class);

        sinceTimestamp.put(endpoint, lastUpdated.getLastUpdated());
        return objectResources;
    }

    public Object getResource(String endpoint) {
        return restClient.get()
                .uri(endpoint)
                .retrieve()
                .body(Object.class);
    }

    public <T> T getResource(String endpoint, Class<T> clazz) {
        return restClient.get()
                .uri(endpoint)
                .retrieve()
                .body(clazz);
    }

    @Data
    private static class LastUpdated {
        private Long lastUpdated;
    }

    public void reset() {
        sinceTimestamp.clear();
    }
}

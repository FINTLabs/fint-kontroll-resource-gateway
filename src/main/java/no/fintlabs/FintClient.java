package no.fintlabs;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.UnknownContentTypeException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
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
        String requestEndpoint = endpoint.concat("/last-updated");
        try {
            LastUpdated lastUpdated = restClient.get()
                    .uri(requestEndpoint)
                    .retrieve()
                    .body(LastUpdated.class);

            requestEndpoint = endpoint;
            ObjectResources objectResources = restClient.get()
                    .uri(endpoint, uriBuilder -> uriBuilder.queryParam("sinceTimeStamp", sinceTimestamp.getOrDefault(endpoint, 0L)).build())
                    .retrieve()
                    .body(ObjectResources.class);

            sinceTimestamp.put(endpoint, lastUpdated.getLastUpdated());
            return objectResources;
        } catch (UnknownContentTypeException exception) {
            log.error("Unexpected FINT response for endpoint={}: status={}, contentType={}, headers={}, body={}",
                    requestEndpoint,
                    exception.getStatusCode().value(),
                    exception.getContentType(),
                    exception.getResponseHeaders(),
                    exception.getResponseBodyAsString());
            throw exception;
        } catch (RestClientResponseException exception) {
            log.error("FINT request failed for endpoint={}: status={}, headers={}, body={}",
                    requestEndpoint,
                    exception.getStatusCode().value(),
                    exception.getResponseHeaders(),
                    exception.getResponseBodyAsString());
            throw exception;
        }
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

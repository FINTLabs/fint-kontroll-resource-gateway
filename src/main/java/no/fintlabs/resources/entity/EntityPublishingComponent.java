package no.fintlabs.resources.entity;

import lombok.extern.slf4j.Slf4j;
import no.fintlabs.FintClient;
import no.novari.kafka.producing.ParameterizedProducerRecord;
import no.novari.kafka.producing.ParameterizedTemplate;
import no.novari.kafka.producing.ParameterizedTemplateFactory;
import no.novari.kafka.topic.EntityTopicService;
import no.novari.kafka.topic.configuration.EntityCleanupFrequency;
import no.novari.kafka.topic.configuration.EntityTopicConfiguration;
import no.fintlabs.resources.entity.properties.EntityConfiguration;
import no.fintlabs.resources.entity.properties.EntityPipelineConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty("fint.resource-gateway.resources.entity.enabled")
public class EntityPublishingComponent {

    private final ParameterizedTemplate<Object> parameterizedTemplate;
    private final FintClient fintClient;
    private final List<EntityPipeline> entityPipelines;

    public EntityPublishingComponent(
            EntityTopicService entityTopicService,
            ParameterizedTemplateFactory parameterizedTemplateFactory,
            EntityConfiguration entityConfiguration,
            EntityPipelineFactory entityPipelineFactory,
            FintClient fintClient
    ) {
        this.parameterizedTemplate = parameterizedTemplateFactory.createTemplate(Object.class);
        this.fintClient = fintClient;
        this.entityPipelines = this.createEntityPipelines
                (
                        entityPipelineFactory,
                        entityConfiguration.getEntityPipelines()
                );

        entityPipelines.forEach(entityPipeline -> {
            entityTopicService.createOrModifyTopic(entityPipeline.getTopicNameParameters(), EntityTopicConfiguration
                    .stepBuilder()
                    .partitions(1)
                    .lastValueRetainedForever()
                    .nullValueRetentionTime(Duration.ofDays(7))
                    .cleanupFrequency(EntityCleanupFrequency.NORMAL)
                    .build()
            );
        });
    }

    private List<EntityPipeline> createEntityPipelines(
            EntityPipelineFactory entityPipelineFactory,
            List<EntityPipelineConfiguration> configs) {
        return configs.stream()
                .map(entityPipelineFactory::create)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRateString = "#{entityConfiguration.refresh.intervalMs}")
    private void resetLastUpdatedTimestamps() {
        log.warn("Resetting resource last updated timestamps");
        this.fintClient.resetLastUpdatedTimestamps();
    }

    @Scheduled(
            initialDelayString = "#{entityConfiguration.pull.initialDelayMs}",
            fixedDelayString = "#{entityConfiguration.pull.fixedDelayMs}")
    private void pullAllUpdatedEntityResources() {
        log.info("Starting pulling resources");
        entityPipelines.forEach(this::pullUpdatedEntityResources);
        log.info("Completed pulling resources");
    }

    private void pullUpdatedEntityResources(EntityPipeline entityPipeline) {
        List<HashMap<String, Object>> resources = getUpdatedResources(entityPipeline.getFintEndpoint());
        for (HashMap<String, Object> resource : resources) {
            String key = getKey(resource, entityPipeline.getSelfLinkKeyFilter());
            parameterizedTemplate.send(
                    ParameterizedProducerRecord.builder()
                            .topicNameParameters(entityPipeline.getTopicNameParameters())
                            .key(key)
                            .value(resource)
                            .build()
            );
        }
        log.info(resources.size() + " entities sent to " + entityPipeline.getTopicNameParameters().getResourceName());
    }

    private List<HashMap<String, Object>> getUpdatedResources(String endpointUrl) {
        try {
            return Objects.requireNonNull(fintClient.getResourcesLastUpdated(endpointUrl))
                    .stream()
                    .map(r -> ((HashMap<String, Object>) r))
                    .collect(Collectors.toList());
        } catch (RestClientException e) {
            log.error("Could not pull entities from endpoint=" + endpointUrl, e);
            return Collections.emptyList();
        }
    }

    // TODO: 19/11/2021 Handle exceptions (casting and no systemid)
    private String getKey(HashMap<String, Object> resource, String selfLinkKeyFilter) {
        HashMap<String, Object> links = (HashMap<String, Object>) resource.get("_links");
        List<HashMap<String, String>> selfLinks = (List<HashMap<String, String>>) links.get("self");
        return selfLinks.stream()
                .filter(o -> o.containsKey("href"))
                .map(o -> o.get("href"))
                .map(k -> k.replaceFirst("^https:/\\/.+\\.felleskomponent.no", ""))
                .filter(o -> o.toLowerCase().contains(selfLinkKeyFilter))
                .min(String::compareTo)
                .orElseThrow(() -> new IllegalStateException(String.format("No %s to generate key for resource=%s",
                        selfLinkKeyFilter, resource)));
    }

}

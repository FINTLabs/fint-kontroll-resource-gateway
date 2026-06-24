package no.fintlabs;

import no.novari.kafka.consuming.ErrorHandlerConfiguration;
import no.novari.kafka.consuming.ErrorHandlerFactory;
import no.novari.kafka.consuming.ListenerConfiguration;
import no.novari.kafka.topic.name.EntityTopicNameParameters;
import no.novari.kafka.topic.name.TopicNamePrefixParameters;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.stereotype.Component;

@Component
public class KafkaConsumerConfigurationDefaults {
    private final ErrorHandlerFactory errorHandlerFactory;

    public KafkaConsumerConfigurationDefaults(ErrorHandlerFactory errorHandlerFactory) {
        this.errorHandlerFactory = errorHandlerFactory;
    }

    public  <T> CommonErrorHandler defaultErrorHandler() {

        return errorHandlerFactory.createErrorHandler(
                ErrorHandlerConfiguration.<T>stepBuilder()
                        .noRetries()
                        .skipFailedRecords()
                        .build()
        );
    }

    public ListenerConfiguration continueFromPreviousListenerConfiguration() {

        return ListenerConfiguration.stepBuilder()
                .groupIdApplicationDefault()
                .maxPollRecordsKafkaDefault()
                .maxPollIntervalKafkaDefault()
                .continueFromPreviousOffsetOnAssignment()
                .build();
    }

    public ListenerConfiguration seekToBeginningListenerConfiguration() {

        return ListenerConfiguration.stepBuilder()
                .groupIdApplicationDefault()
                .maxPollRecordsKafkaDefault()
                .maxPollIntervalKafkaDefault()
                .seekToBeginningOnAssignment()
                .build();
    }

    public TopicNamePrefixParameters defaultTopicNamePrefixParameters() {

        return TopicNamePrefixParameters.stepBuilder()
                .orgIdApplicationDefault()
                .domainContextApplicationDefault()
                .build();
    }


    public EntityTopicNameParameters defaultEntityTopic(String resourceName) {

        return EntityTopicNameParameters.builder()
                .topicNamePrefixParameters(defaultTopicNamePrefixParameters())
                .resourceName(resourceName)
                .build();
    }
}

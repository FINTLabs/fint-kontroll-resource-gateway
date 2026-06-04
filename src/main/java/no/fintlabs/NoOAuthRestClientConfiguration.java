package no.fintlabs;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Getter
@Setter
@Configuration
@ConditionalOnProperty(name = "fint.kontroll.datainput", havingValue = "mock")
@ConfigurationProperties(prefix = "fint.client")
@Slf4j
public class NoOAuthRestClientConfiguration {

    private String baseUrl;

    @Bean
    public ClientHttpRequestFactory clientHttpRequestFactory() {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMinutes(5))
                        .build()
        );
        requestFactory.setReadTimeout(Duration.ofMinutes(5));
        return requestFactory;
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder, ClientHttpRequestFactory clientHttpRequestFactory) {
        RestClient restClient = builder
                .requestFactory(clientHttpRequestFactory)
                .baseUrl(baseUrl)
                .build();
        log.info("simple restclient without authentication created");
        return restClient;
    }

}

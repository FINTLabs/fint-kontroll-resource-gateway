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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Getter
@Setter
@Configuration
@ConditionalOnProperty(name= "fint.kontroll.datainput", havingValue = "fint")
@ConfigurationProperties(prefix = "fint.client")
@Slf4j
public class OAuthRestClientConfiguration {

    private String baseUrl;
    private String username;
    private String password;
    private String registrationId;

    @Bean
    public Authentication dummyAuthentication() {
        return new UsernamePasswordAuthenticationToken("fint", "client", Collections.emptyList());
    }

    @Bean
    @ConditionalOnProperty(name = "fint.resource-gateway.authorization", havingValue = "enabled")
    public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                 OAuth2AuthorizedClientService authorizedClientService) {

        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .password()
                .refreshToken()
                .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService);

        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        authorizedClientManager.setContextAttributesMapper(contextAttributesMapper());

        return authorizedClientManager;
    }

    private Function<OAuth2AuthorizeRequest, Map<String, Object>> contextAttributesMapper() {
        return authorizeRequest -> {
            Map<String, Object> contextAttributes = new HashMap<>();
            contextAttributes.put(OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME, username);
            contextAttributes.put(OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME, password);
            return contextAttributes;
        };
    }

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
    public RestClient restClient(
            RestClient.Builder builder,
            Optional<OAuth2AuthorizedClientManager> authorizedClientManager,
            ClientHttpRequestFactory clientHttpRequestFactory,
            Authentication dummyAuthentication
    ) {
        authorizedClientManager.ifPresent(presentAuthorizedClientManager -> {
            OAuth2ClientHttpRequestInterceptor oauth2ClientHttpRequestInterceptor =
                    new OAuth2ClientHttpRequestInterceptor(presentAuthorizedClientManager);
            oauth2ClientHttpRequestInterceptor.setClientRegistrationIdResolver(request -> registrationId);
            oauth2ClientHttpRequestInterceptor.setPrincipalResolver(request -> dummyAuthentication);
            builder.requestInterceptor(oauth2ClientHttpRequestInterceptor);
        });

        log.info("oAuth restclient created");

        return builder
                .requestFactory(clientHttpRequestFactory)
                .baseUrl(baseUrl)
                .build();
    }

}

package pl.gov.coi.eunflowruadapterbe.config.integration;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import pl.gov.coi.eunflowruadapterbe.ewyrys.client.api.ExtractEpucApi;
import pl.gov.coi.eunflowruadapterbe.ewyrys.client.invoker.ApiClient;
import pl.gov.coi.eunflowruadapterbe.service.EwyrysApiSecurityService;

@Configuration
public class EwyrysApiClientConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(EwyrysApiClientConfiguration.class);

    private final EwyrysApiSecurityService ewyrysApiSecurityService;

    public EwyrysApiClientConfiguration(EwyrysApiSecurityService ewyrysApiSecurityService) {
        this.ewyrysApiSecurityService = ewyrysApiSecurityService;
    }

    @Value("${ewyrysapi.base-url}")
    private String baseUrl;

    @Value("${ewyrysapi.connect-timeout-in-seconds}")
    private int connectTimeout;

    @Value("${ewyrysapi.read-timeout-in-seconds}")
    private int readTimeout;

    // Allow switching strictness per environment (default: strict)
    @Value("${ewyrysapi.fail-on-unknown-properties:true}")
    private boolean failOnUnknownProperties;

    @Value("${ssl.off:false}")
    private boolean sslOff;    

    @Bean(name = "ewyrysApiClient")
    ApiClient ewyrysApiClient() {

        // 1. Create a logging interceptor
        ClientHttpRequestInterceptor logging = (request, body, execution) -> {
            logger.info("<=== EWYRYS API START ===>");
            logger.info("{} {}", request.getMethod(), request.getURI());
            request.getHeaders().forEach((k, v) -> {
                if ("authorization".equalsIgnoreCase(k)) {
                    logger.info("{}: {}", k, "[REDACTED]");
                } else {
                    logger.info("{}: {}", k, v);
                }
            });
            // Log body only for JSON and keep it short
            String ct = request.getHeaders().getFirst("Content-Type");
            if (ct != null && (ct.startsWith("application/json") || ct.contains("+json"))) {
                if (body != null && body.length < 1000) {
                    String preview = new String(body, StandardCharsets.UTF_8);
                    logger.info("Body: {}", preview);
                }
            }
            logger.info("<=== EWYRYS API END ===>");
            return execution.execute(request, body);
        };

        // 2. Create a mapper that writes dates as ISO-8601 strings
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.configOverride(OffsetDateTime.class).setFormat(JsonFormat.Value.forShape(JsonFormat.Shape.STRING));
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknownProperties);

        // 3. Create a JSON message converter with our mapper
        MappingJackson2HttpMessageConverter jackson = new MappingJackson2HttpMessageConverter(objectMapper);
        jackson.setSupportedMediaTypes(List.of(
            MediaType.APPLICATION_JSON,
            MediaType.valueOf("application/problem+json"),
            MediaType.valueOf("application/*+json")
        ));

        // 4. Configure timeouts
        ClientHttpRequestFactory requestFactory = null;
        if (sslOff) {
            logger.warn("RestClient EWYRYS API SSL verification is disabled (ssl.off=true). This is NOT recommended for production environments.");
            requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                setConnectTimeout(Duration.ofSeconds(connectTimeout));
                setReadTimeout(Duration.ofSeconds(readTimeout));
            }};
            
        } else {
            ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings
                    .defaults()
                    .withConnectTimeout(Duration.ofSeconds(connectTimeout))
                    .withReadTimeout(Duration.ofSeconds(readTimeout));

            requestFactory = ClientHttpRequestFactoryBuilder.detect().build(settings);
        }

        RestClient restClient = RestClient.builder()
            .requestInitializer(request -> {
                String token = ewyrysApiSecurityService.getEwyrysAccessToken();
                request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            })
            .requestFactory(requestFactory)
            .requestInterceptor(logging)
            .messageConverters(list -> {
                list.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                list.add(0, jackson);
            })
            .build();

        ApiClient apiClient = new ApiClient(restClient, objectMapper, ApiClient.createDefaultDateFormat());
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }
    
    @Bean
    public ExtractEpucApi extractEpucApi(@Qualifier("ewyrysApiClient") ApiClient apiClient) {
        return new ExtractEpucApi(apiClient);
    }

}

package pl.gov.coi.eunflowruadapterbe.config.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.util.StreamUtils;

@Configuration
public class EwyrysApiWireMockConfig {

    private static final Logger logger = LoggerFactory.getLogger(EwyrysApiWireMockConfig.class);

    @Value("${wiremock.ewyrys.port:8000}")
    private int wireMockPort;

    @Value("${wiremock.ewyrys.openapi-location:contract/ewyrys/rest/mrit-ru-eservices-be-api-extract-epuc-v1.0.yaml}")
    private String openApiLocation;    

    private WireMockServer wireMockServer;

    @Bean(destroyMethod = "stop")
    public WireMockServer completeSeApiWireMockServer() {
        try {

            WireMockConfiguration config = WireMockConfiguration.options()
            .disableRequestJournal()
            .asynchronousResponseEnabled(true)
            .port(wireMockPort)
            .extensions(new EwyrysApiRequestResponseOpenApiValidationTransformer(openApiLocation));

            wireMockServer = new WireMockServer(config);
            wireMockServer.start();

            setupCompleteApiMocks();

            logger.info("Complete Ewyrys Api WireMock started on port: {}", wireMockPort);
            return wireMockServer;
        } catch (Exception e) {
            logger.error("Failed to start Complete Ewyrys Api WireMock on port: {}", wireMockPort, e);
            throw new RuntimeException("Failed to start Complete Ewyrys Api WireMock", e);
        }
    }

    @PreDestroy
    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            logger.info("SeApi WireMock stopped");
        }
    }

    private void setupCompleteApiMocks() {

        // Mock for GET /ewyrys-api/entries
        setupCreateApplicationEndpoints();

        setupUpdateApplicationStatusEndpoints();

        logger.info("Setup complete Ewyrys Api WireMock with all endpoint categories");
    }

    private void setupCreateApplicationEndpoints() {
        wireMockServer.stubFor(post(urlPathMatching("/ewyrys-epuc/v1.0/application"))
            .withRequestBody(matching(".*(businesskey-ok).*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.CREATED.value())
                .withHeader("Content-Type", "application/json")
            ));

        wireMockServer.stubFor(post(urlPathMatching("/ewyrys-epuc/v1.0/application"))
            .withRequestBody(matching(".*(businesskey-ok).*"))
            .withHeader("X-Delay-create-application", matching("true"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.CREATED.value())
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(10000)
            ));            

        wireMockServer.stubFor(post(urlPathMatching("/ewyrys-epuc/v1.0/application"))
            .withRequestBody(matching(".*(businesskey-conflict).*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.CONFLICT.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/createApplication/post-create-application-conflict.json"))
            )); 
            
        wireMockServer.stubFor(post(urlPathMatching("/ewyrys-epuc/v1.0/application"))
            .withRequestBody(matching(".*(businesskey-forbidden).*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.FORBIDDEN.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/createApplication/post-create-application-forbidden.json"))
            )); 
            
         wireMockServer.stubFor(post(urlPathMatching("/ewyrys-epuc/v1.0/application"))
            .withRequestBody(matching(".*(businesskey-internal-server-error).*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/createApplication/post-create-application-internal-server-error.json"))
            )); 
            
        wireMockServer.stubFor(post(urlPathMatching("/ewyrys-epuc/v1.0/application"))
            .withRequestBody(matching(".*(businesskey-unauthorized).*"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.UNAUTHORIZED.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/createApplication/post-create-application-unauthorized.json"))
            ));             

    }

    private void setupUpdateApplicationStatusEndpoints() {
            wireMockServer.stubFor(put(urlPathMatching("/ewyrys-epuc/v1.0/application/businesskey-ok"))
                .willReturn(aResponse()
                    .withStatus(HttpStatus.NO_CONTENT.value())
                    .withHeader("Content-Type", "application/json")
                ));

        wireMockServer.stubFor(put(urlPathMatching("/ewyrys-epuc/v1.0/application/businesskey-ok"))
            .withHeader("X-Delay-update-application-status", matching("true"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.NO_CONTENT.value())
                .withHeader("Content-Type", "application/json")
                .withFixedDelay(10000)
            ));

        wireMockServer.stubFor(put(urlPathMatching("/ewyrys-epuc/v1.0/application/businesskey-notfound"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.NOT_FOUND.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/updateApplicationStatus/put-update-application-status-notfound.json"))
            ));

        wireMockServer.stubFor(put(urlPathMatching("/ewyrys-epuc/v1.0/application/businesskey-internal-server-error"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/updateApplicationStatus/put-update-application-status-internal-server-error.json"))
            ));
            
        wireMockServer.stubFor(put(urlPathMatching("/ewyrys-epuc/v1.0/application/businesskey-unauthorized"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.UNAUTHORIZED.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/updateApplicationStatus/put-update-application-status-unauthorized.json"))
            ));
            
           wireMockServer.stubFor(put(urlPathMatching("/ewyrys-epuc/v1.0/application/businesskey-forbidden"))
            .willReturn(aResponse()
                .withStatus(HttpStatus.FORBIDDEN.value())
                .withHeader("Content-Type", "application/json")
                .withBody(loadResponseFromFile("wiremock/responses/ewyrys/updateApplicationStatus/put-update-application-status-forbidden.json"))
            )); 

    }

    /**
     * Loads a response from a specified file in the classpath.
     *
     * @param filePath the path of the file to load
     * @return the content of the file as a String
     */
    private String loadResponseFromFile(String filePath) {
        try {
            ClassPathResource resource = new ClassPathResource(filePath);
            try (InputStream inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.error("Failed to load response file: {}", filePath, e);
            return createFallbackErrorResponse(filePath);
        }
    }

    /**
     * Creates a fallback error response in case the specified file cannot be loaded.
     *
     * @param filePath the path of the file that failed to load
     * @return a JSON string representing the error response
     */
    private String createFallbackErrorResponse(String filePath) {
        return String.format("""
            {
              "error": "CONFIGURATION_ERROR",
              "message": "Failed to load mock response",
              "details": "Response file '%s' could not be loaded",
              "timestamp": "%s"
            }
            """, filePath, java.time.Instant.now().toString());
    }

}
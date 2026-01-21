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
public class OsApiWireMockConfig {

    private static final Logger logger = LoggerFactory.getLogger(OsApiWireMockConfig.class);

    @Value("${wiremock.osapi.port:8087}")
    private int wireMockPort;

    private WireMockServer wireMockServer;

    @Bean(destroyMethod = "stop")
    public WireMockServer completeOsApiWireMockServer() {
        try {

            WireMockConfiguration config = WireMockConfiguration.options()
            .disableRequestJournal()
            .asynchronousResponseEnabled(true)
            .port(wireMockPort);

            wireMockServer = new WireMockServer(config);
            wireMockServer.start();

            setupCompleteApiMocks();

            logger.info("Complete OsApi WireMock started on port: {}", wireMockPort);
            return wireMockServer;
        } catch (Exception e) {
            logger.error("Failed to start Complete OsApi WireMock on port: {}", wireMockPort, e);
            throw new RuntimeException("Failed to start Complete OsApi WireMock", e);
        }
    }

    @PreDestroy
    public void stopWireMock() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            logger.info("OsApi WireMock stopped");
        }
    }

    private void setupCompleteApiMocks() {
        // Download endpoints
        setupInsideDownloadEndpoints();
        logger.info("Setup complete OsApi WireMock with all endpoint categories");
    }

    private void setupInsideDownloadEndpoints() {
             
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // SUCCESS SCENARIOS - Binary file downloads with proper headers
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    // Small xml file - for quick testing
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-ok-xml\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", "application/xml")
            .withHeader("Content-Disposition", "attachment; filename=\"=?UTF-8?Q?reference-ok-xml.xml?=\"; filename*=UTF-8''reference-ok-xml.xml")
            .withHeader("Reference-ID", "reference-ok-xml")
            .withBody("<test>This is a small test XML file content for streaming download testing.</test>")
            ));

    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-ok-xml-delay\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", "application/xml")
            .withHeader("Content-Disposition", "attachment; filename=\"=?UTF-8?Q?reference-ok-xml-delay.xml?=\"; filename*=UTF-8''reference-ok-xml-delay.xml")
            .withHeader("Reference-ID", "reference-ok-xml-delay")
            .withBody("<test>This is a small test XML file content for streaming download testing.</test>")
            .withFixedDelay(10000)
            ));            
               
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // EDGE CASES & SPECIAL SCENARIOS
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    // Empty file
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-ok-empty\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", "application/xml")
            .withHeader("Content-Disposition", "attachment; filename=\"=?UTF-8?Q?empty-file.xml?=\"; filename*=UTF-8''empty-file.xml")
            .withHeader("Reference-ID", "reference-ok-empty")
            .withHeader("Content-Length", "0")
            .withBody("")
            ));
    
    // Missing Content-Disposition header
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-ok-no-disposition\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", "application/xml")
            .withHeader("Reference-ID", "reference-ok-no-disposition")
            .withBody("<test>This is a small test XML file content for streaming download testing.</test>")
            ));
    
    // Missing filename header
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-ok-no-filename\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", "application/pdf")
            .withHeader("Content-Disposition", "attachment; filename=\"=?UTF-8?Q?extracted-from-disposition.pdf?=\"; filename*=UTF-8''extracted-from-disposition.pdf")
            .withHeader("Reference-ID", "reference-ok-no-filename")
            .withBody("PDF content - filename should be extracted from Content-Disposition")
            ));
        
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // ERROR SCENARIOS
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    // File not found
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-not-found\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                    "error": "FILE_NOT_FOUND",
                    "message": "File with reference 'reference-not-found' not found",
                    "timestamp": "%s"
                }""".formatted(java.time.Instant.now().toString()))
            ));
    
    // Hash mismatch - integrity check failed
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-wrong-hash\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.UNPROCESSABLE_ENTITY.value())
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                    "error": "INTEGRITY_CHECK_FAILED",
                    "message": "Hash from meta data do not match hash from nfs content!",
                    "timestamp": "%s"
                }""".formatted(java.time.Instant.now().toString()))
            ));
    
    // Unauthorized access
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-unauthorized\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.UNAUTHORIZED.value())
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                    "error": "UNAUTHORIZED",
                    "message": "Unauthorized access to the resource",
                    "timestamp": "%s"
                }""".formatted(java.time.Instant.now().toString()))
            ));
    
    // Forbidden access
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-forbidden\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.FORBIDDEN.value())
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                    "error": "FORBIDDEN",
                    "message": "Forbidden access to the resource",
                    "timestamp": "%s"
                }""".formatted(java.time.Instant.now().toString()))
            ));
    
    // Server error
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-server-error\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                    "error": "INTERNAL_SERVER_ERROR",
                    "message": "Internal server error occurred",
                    "timestamp": "%s"
                }""".formatted(java.time.Instant.now().toString()))
            ));
    
    // Service unavailable
    wireMockServer.stubFor(post(urlPathMatching("/api/v3.0.0/files/inside-download"))
        .withRequestBody(containing("\"reference\":\"reference-service-unavailable\""))
        .willReturn(aResponse()
            .withStatus(HttpStatus.SERVICE_UNAVAILABLE.value())
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                    "error": "SERVICE_UNAVAILABLE",
                    "message": "Service temporarily unavailable",
                    "timestamp": "%s"
                }""".formatted(java.time.Instant.now().toString()))
            ));
        
    logger.info("Setup complete: inside-download endpoints with streaming support");
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
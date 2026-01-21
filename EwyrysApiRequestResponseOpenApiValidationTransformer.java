package pl.gov.coi.eunflowruadapterbe.config.mock;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EwyrysApiRequestResponseOpenApiValidationTransformer extends ResponseTransformer {

    private static final Logger logger = LoggerFactory.getLogger(EwyrysApiRequestResponseOpenApiValidationTransformer.class);
    private final OpenApiInteractionValidator validator;

    public EwyrysApiRequestResponseOpenApiValidationTransformer(String classpathSpecPath) {
        this.validator = OpenApiInteractionValidator
            .createFor(classpathSpecPath)
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request.security.*", ValidationReport.Level.ERROR)
                    .withLevel("validation.response.security.*", ValidationReport.Level.ERROR)
                    // ignore responses with a status code not declared in the spec (e.g. 401/403 in negative flows)
                    .withLevel("validation.response.status.unknown", ValidationReport.Level.IGNORE)
                .build()
            )
            .build();
    }   

    @Override
    public String getName() {
        return "ewyrys-openapi-response-validator";
    }

    @Override
    public boolean applyGlobally() {
        return true;
    }

    @Override
    public Response transform(Request request, Response response, FileSource files, Parameters parameters) {

        com.atlassian.oai.validator.model.Request.Method method =
            com.atlassian.oai.validator.model.Request.Method.valueOf(request.getMethod().getName());

        com.atlassian.oai.validator.model.SimpleRequest.Builder reqBuilder =
            new com.atlassian.oai.validator.model.SimpleRequest.Builder(method, request.getUrl());

        for (String name : request.getAllHeaderKeys()) {
            com.github.tomakehurst.wiremock.http.HttpHeader header = request.header(name);
            if (header != null && !header.values().isEmpty()) {
                for (String value : header.values()) {
                    reqBuilder.withHeader(name, value);
                }
            }
        }

        // Enforce Authorization: Bearer <token>
        String auth = request.getHeader("Authorization");
        boolean hasBearer = auth != null && auth.toLowerCase().startsWith("bearer ");
        boolean tokenPresent = hasBearer && auth != null && auth.length() > 7 && !auth.substring(7).trim().isEmpty();
        if (!tokenPresent) {
            // For success stubs, fail with 401 if Authorization is missing/invalid
            return com.github.tomakehurst.wiremock.http.Response.response()
                .status(401)
                .headers(new com.github.tomakehurst.wiremock.http.HttpHeaders(
                    new com.github.tomakehurst.wiremock.http.HttpHeader("Content-Type", "text/plain; charset=UTF-8"),
                    new com.github.tomakehurst.wiremock.http.HttpHeader("WWW-Authenticate", "Bearer")
                ))
                .body("Ewyrys API Missing or invalid Authorization: Bearer <token>")
                .build();
        }          

        byte[] requestBodyBytes = request.getBody();
        if (requestBodyBytes != null && requestBodyBytes.length > 0) {
            // ensure validator treats body as JSON when header is missing
            String contentType = request.getHeader("Content-Type");
            if (contentType == null || contentType.isBlank()) {
                reqBuilder.withContentType("application/json");
            } else {
                reqBuilder.withContentType(contentType);
            }
            reqBuilder.withBody(new String(requestBodyBytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        try {

            var reportRequest = validator.validateRequest(reqBuilder.build());

            boolean hasRequestErrors = reportRequest.getMessages().stream()
                .anyMatch(m -> m.getLevel() == com.atlassian.oai.validator.report.ValidationReport.Level.ERROR);

            if (hasRequestErrors) {
                StringBuilder sb = new StringBuilder("Ewyrys API OpenAPI request validation failed:");
                for (var m : reportRequest.getMessages()) {
                    sb.append("\n- [").append(m.getLevel()).append("] ").append(m.getMessage());
                    if (m.getContext() != null && !m.getContext().isEmpty()) {
                        sb.append(" (").append(m.getContext()).append(")");
                    }
                }
                logger.error("!!! Ewyrys API OpenAPI request validation failed for request: " + sb.toString());
                return com.github.tomakehurst.wiremock.http.Response.Builder
                        .like(response)
                        .but()
                        .status(400)
                        .headers(new com.github.tomakehurst.wiremock.http.HttpHeaders(
                                new com.github.tomakehurst.wiremock.http.HttpHeader("Content-Type", "text/plain; charset=UTF-8")))
                        .body(sb.toString())
                        .build();
            }

        } catch (Exception e) {
            return com.github.tomakehurst.wiremock.http.Response.Builder
                    .like(response)
                    .but()
                    .status(400)
                    .headers(new com.github.tomakehurst.wiremock.http.HttpHeaders(
                            new com.github.tomakehurst.wiremock.http.HttpHeader("Content-Type", "text/plain; charset=UTF-8")))
                    .body("Ewyrys API OpenAPI request validation error: " + e.getMessage())
                    .build();
        }

        try {
            // Validate response
            SimpleResponse.Builder resp = new SimpleResponse.Builder(response.getStatus());

            for (com.github.tomakehurst.wiremock.http.HttpHeader h : response.getHeaders().all()) {
                if (h != null && !h.values().isEmpty()) {
                    for (String v : h.values()) {
                        resp.withHeader(h.key(), v);
                    }
                }
            }

            String responseBody = response.getBodyAsString();
            if (responseBody != null) {
                resp.withBody(responseBody);
            }

            ValidationReport reportResponse = validator.validateResponse(
                request.getUrl(), method, resp.build()
            );

            boolean hasResponseErrors = reportResponse.getMessages().stream()
                .anyMatch(m -> m.getLevel() == com.atlassian.oai.validator.report.ValidationReport.Level.ERROR);

            if (hasResponseErrors) {
                StringBuilder sb = new StringBuilder("Ewyrys API OpenAPI response validation failed:");
                for (ValidationReport.Message m : reportResponse.getMessages()) {
                    sb.append("\n- [").append(m.getLevel()).append("] ").append(m.getMessage());
                    if (m.getContext() != null && !m.getContext().isEmpty()) {
                        sb.append(" (").append(m.getContext()).append(")");
                    }
                }
                logger.error("!!! Ewyrys API OpenAPI response validation failed for request: " + sb.toString());
                return com.github.tomakehurst.wiremock.http.Response.Builder
                        .like(response)
                        .but()
                        .status(400)
                        .headers(new com.github.tomakehurst.wiremock.http.HttpHeaders(
                                new com.github.tomakehurst.wiremock.http.HttpHeader("Content-Type", "text/plain; charset=UTF-8")))
                        .body(sb.toString())
                        .build();
            }

        } catch (Exception e) {
            return com.github.tomakehurst.wiremock.http.Response.Builder
                    .like(response)
                    .but()
                    .status(400)
                    .headers(new com.github.tomakehurst.wiremock.http.HttpHeaders(
                            new com.github.tomakehurst.wiremock.http.HttpHeader("Content-Type", "text/plain; charset=UTF-8")))
                    .body("Ewyrys API OpenAPI response validation error: " + e.getMessage())
                    .build();
        }

        return response;

    }
}
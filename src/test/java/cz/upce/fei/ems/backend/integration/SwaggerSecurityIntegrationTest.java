package cz.upce.fei.ems.backend.integration;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SwaggerSecurityIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void swaggerUiAndOpenApiRequireConfiguredBasicAuthentication() throws Exception {
        HttpResponse<String> unauthenticatedUi = get("/swagger-ui/index.html", null);
        assertEquals(401, unauthenticatedUi.statusCode());
        assertEquals(401, get("/v3/api-docs", null).statusCode());

        assertEquals(200, getWithSwaggerCredentials("/swagger-ui/index.html").statusCode());
        assertEquals(200, getWithSwaggerCredentials("/v3/api-docs").statusCode());
    }

    private HttpResponse<String> getWithSwaggerCredentials(String path) throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString("swagger:test-swagger-password".getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Authorization", "Basic " + credentials)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

package cz.upce.fei.ems.backend.integration;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwaggerSecurityIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void swaggerUiAndOpenApiRequireConfiguredBasicAuthentication() throws Exception {
        HttpResponse<String> unauthenticatedUi = get("/swagger-ui/index.html", null);
        assertEquals(401, unauthenticatedUi.statusCode());
        assertEquals(401, get("/v3/api-docs", null).statusCode());

        assertEquals(200, getWithSwaggerCredentials("/swagger-ui/index.html").statusCode());
        assertEquals(200, getWithSwaggerCredentials("/v3/api-docs").statusCode());
    }

    @Test
    void javadocPagesAndAssetsRequireConfiguredBasicAuthentication() throws Exception {
        for (String path : new String[]{"/javadoc/index.html", "/javadoc/stylesheet.css",
                "/javadoc/cz/upce/fei/ems/backend/config/SecurityConfig.html"}) {
            assertEquals(401, get(path, null).statusCode());
            assertEquals(401, getWithBasicCredentials(path, "swagger:wrong-password").statusCode());
            HttpResponse<String> response = getWithSwaggerCredentials(path);
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElse("")
                    .startsWith(path.endsWith(".css") ? "text/css" : "text/html"));
            assertTrue(!response.body().isBlank());
        }
    }

    private HttpResponse<String> getWithSwaggerCredentials(String path) throws Exception {
        return getWithBasicCredentials(path, "swagger:test-swagger-password");
    }

    private HttpResponse<String> getWithBasicCredentials(String path, String usernameAndPassword)
            throws Exception {
        String credentials = Base64.getEncoder()
                .encodeToString(usernameAndPassword.getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header("Authorization", "Basic " + credentials)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}

package cz.upce.fei.ems.backend.integration;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.dto.RegisterRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Import(GlobalExceptionHandlerIntegrationTest.ExceptionProbeController.class)
class GlobalExceptionHandlerIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void validationErrorsReturnStructuredJson() throws Exception {
        RegisterRequestDto request = new RegisterRequestDto();
        request.setUsername("ab");
        request.setPassword("12345");
        request.setPublicKey("key");

        HttpResponse<String> response = post("/api/auth/register", null, request);
        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode());
        assertJsonError(response);
        assertEquals(400, body.get("status").asInt());
        assertEquals("/api/auth/register", body.get("instance").asText());
        assertTrue(body.get("errors").has("username") || body.get("errors").has("password"));
    }

    @Test
    void malformedJsonReturnsStructuredBadRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(400, response.statusCode());
        assertJsonError(response);
        assertEquals("Bad Request", body.get("title").asText());
        assertEquals("Request body is missing or malformed.", body.get("detail").asText());
    }

    @Test
    void securityFilterErrorsUseTheSameJsonErrorShape() throws Exception {
        HttpResponse<String> missingToken = get("/api/users/me", null);
        JsonNode missingBody = objectMapper.readTree(missingToken.body());
        assertEquals(401, missingToken.statusCode());
        assertJsonError(missingToken);
        assertEquals(401, missingBody.get("status").asInt());
        assertEquals("/api/users/me", missingBody.get("instance").asText());

        HttpResponse<String> invalidToken = get("/api/users/me", "not-a-valid-jwt");
        JsonNode invalidBody = objectMapper.readTree(invalidToken.body());
        assertEquals(401, invalidToken.statusCode());
        assertJsonError(invalidToken);
        assertEquals("Invalid or expired JWT token.", invalidBody.get("detail").asText());
    }

    @Test
    void authorizationFailuresReturnStructuredForbidden() throws Exception {
        String username = "not_admin_" + UUID.randomUUID().toString().replace("-", "");
        String token = loginAndGetToken(username, "Password123!");
        AppUser user = userRepository.findByUsername(username).orElseThrow();
        user.setMainAdmin(false);
        userRepository.save(user);

        HttpResponse<String> response = post("/api/admin/users/123/ban", token, null);
        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(403, response.statusCode());
        assertJsonError(response);
        assertEquals(403, body.get("status").asInt());
        assertEquals("Access denied.", body.get("detail").asText());
    }

    @Test
    void notFoundResponsesHaveJsonBodiesForMissingRoutesAndResources() throws Exception {
        HttpResponse<String> missingRoute = get("/api/auth/does-not-exist", null);
        assertEquals(404, missingRoute.statusCode());
        assertJsonError(missingRoute);

        HttpRequest htmlAccept = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/auth/does-not-exist"))
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> missingRouteWithHtmlAccept = httpClient.send(htmlAccept,
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, missingRouteWithHtmlAccept.statusCode());
        assertJsonError(missingRouteWithHtmlAccept);

        String textAcceptToken = loginAndGetToken("text_accept_" + UUID.randomUUID().toString().replace("-", ""),
                "Password123!");
        HttpRequest textAccept = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/users/me"))
                .header("Authorization", "Bearer " + textAcceptToken)
                .header("Accept", "text/plain")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(textAccept, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() == 200 || response.statusCode() == 406, response.body());
        assertJsonContent(response);

        String token = loginAndGetToken("missing_resource_" + UUID.randomUUID().toString().replace("-", ""),
                "Password123!");
        HttpResponse<String> missingResource = get("/api/channels/9223372036854775000", token);
        assertEquals(404, missingResource.statusCode());
        assertJsonError(missingResource);
    }

    @Test
    void swaggerBasicAuthenticationFailuresAlsoReturnJson() throws Exception {
        HttpResponse<String> response = get("/swagger-ui/index.html", null);
        assertEquals(401, response.statusCode());
        assertJsonError(response);
    }

    @Test
    void invalidParametersAndResponseStatusFailuresReturnJson() throws Exception {
        String token = loginAndGetToken("bad_parameter_" + UUID.randomUUID().toString().replace("-", ""),
                "Password123!");

        HttpResponse<String> typeMismatch = get("/api/channels/1/messages?page=invalid", token);
        assertEquals(400, typeMismatch.statusCode());
        assertJsonError(typeMismatch);

        HttpResponse<String> invalidPage = get("/api/channels/1/messages?page=-1", token);
        assertEquals(400, invalidPage.statusCode());
        assertJsonError(invalidPage);

        HttpRequest wrongMethodRequest = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/users/me/display-name"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> wrongMethod = httpClient.send(wrongMethodRequest,
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, wrongMethod.statusCode());
        assertJsonError(wrongMethod);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void internalServerErrorUsesProblemJsonAndLogsOriginalStackTrace(CapturedOutput output) throws Exception {
        String token = loginAndGetToken("internal_error_" + UUID.randomUUID().toString().replace("-", ""),
                "Password123!");
        HttpResponse<String> response = get("/test-support/internal-error", token);
        JsonNode body = objectMapper.readTree(response.body());

        assertEquals(500, response.statusCode());
        assertEquals("application/problem+json", response.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("about:blank", body.get("type").asText());
        assertEquals("Internal Server Error", body.get("title").asText());
        assertEquals(500, body.get("status").asInt());
        assertEquals("Internal server error.", body.get("detail").asText());
        assertEquals("/test-support/internal-error", body.get("instance").asText());
        assertFalse(body.toString().contains("sensitive-test-cause"));
        assertTrue(output.getAll().contains("java.lang.RuntimeException: sensitive-test-cause"));
        assertTrue(output.getAll().contains("ExceptionProbeController"));
    }

    private void assertJsonError(HttpResponse<String> response) throws Exception {
        assertJsonContent(response);
        JsonNode body = objectMapper.readTree(response.body());
        assertNotNull(body);
        assertTrue(body.has("type"), body.toString());
        assertTrue(body.has("title"), body.toString());
        assertTrue(body.has("status"), body.toString());
        assertTrue(body.has("detail"), body.toString());
        assertTrue(body.has("instance"), body.toString());
    }

    private void assertJsonContent(HttpResponse<String> response) throws Exception {
        assertNotNull(response.headers().firstValue("Content-Type").orElse(null));
        String contentType = response.headers().firstValue("Content-Type").orElseThrow().toLowerCase();
        assertTrue(contentType.startsWith("application/json")
                || contentType.startsWith("application/problem+json"), contentType);
        assertNotNull(objectMapper.readTree(response.body()));
    }

    @RestController
    static class ExceptionProbeController {
        @GetMapping("/test-support/internal-error")
        void triggerInternalError() {
            throw new RuntimeException("sensitive-test-cause");
        }
    }
}

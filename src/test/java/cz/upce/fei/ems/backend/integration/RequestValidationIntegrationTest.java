package cz.upce.fei.ems.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import cz.upce.fei.ems.backend.domain.MessageServerType;
import cz.upce.fei.ems.backend.dto.CreateChannelRequestDto;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RequestValidationIntegrationTest extends BaseRestApiIntegrationTest {

    @Test
    void rejectsDuplicateQueryParametersWithJson400() throws Exception {
        HttpResponse<String> response = get("/api/channels/1/messages?page=1&page=999", null);

        assertJsonStatus(response, 400);
        assertEquals("Duplicate request parameters are not allowed.", json(response).get("detail").asText());
    }

    @Test
    void rejectsDuplicateSingletonHeadersWithJson400() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/auth/register"))
                .header("Content-Type", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertJsonStatus(response, 400);
        assertEquals("Duplicate request header is not allowed.", json(response).get("detail").asText());
    }

    @Test
    void rejectsUnsupportedContentTypeCharsetAndCompressionWithJson415() throws Exception {
        assertJsonStatus(postWithHeaders("/api/auth/register", "{}", "text/plain", null), 415);
        assertJsonStatus(postWithHeaders("/api/auth/register", "{}", "application/json; charset=UTF-16", null), 415);
        assertJsonStatus(postWithHeaders("/api/auth/register", "{}", "application/json", "gzip"), 415);
    }

    @Test
    void allowsUtf8JsonAndRejectsDuplicateJsonKeysAndScalarCoercion() throws Exception {
        String name = unique("valid_utf8");
        String valid = "{\"username\":\"" + name + "\",\"password\":\"Password123!\",\"publicKey\":\"key\"}";
        HttpResponse<String> accepted = postWithHeaders("/api/auth/register", valid,
                "application/json; charset=UTF-8", null);
        assertEquals(201, accepted.statusCode());

        String duplicateJson = "{\"username\":\"valid_json_" + unique("x")
                + "\",\"username\":\"overwrite\",\"password\":\"Password123!\",\"publicKey\":\"key\"}";
        assertJsonStatus(postWithHeaders("/api/auth/register", duplicateJson, "application/json", null), 400);
    }

    @Test
    void capsRequestTargetAndBodySize() throws Exception {
        HttpResponse<String> longUrl = get("/api/auth/missing?x=" + "a".repeat(4_100), null);
        assertJsonStatus(longUrl, 414);

        String oversizedJson = "{\"username\":\"" + unique("oversized")
                + "\",\"password\":\"Password123!\",\"publicKey\":\"" + "x".repeat(1_048_600) + "\"}";
        assertJsonStatus(postWithHeaders("/api/auth/register", oversizedJson, "application/json", null), 413);
    }

    @Test
    void capsTomcatHeaderCountAndCombinedRequestHeaderSize() throws Exception {
        StringBuilder tooManyHeaders = new StringBuilder("GET /api/auth/missing HTTP/1.1\r\nHost: localhost\r\n");
        for (int i = 0; i < 105; i++) tooManyHeaders.append("X-Test-").append(i).append(": a\r\n");
        tooManyHeaders.append("Connection: close\r\n\r\n");
        assertEquals(400, rawHttpStatus(tooManyHeaders.toString()));

        String tooLargeHeader = "GET /api/auth/missing HTTP/1.1\r\nHost: localhost\r\nX-Large: "
                + "a".repeat(17_000) + "\r\nConnection: close\r\n\r\n";
        assertTrue(rawHttpStatus(tooLargeHeader) >= 400);
    }

    @Test
    void rejectsInvalidIdsPaginationAndInvisibleUsernameCharacters() throws Exception {
        String token = loginAndGetToken(unique("validation"), "Password123!");
        assertJsonStatus(get("/api/channels/0", token), 400);
        assertJsonStatus(get("/api/channels/9223372036854775808", token), 400);
        assertJsonStatus(get("/api/channels/1/messages?page=10001", token), 400);
        assertJsonStatus(get("/api/channels/1/messages?size=101", token), 400);

        HttpResponse<String> serverResponse = post("/api/servers", token, createServer());
        long serverId = objectMapper.readTree(serverResponse.body()).get("id").asLong();
        HttpResponse<String> channelResponse = post("/api/servers/" + serverId + "/channels", token,
                createChannel());
        long channelId = objectMapper.readTree(channelResponse.body()).get("id").asLong();
        for (String userId : new String[]{"\"1\"", "{}", "10000000000000000000", "0", "1.5"}) {
            HttpResponse<String> invalidId = postWithHeaders("/api/channels/" + channelId + "/members",
                    "[{\"userId\":" + userId + ",\"ciphertext\":\"AQ==\"}]",
                    "application/json", null, token);
            assertEquals(400, invalidId.statusCode(), "userId JSON value: " + userId + "; response=" + invalidId.body());
        }
        HttpResponse<String> invalidCiphertext = postWithHeaders("/api/channels/" + channelId + "/members",
                "[{\"userId\":1,\"ciphertext\":\"not-base64!\"}]",
                "application/json", null, token);
        assertJsonStatus(invalidCiphertext, 400);

        String invalidUsername = "{\"username\":\"bad\\u202Ename\",\"password\":\"Password123!\",\"publicKey\":\"key\"}";
        assertJsonStatus(postWithHeaders("/api/auth/register", invalidUsername, "application/json", null), 400);
    }

    private HttpResponse<String> postWithHeaders(String path, String body, String contentType,
                                                String contentEncoding) throws Exception {
        return postWithHeaders(path, body, contentType, contentEncoding, null);
    }

    private HttpResponse<String> postWithHeaders(String path, String body, String contentType,
                                                String contentEncoding, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + path))
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (contentEncoding != null) request.header("Content-Encoding", contentEncoding);
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertJsonStatus(HttpResponse<String> response, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse("")
                .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE), response.body());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(expectedStatus, body.get("status").asInt());
        assertNotNull(body.get("detail"));
    }

    private JsonNode json(HttpResponse<String> response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private int rawHttpStatus(String request) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), 2_000);
            socket.setSoTimeout(3_000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            socket.shutdownOutput();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(response);
            String statusLine = response.toString(StandardCharsets.US_ASCII).lines().findFirst().orElse("");
            String[] parts = statusLine.split(" ");
            return parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        }
    }

    private String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private CreateServerRequestDto createServer() {
        CreateServerRequestDto request = new CreateServerRequestDto();
        request.setName(unique("validation_server"));
        request.setType(MessageServerType.ORGANIZATION);
        return request;
    }

    private CreateChannelRequestDto createChannel() {
        CreateChannelRequestDto request = new CreateChannelRequestDto();
        request.setName(unique("validation_channel"));
        return request;
    }
}

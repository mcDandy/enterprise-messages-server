package cz.upce.fei.ems.backend.exception;

import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonErrorControllerTest {
    private final JsonErrorController controller = new JsonErrorController(
            new ApiErrorWriter(org.mockito.Mockito.mock(tools.jackson.databind.ObjectMapper.class)));

    @Test
    void mapsServletContainerNotFoundToStructuredJsonError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/unknown");

        var response = controller.handleError(request);

        assertEquals(404, response.getStatusCode().value());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Resource not found.", response.getBody().getDetail());
        assertEquals("/unknown", response.getBody().getInstance().toString());
    }

    @Test
    void defaultsUnknownServletErrorsToSafeInternalErrorJson() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/failed");

        var response = controller.handleError(request);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Request failed.", response.getBody().getDetail());
        assertEquals("/failed", response.getBody().getInstance().toString());
    }
}

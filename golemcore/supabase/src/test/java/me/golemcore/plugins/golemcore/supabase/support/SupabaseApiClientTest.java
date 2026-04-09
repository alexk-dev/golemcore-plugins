package me.golemcore.plugins.golemcore.supabase.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.supabase.SupabasePluginConfig;
import me.golemcore.plugins.golemcore.supabase.SupabasePluginConfigService;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupabaseApiClientTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private SupabasePluginConfigService configService;
    private MockSupabaseApiClient client;
    private SupabasePluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(SupabasePluginConfigService.class);
        config = SupabasePluginConfig.builder()
                .enabled(true)
                .projectUrl("https://project.supabase.co")
                .apiKey("token")
                .defaultSchema("public")
                .build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
        client = new MockSupabaseApiClient(configService);
    }

    @Test
    void shouldBuildSelectRequestAndParseRows() {
        client.enqueueResponse(200, """
                [
                  {
                    "id": 1,
                    "name": "Alice"
                  }
                ]
                """);

        List<Map<String, Object>> rows = client.selectRows(
                "tasks",
                "public",
                "id,name",
                5,
                10,
                "id",
                Optional.of(false),
                Map.of("status", "eq.active"));

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("/rest/v1/tasks", request.url().encodedPath());
        assertEquals("id,name", request.url().queryParameter("select"));
        assertEquals("5", request.url().queryParameter("limit"));
        assertEquals("10", request.url().queryParameter("offset"));
        assertEquals("id.desc", request.url().queryParameter("order"));
        assertEquals("eq.active", request.url().queryParameter("status"));
        assertEquals("token", request.header("apikey"));
        assertEquals("Bearer token", request.header("Authorization"));
        assertEquals("public", request.header("Accept-Profile"));
        assertEquals(1, rows.size());
        assertEquals("Alice", rows.getFirst().get("name"));
    }

    @Test
    void shouldSendInsertRequestWithRepresentationPreference() throws Exception {
        client.enqueueResponse(201, """
                [
                  {
                    "id": 7,
                    "name": "Alice"
                  }
                ]
                """);

        List<Map<String, Object>> rows = client.insertRow("tasks", "public", Map.of("name", "Alice"));

        Request request = client.getCapturedRequests().getFirst();
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = objectMapper.readValue(readRequestBody(request), Map.class);
        assertEquals(1, rows.size());
        assertEquals(Map.of("name", "Alice"), body);
        assertEquals("public", request.header("Content-Profile"));
        assertEquals("return=representation", request.header("Prefer"));
    }

    @Test
    void shouldSurfaceApiErrorMessage() {
        client.enqueueResponse(400, """
                {
                  "message": "Bad request",
                  "details": "column missing"
                }
                """);

        SupabaseApiException exception = assertThrows(SupabaseApiException.class,
                () -> client.deleteRows("tasks", "public", Map.of("id", "eq.1")));

        assertEquals(400, exception.getStatusCode());
        assertEquals("Bad request | column missing", exception.getMessage());
    }

    private String readRequestBody(Request request) throws IOException {
        assertNotNull(request.body());
        try (Buffer buffer = new Buffer()) {
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class MockSupabaseApiClient extends SupabaseApiClient {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private MockSupabaseApiClient(SupabasePluginConfigService configService) {
            super(configService);
        }

        @Override
        protected Response executeRequest(Request request) {
            capturedRequests.add(request);
            PlannedResponse plannedResponse = plannedResponses.remove();
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(plannedResponse.code())
                    .message("mock")
                    .body(ResponseBody.create(plannedResponse.body(), APPLICATION_JSON))
                    .build();
        }

        private void enqueueResponse(int code, String body) {
            plannedResponses.add(new PlannedResponse(code, body));
        }

        private List<Request> getCapturedRequests() {
            return capturedRequests;
        }
    }

    private record PlannedResponse(int code, String body) {
    }
}

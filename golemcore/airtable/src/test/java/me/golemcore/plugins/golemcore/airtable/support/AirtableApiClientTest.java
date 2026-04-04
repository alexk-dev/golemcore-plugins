package me.golemcore.plugins.golemcore.airtable.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugins.golemcore.airtable.AirtablePluginConfig;
import me.golemcore.plugins.golemcore.airtable.AirtablePluginConfigService;
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
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirtableApiClientTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");

    private AirtablePluginConfigService configService;
    private MockAirtableApiClient client;
    private AirtablePluginConfig config;

    @BeforeEach
    void setUp() {
        configService = mock(AirtablePluginConfigService.class);
        config = AirtablePluginConfig.builder()
                .enabled(true)
                .apiToken("token")
                .baseId("appBase")
                .defaultTable("Tasks")
                .build();
        config.normalize();
        when(configService.getConfig()).thenReturn(config);
        client = new MockAirtableApiClient(configService);
    }

    @Test
    void shouldBuildListRequestAndParseRecords() {
        client.enqueueResponse(200, """
                {
                  "records": [
                    {
                      "id": "rec123",
                      "createdTime": "2026-04-04T00:00:00.000Z",
                      "fields": {
                        "Name": "Alice",
                        "Status": "Open"
                      }
                    }
                  ],
                  "offset": "itrNext"
                }
                """);

        AirtableApiClient.AirtableListResponse response = client.listRecords(
                "Tasks",
                "Grid",
                "Status='Open'",
                5,
                List.of("Name", "Status"),
                "Created",
                "desc");

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("/v0/appBase/Tasks", request.url().encodedPath());
        assertEquals("Grid", request.url().queryParameter("view"));
        assertEquals("Status='Open'", request.url().queryParameter("filterByFormula"));
        assertEquals("5", request.url().queryParameter("maxRecords"));
        assertEquals(List.of("Name", "Status"), request.url().queryParameterValues("fields[]"));
        assertEquals("Created", request.url().queryParameter("sort[0][field]"));
        assertEquals("desc", request.url().queryParameter("sort[0][direction]"));
        assertEquals("Bearer token", request.header("Authorization"));
        assertEquals("rec123", response.records().getFirst().id());
        assertEquals("Alice", response.records().getFirst().fields().get("Name"));
        assertEquals("itrNext", response.offset());
    }

    @Test
    void shouldSendWriteRequestWithTypecast() throws Exception {
        client.enqueueResponse(200, """
                {
                  "id": "rec999",
                  "fields": {
                    "Name": "Alice"
                  }
                }
                """);

        AirtableApiClient.AirtableRecord record = client.createRecord("Tasks", Map.of("Name", "Alice"), true);

        Request request = client.getCapturedRequests().getFirst();
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> body = objectMapper.readValue(readRequestBody(request), Map.class);
        assertEquals("rec999", record.id());
        assertEquals(Map.of("Name", "Alice"), body.get("fields"));
        assertEquals(true, body.get("typecast"));
    }

    @Test
    void shouldSurfaceApiErrorMessage() {
        client.enqueueResponse(422, """
                {
                  "error": {
                    "message": "Invalid request"
                  }
                }
                """);

        AirtableApiException exception = assertThrows(AirtableApiException.class,
                () -> client.getRecord("Tasks", "rec123"));

        assertEquals(422, exception.getStatusCode());
        assertEquals("Invalid request", exception.getMessage());
    }

    private String readRequestBody(Request request) throws IOException {
        assertNotNull(request.body());
        try (Buffer buffer = new Buffer()) {
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class MockAirtableApiClient extends AirtableApiClient {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private MockAirtableApiClient(AirtablePluginConfigService configService) {
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

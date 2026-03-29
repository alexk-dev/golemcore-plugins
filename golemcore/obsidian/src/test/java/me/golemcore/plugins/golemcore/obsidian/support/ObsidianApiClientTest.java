package me.golemcore.plugins.golemcore.obsidian.support;

import me.golemcore.plugins.golemcore.obsidian.ObsidianPluginConfig;
import me.golemcore.plugins.golemcore.obsidian.ObsidianPluginConfigService;
import me.golemcore.plugins.golemcore.obsidian.model.ObsidianSearchResult;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ObsidianApiClientTest {

    private static final MediaType APPLICATION_JSON = MediaType.get("application/json");
    private static final MediaType TEXT_MARKDOWN = MediaType.get("text/markdown; charset=utf-8");

    private ObsidianPluginConfigService configService;
    private MockObsidianApiClient client;

    @BeforeEach
    void setUp() {
        configService = mock(ObsidianPluginConfigService.class);
        ObsidianPluginConfig config = ObsidianPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://127.0.0.1:27124")
                .apiKey("api-key")
                .timeoutMs(30_000)
                .allowInsecureTls(false)
                .defaultSearchContextLength(100)
                .build();
        when(configService.getConfig()).thenReturn(config);
        client = new MockObsidianApiClient(configService);
    }

    @Test
    void shouldListRootDirectoryUsingVaultRootEndpoint() throws Exception {
        client.enqueueResponse(200, "{\"files\":[\"Inbox.md\",\"Projects/\"]}");

        List<String> files = client.listDirectory("");

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("GET", request.method());
        assertEquals("https://127.0.0.1:27124/vault/", request.url().toString());
        assertEquals("Bearer api-key", request.header("Authorization"));
        assertEquals(List.of("Inbox.md", "Projects/"), files);
    }

    @Test
    void shouldListNestedDirectoryUsingPathSafeVaultEndpoint() throws Exception {
        client.enqueueResponse(200, "{\"files\":[\"Meeting Notes.md\"]}");

        List<String> files = client.listDirectory("Projects/2026 Notes");

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("GET", request.method());
        assertEquals("https://127.0.0.1:27124/vault/Projects/2026%20Notes/", request.url().toString());
        assertEquals(List.of("Meeting Notes.md"), files);
    }

    @Test
    void shouldReadAndWriteFilesUsingPathSafeVaultEndpoints() throws Exception {
        client.enqueueResponse(200, """
                {
                  "content": "# Inbox\\nBody",
                  "frontmatter": {},
                  "path": "Inbox Notes.md",
                  "stat": {
                    "ctime": 1,
                    "mtime": 2,
                    "size": 11
                  },
                  "tags": []
                }
                """);
        client.enqueueResponse(204, null);

        String note = client.readNote("Inbox Notes.md");
        client.writeNote("Projects/2026 Notes.md", "Updated body");

        assertEquals("# Inbox\nBody", note);

        Request readRequest = client.getCapturedRequests().get(0);
        assertEquals("GET", readRequest.method());
        assertEquals("https://127.0.0.1:27124/vault/Inbox%20Notes.md", readRequest.url().toString());
        assertEquals("application/vnd.olrapi.note+json", readRequest.header("Accept"));

        Request writeRequest = client.getCapturedRequests().get(1);
        assertEquals("PUT", writeRequest.method());
        assertEquals("https://127.0.0.1:27124/vault/Projects/2026%20Notes.md", writeRequest.url().toString());
        assertEquals("text/markdown; charset=utf-8", writeRequest.header("Content-Type"));
        assertEquals("Updated body", readRequestBody(writeRequest));
    }

    @Test
    void shouldMapUnauthorizedResponsesToAuthFailures() {
        client.enqueueResponse(401, "{\"message\":\"unauthorized\"}");

        ObsidianApiException exception = assertThrows(ObsidianApiException.class,
                () -> client.readNote("Inbox.md"));

        assertEquals(401, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("unauthorized"));
    }

    @Test
    void shouldWrapTransportFailuresWithoutSyntheticHttpStatus() {
        client.enqueueFailure(new IOException("connect timed out"));

        ObsidianTransportException exception = assertThrows(ObsidianTransportException.class,
                () -> client.readNote("Inbox.md"));

        assertTrue(exception.getMessage().contains("connect timed out"));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void shouldSearchSimpleUsingTopLevelArrayAndNestedMatchContract() throws Exception {
        client.enqueueResponse(200, """
                [
                  {
                    "filename": "Inbox.md",
                    "score": 0.98,
                    "matches": [
                      {
                        "context": "Daily review notes",
                        "match": {
                          "start": 6,
                          "end": 12,
                          "source": "content"
                        }
                      }
                    ]
                  }
                ]
                """);

        List<ObsidianSearchResult> results = client.simpleSearch("daily review", 42);

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("POST", request.method());
        assertEquals("https://127.0.0.1:27124/search/simple/?query=daily%20review&contextLength=42",
                request.url().toString());
        assertEquals(List.of("Inbox.md"), results.stream().map(ObsidianSearchResult::getFilename).toList());
        assertEquals(0.98, results.getFirst().getScore());
        assertEquals("Daily review notes", results.getFirst().getMatches().getFirst().getContext());
        assertEquals(6, results.getFirst().getMatches().getFirst().getMatch().getStart());
        assertEquals(12, results.getFirst().getMatches().getFirst().getMatch().getEnd());
        assertEquals("content", results.getFirst().getMatches().getFirst().getMatch().getSource());
    }

    @Test
    void shouldFailFastWhenSuccessfulListResponseHasWrongShape() {
        client.enqueueResponse(200, "{\"files\":null}");

        ObsidianApiException exception = assertThrows(ObsidianApiException.class,
                () -> client.listDirectory(""));

        assertEquals(200, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Invalid"));
    }

    @Test
    void shouldFailFastWhenSuccessfulSearchResponseHasWrongShape() {
        client.enqueueResponse(200, "{\"results\":[]}");

        ObsidianApiException exception = assertThrows(ObsidianApiException.class,
                () -> client.simpleSearch("daily review", 42));

        assertEquals(200, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("array"));
    }

    private String readRequestBody(Request request) throws IOException {
        RequestBody body = request.body();
        if (body == null) {
            return "";
        }
        try (Buffer buffer = new Buffer()) {
            body.writeTo(buffer);
            return buffer.readUtf8();
        }
    }

    private static final class MockObsidianApiClient extends ObsidianApiClient {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private MockObsidianApiClient(ObsidianPluginConfigService configService) {
            super(configService);
        }

        @Override
        protected Response executeRequest(Request request) throws IOException {
            capturedRequests.add(request);
            PlannedResponse plannedResponse = plannedResponses.remove();
            if (plannedResponse.failure() != null) {
                throw plannedResponse.failure();
            }
            ResponseBody responseBody = ResponseBody.create(
                    plannedResponse.body() == null ? "" : plannedResponse.body(),
                    plannedResponse.mediaType());
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(plannedResponse.code())
                    .message("mock")
                    .body(responseBody)
                    .build();
        }

        private void enqueueResponse(int code, String body) {
            plannedResponses.add(new PlannedResponse(code, body, APPLICATION_JSON, null));
        }

        private void enqueueFailure(IOException failure) {
            plannedResponses.add(new PlannedResponse(0, null, null, failure));
        }

        private List<Request> getCapturedRequests() {
            return capturedRequests;
        }
    }

    private record PlannedResponse(int code, String body, MediaType mediaType, IOException failure) {
    }
}

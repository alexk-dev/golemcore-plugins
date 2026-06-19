package me.golemcore.plugins.golemcore.nextcloud.support;

import me.golemcore.plugins.golemcore.nextcloud.NextcloudPluginConfig;
import me.golemcore.plugins.golemcore.nextcloud.NextcloudPluginConfigService;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
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

class NextcloudWebDavClientTest {

    private static final MediaType XML = MediaType.get("application/xml; charset=utf-8");
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");

    private NextcloudPluginConfigService configService;
    private MockNextcloudWebDavClient client;

    @BeforeEach
    void setUp() {
        configService = mock(NextcloudPluginConfigService.class);
        NextcloudPluginConfig config = NextcloudPluginConfig.builder()
                .enabled(true)
                .baseUrl("https://cloud.example.com")
                .username("alex")
                .appPassword("secret")
                .rootPath("AI")
                .timeoutMs(30_000)
                .allowInsecureTls(false)
                .build();
        when(configService.getConfig()).thenReturn(config);
        client = new MockNextcloudWebDavClient(configService);
    }

    @Test
    void shouldListDirectoryUsingPropfindAndParseDavXml() {
        client.enqueueResponse(207, """
                <?xml version=\"1.0\"?>
                <d:multistatus xmlns:d=\"DAV:\">
                  <d:response>
                    <d:href>/remote.php/dav/files/alex/AI/docs/</d:href>
                    <d:propstat>
                      <d:status>HTTP/1.1 200 OK</d:status>
                      <d:prop>
                        <d:resourcetype><d:collection/></d:resourcetype>
                      </d:prop>
                    </d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/remote.php/dav/files/alex/AI/docs/readme.md</d:href>
                    <d:propstat>
                      <d:status>HTTP/1.1 200 OK</d:status>
                      <d:prop>
                        <d:resourcetype/>
                        <d:getcontentlength>5</d:getcontentlength>
                        <d:getcontenttype>text/markdown</d:getcontenttype>
                      </d:prop>
                    </d:propstat>
                  </d:response>
                </d:multistatus>
                """, XML);

        List<NextcloudResource> entries = client.listDirectory("docs");

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("PROPFIND", request.method());
        assertEquals("1", request.header("Depth"));
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/docs/", request.url().toString());
        assertEquals(1, entries.size());
        assertEquals("docs/readme.md", entries.getFirst().path());
        assertEquals("readme.md", entries.getFirst().name());
        assertEquals(false, entries.getFirst().directory());
        assertEquals(5L, entries.getFirst().size());
    }

    @Test
    void shouldReadFileAndPreserveMetadata() {
        client.enqueueResponse(200, "hello", OCTET_STREAM, "text/plain; charset=utf-8", "etag",
                "Mon, 01 Jan 2026 00:00:00 GMT");

        NextcloudFileContent content = client.readFile("docs/readme.md");

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("GET", request.method());
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/docs/readme.md", request.url().toString());
        assertEquals("docs/readme.md", content.path());
        assertEquals("text/plain; charset=utf-8", content.mimeType());
        assertEquals("etag", content.etag());
        assertEquals("Mon, 01 Jan 2026 00:00:00 GMT", content.lastModified());
        assertEquals("hello", new String(content.bytes()));
    }

    @Test
    void shouldWriteBinaryFileUsingPut() {
        client.enqueueResponse(201, "", OCTET_STREAM);

        client.writeFile("docs/archive.zip", new byte[] { 1, 2, 3 });

        Request request = client.getCapturedRequests().getFirst();
        assertEquals("PUT", request.method());
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/docs/archive.zip",
                request.url().toString());
        assertEquals("application/zip", request.header("Content-Type"));
    }

    @Test
    void shouldCreateIntermediateDirectoriesWithMkcol() {
        client.enqueueResponse(201, "", OCTET_STREAM);
        client.enqueueResponse(405, "", OCTET_STREAM);

        client.createDirectoryRecursive("docs/archive");

        assertEquals(2, client.getCapturedRequests().size());
        assertEquals("MKCOL", client.getCapturedRequests().get(0).method());
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/docs/",
                client.getCapturedRequests().get(0).url().toString());
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/docs/archive/",
                client.getCapturedRequests().get(1).url().toString());
    }

    @Test
    void shouldSendDestinationHeaderForMoveAndCopy() {
        client.enqueueResponse(201, "", OCTET_STREAM);
        client.move("docs/a.txt", "archive/a.txt");
        Request moveRequest = client.getCapturedRequests().getFirst();
        assertEquals("MOVE", moveRequest.method());
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/archive/a.txt",
                moveRequest.header("Destination"));

        client.clear();
        client.enqueueResponse(201, "", OCTET_STREAM);
        client.copy("docs/a.txt", "archive/a.txt");
        Request copyRequest = client.getCapturedRequests().getFirst();
        assertEquals("COPY", copyRequest.method());
        assertEquals("https://cloud.example.com/remote.php/dav/files/alex/AI/archive/a.txt",
                copyRequest.header("Destination"));
    }

    @Test
    void shouldMapHttpErrorsToApiException() {
        client.enqueueResponse(404, "missing", OCTET_STREAM);

        NextcloudApiException exception = assertThrows(NextcloudApiException.class,
                () -> client.readFile("docs/readme.md"));

        assertEquals(404, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("missing"));
    }

    @Test
    void shouldWrapTransportFailures() {
        client.enqueueFailure(new IOException("timeout"));

        NextcloudTransportException exception = assertThrows(NextcloudTransportException.class,
                () -> client.readFile("docs/readme.md"));

        assertTrue(exception.getMessage().contains("timeout"));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void shouldFailWhenDavXmlIsMalformed() {
        client.enqueueResponse(207, "<not-xml", XML);

        NextcloudApiException exception = assertThrows(NextcloudApiException.class,
                () -> client.listDirectory("docs"));

        assertEquals(500, exception.getStatusCode());
        assertTrue(exception.getMessage().contains("Invalid WebDAV XML"));
    }

    private static final class MockNextcloudWebDavClient extends NextcloudWebDavClient {

        private final Queue<PlannedResponse> plannedResponses = new ArrayDeque<>();
        private final List<Request> capturedRequests = new ArrayList<>();

        private MockNextcloudWebDavClient(NextcloudPluginConfigService configService) {
            super(configService);
        }

        @Override
        @SuppressWarnings("PMD.CloseResource")
        protected Response executeRequest(Request request) throws IOException {
            capturedRequests.add(request);
            PlannedResponse planned = plannedResponses.remove();
            if (planned.failure() != null) {
                throw planned.failure();
            }
            ResponseBody responseBody = ResponseBody.create(planned.body(), planned.mediaType());
            Response.Builder builder = new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(planned.code())
                    .message("mock")
                    .body(responseBody);
            if (planned.contentType() != null) {
                builder.header("Content-Type", planned.contentType());
            }
            if (planned.etag() != null) {
                builder.header("ETag", planned.etag());
            }
            if (planned.lastModified() != null) {
                builder.header("Last-Modified", planned.lastModified());
            }
            return builder.build();
        }

        private void enqueueResponse(int code, String body, MediaType mediaType) {
            plannedResponses.add(new PlannedResponse(code, body, mediaType, null, null, null, null));
        }

        private void enqueueResponse(int code, String body, MediaType mediaType, String contentType, String etag,
                String lastModified) {
            plannedResponses.add(new PlannedResponse(code, body, mediaType, contentType, etag, lastModified, null));
        }

        private void enqueueFailure(IOException failure) {
            plannedResponses.add(new PlannedResponse(0, "", OCTET_STREAM, null, null, null, failure));
        }

        private List<Request> getCapturedRequests() {
            return capturedRequests;
        }

        private void clear() {
            capturedRequests.clear();
            plannedResponses.clear();
        }
    }

    private record PlannedResponse(
            int code,
            String body,
            MediaType mediaType,
            String contentType,
            String etag,
            String lastModified,
            IOException failure) {
    }
}

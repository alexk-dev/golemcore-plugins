package me.golemcore.bot.testsupport.http;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class OkHttpMockEngine implements Interceptor {

    private static final String DEFAULT_CONTENT_TYPE = "application/json";

    private final Queue<PlannedResult> plannedResults = new ConcurrentLinkedQueue<>();
    private final Queue<CapturedRequest> capturedRequests = new ConcurrentLinkedQueue<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    public void enqueueJson(int code, String body) {
        enqueue(code, body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0], DEFAULT_CONTENT_TYPE);
    }

    public CapturedRequest takeRequest() {
        return capturedRequests.poll();
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String requestBody = readRequestBody(request);
        capturedRequests.add(new CapturedRequest(request, requestBody));
        requestCount.incrementAndGet();

        PlannedResult plannedResult = plannedResults.poll();
        if (plannedResult == null) {
            throw new IOException("No planned response for request: " + request.method() + " " + request.url());
        }

        MediaType mediaType = plannedResult.contentType() != null
                ? MediaType.parse(plannedResult.contentType())
                : null;
        ResponseBody responseBody = ResponseBody.create(plannedResult.body(), mediaType);
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(plannedResult.code())
                .message("mock")
                .headers(Headers.of())
                .body(responseBody)
                .build();
    }

    private void enqueue(int code, byte[] body, String contentType) {
        plannedResults.add(new PlannedResult(code, body, contentType));
    }

    private String readRequestBody(Request request) throws IOException {
        RequestBody requestBody = request.body();
        if (requestBody == null) {
            return "";
        }
        try (Buffer buffer = new Buffer()) {
            requestBody.writeTo(buffer);
            return buffer.readString(StandardCharsets.UTF_8);
        }
    }

    private record PlannedResult(int code, byte[] body, String contentType) {
    }

    public static final class CapturedRequest {
        private final Request request;
        private final String requestBody;

        private CapturedRequest(Request request, String requestBody) {
            this.request = request;
            this.requestBody = requestBody;
        }

        public String method() {
            return request.method();
        }

        public String target() {
            String encodedPath = request.url().encodedPath();
            String encodedQuery = request.url().encodedQuery();
            if (encodedQuery == null || encodedQuery.isBlank()) {
                return encodedPath;
            }
            return encodedPath + "?" + encodedQuery;
        }

        public String header(String name) {
            return request.header(name);
        }

        public String body() {
            return requestBody;
        }
    }
}

package me.golemcore.plugins.golemcore.nextcloud.support;

import me.golemcore.plugins.golemcore.nextcloud.NextcloudPluginConfig;
import me.golemcore.plugins.golemcore.nextcloud.NextcloudPluginConfigService;
import okhttp3.Credentials;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class NextcloudWebDavClient {

    private static final String REMOTE_PHP_SEGMENT = "remote.php";
    private static final String DAV_SEGMENT = "dav";
    private static final String FILES_SEGMENT = "files";
    private static final MediaType APPLICATION_XML = MediaType.get("application/xml; charset=utf-8");
    private static final MediaType TEXT_PLAIN = MediaType.get("text/plain; charset=utf-8");
    private static final String PROPFIND_BODY = """
            <?xml version=\"1.0\" encoding=\"UTF-8\"?>
            <d:propfind xmlns:d=\"DAV:\">
              <d:prop>
                <d:resourcetype/>
                <d:getcontentlength/>
                <d:getcontenttype/>
                <d:getetag/>
                <d:getlastmodified/>
              </d:prop>
            </d:propfind>
            """;

    private final NextcloudPluginConfigService configService;

    public NextcloudWebDavClient(NextcloudPluginConfigService configService) {
        this.configService = configService;
    }

    public List<NextcloudResource> listDirectory(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        List<NextcloudResource> resources = propfind(normalizedPath, 1, true);
        List<NextcloudResource> children = new ArrayList<>();
        for (NextcloudResource resource : resources) {
            if (!normalizedPath.equals(resource.path())) {
                children.add(resource);
            }
        }
        return List.copyOf(children);
    }

    public NextcloudResource fileInfo(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        List<NextcloudResource> resources = propfind(normalizedPath, 0, normalizedPath.isBlank());
        for (NextcloudResource resource : resources) {
            if (normalizedPath.equals(resource.path())) {
                return resource;
            }
        }
        if (!resources.isEmpty()) {
            return resources.getFirst();
        }
        throw new NextcloudApiException(404, "Path not found: " + displayPath(normalizedPath));
    }

    public NextcloudFileContent readFile(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        Request request = new Request.Builder()
                .url(buildResourceUrl(normalizedPath, false))
                .header("Authorization", authorizationHeader())
                .get()
                .build();

        try (Response response = openResponse(request)) {
            int statusCode = response.code();
            String mimeType = firstText(response.header("Content-Type"), detectMimeType(normalizedPath));
            String etag = response.header("ETag");
            String lastModified = response.header("Last-Modified");
            byte[] bytes = readResponseBytes(response);
            ensureSuccessful(statusCode, bytes);
            Long size = (long) bytes.length;
            return new NextcloudFileContent(normalizedPath, bytes, mimeType, size, etag, lastModified);
        }
    }

    public void writeFile(String relativePath, byte[] bytes) {
        String normalizedPath = normalizeRelativePath(relativePath);
        String mimeType = detectMimeType(normalizedPath);
        Request request = new Request.Builder()
                .url(buildResourceUrl(normalizedPath, false))
                .header("Authorization", authorizationHeader())
                .header("Content-Type", mimeType)
                .put(RequestBody.create(bytes, MediaType.get(mimeType)))
                .build();

        executeVoid(request);
    }

    public void createDirectoryRecursive(String relativeDirectoryPath) {
        String normalizedPath = normalizeRelativePath(relativeDirectoryPath);
        if (normalizedPath.isBlank()) {
            return;
        }
        StringBuilder currentPath = new StringBuilder();
        for (String segment : splitPath(normalizedPath)) {
            if (!currentPath.isEmpty()) {
                currentPath.append('/');
            }
            currentPath.append(segment);
            Request request = new Request.Builder()
                    .url(buildResourceUrl(currentPath.toString(), true))
                    .header("Authorization", authorizationHeader())
                    .method("MKCOL", RequestBody.create("", TEXT_PLAIN))
                    .build();
            try (Response response = openResponse(request)) {
                int statusCode = response.code();
                byte[] body = readResponseBytes(response);
                if (statusCode == 201 || statusCode == 405) {
                    continue;
                }
                ensureSuccessful(statusCode, body);
            }
        }
    }

    public void delete(String relativePath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        Request request = new Request.Builder()
                .url(buildResourceUrl(normalizedPath, false))
                .header("Authorization", authorizationHeader())
                .delete()
                .build();
        executeVoid(request);
    }

    public void move(String relativePath, String targetPath) {
        executeCopyOrMove("MOVE", relativePath, targetPath);
    }

    public void copy(String relativePath, String targetPath) {
        executeCopyOrMove("COPY", relativePath, targetPath);
    }

    protected Response executeRequest(Request request) throws IOException {
        return buildHttpClient(getConfig()).newCall(request).execute();
    }

    private List<NextcloudResource> propfind(String relativePath, int depth, boolean directoryUrl) {
        Request request = new Request.Builder()
                .url(buildResourceUrl(relativePath, directoryUrl))
                .header("Authorization", authorizationHeader())
                .header("Depth", String.valueOf(depth))
                .method("PROPFIND", RequestBody.create(PROPFIND_BODY, APPLICATION_XML))
                .build();

        try (Response response = openResponse(request)) {
            int statusCode = response.code();
            byte[] body = readResponseBytes(response);
            ensureSuccessful(statusCode, body);
            if (body.length == 0) {
                return List.of();
            }
            return parseResources(new String(body, StandardCharsets.UTF_8));
        }
    }

    private void executeCopyOrMove(String method, String relativePath, String targetPath) {
        String normalizedPath = normalizeRelativePath(relativePath);
        String normalizedTargetPath = normalizeRelativePath(targetPath);
        Request request = new Request.Builder()
                .url(buildResourceUrl(normalizedPath, false))
                .header("Authorization", authorizationHeader())
                .header("Destination", buildResourceUrl(normalizedTargetPath, false).toString())
                .header("Overwrite", "F")
                .method(method, RequestBody.create("", TEXT_PLAIN))
                .build();
        executeVoid(request);
    }

    private void executeVoid(Request request) {
        try (Response response = openResponse(request)) {
            int statusCode = response.code();
            byte[] body = readResponseBytes(response);
            ensureSuccessful(statusCode, body);
        }
    }

    private Response openResponse(Request request) {
        try {
            return executeRequest(request);
        } catch (IOException ex) {
            throw new NextcloudTransportException(transportMessage(ex), ex);
        }
    }

    private byte[] readResponseBytes(Response response) {
        try (ResponseBody body = response.body()) {
            return body != null ? body.bytes() : new byte[0];
        } catch (IOException ex) {
            throw new NextcloudTransportException(transportMessage(ex), ex);
        }
    }

    private List<NextcloudResource> parseResources(String xmlBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();
            Document document = documentBuilder.parse(new InputSource(new StringReader(xmlBody)));
            NodeList responseNodes = document.getElementsByTagNameNS("*", "response");
            List<NextcloudResource> resources = new ArrayList<>(responseNodes.getLength());
            for (int index = 0; index < responseNodes.getLength(); index++) {
                Element response = (Element) responseNodes.item(index);
                String href = textOfFirstChild(response, "href");
                Element prop = findSuccessfulProp(response);
                if (!hasText(href) || prop == null) {
                    continue;
                }
                String path = extractRelativePath(href);
                boolean directory = isDirectory(prop);
                String name = path.isBlank() ? displayNameForRoot() : fileName(path);
                Long size = parseLong(textOfFirstChild(prop, "getcontentlength"));
                String mimeType = textOfFirstChild(prop, "getcontenttype");
                String etag = textOfFirstChild(prop, "getetag");
                String lastModified = textOfFirstChild(prop, "getlastmodified");
                resources.add(new NextcloudResource(path, name, directory, size, mimeType, etag, lastModified));
            }
            return List.copyOf(resources);
        } catch (ParserConfigurationException | IOException | SAXException ex) {
            throw new NextcloudApiException(500, "Invalid WebDAV XML response: " + ex.getMessage());
        }
    }

    private Element findSuccessfulProp(Element response) {
        NodeList propstatNodes = response.getElementsByTagNameNS("*", "propstat");
        for (int index = 0; index < propstatNodes.getLength(); index++) {
            Element propstat = (Element) propstatNodes.item(index);
            String status = textOfFirstChild(propstat, "status");
            if (status != null && status.contains(" 200 ")) {
                NodeList propNodes = propstat.getElementsByTagNameNS("*", "prop");
                if (propNodes.getLength() > 0) {
                    return (Element) propNodes.item(0);
                }
            }
        }
        return null;
    }

    private boolean isDirectory(Element prop) {
        NodeList resourceTypes = prop.getElementsByTagNameNS("*", "resourcetype");
        if (resourceTypes.getLength() == 0) {
            return false;
        }
        Element resourceType = (Element) resourceTypes.item(0);
        return resourceType.getElementsByTagNameNS("*", "collection").getLength() > 0;
    }

    private String textOfFirstChild(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String textContent = nodes.item(0).getTextContent();
        return hasText(textContent) ? textContent.trim() : null;
    }

    private void ensureSuccessful(int statusCode, byte[] body) {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        String responseBody = new String(body, StandardCharsets.UTF_8).trim();
        String message = hasText(responseBody) ? responseBody : "HTTP " + statusCode;
        throw new NextcloudApiException(statusCode, message);
    }

    private String extractRelativePath(String href) {
        URI uri = URI.create(href);
        String rawPath = firstText(uri.getRawPath(), href);
        String prefixWithSlash = buildResourceUrl("", true).encodedPath();
        String prefixWithoutSlash = buildResourceUrl("", false).encodedPath();
        String rawRelativePath;
        if (rawPath.equals(prefixWithoutSlash) || rawPath.equals(prefixWithSlash)) {
            return "";
        }
        if (rawPath.startsWith(prefixWithSlash)) {
            rawRelativePath = rawPath.substring(prefixWithSlash.length());
        } else if (rawPath.startsWith(prefixWithoutSlash + "/")) {
            rawRelativePath = rawPath.substring(prefixWithoutSlash.length() + 1);
        } else if (rawPath.startsWith(prefixWithoutSlash)) {
            rawRelativePath = rawPath.substring(prefixWithoutSlash.length());
        } else {
            throw new NextcloudApiException(500, "Unexpected WebDAV href: " + href);
        }
        while (rawRelativePath.startsWith("/")) {
            rawRelativePath = rawRelativePath.substring(1);
        }
        while (rawRelativePath.endsWith("/")) {
            rawRelativePath = rawRelativePath.substring(0, rawRelativePath.length() - 1);
        }
        return decodePath(rawRelativePath);
    }

    private String decodePath(String rawRelativePath) {
        if (!hasText(rawRelativePath)) {
            return "";
        }
        List<String> decodedSegments = new ArrayList<>();
        for (String segment : rawRelativePath.split("/")) {
            if (!segment.isBlank()) {
                decodedSegments.add(URLDecoder.decode(segment, StandardCharsets.UTF_8));
            }
        }
        return String.join("/", decodedSegments);
    }

    private OkHttpClient buildHttpClient(NextcloudPluginConfig config) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .callTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .readTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .writeTimeout(Duration.ofMillis(config.getTimeoutMs()));
        HttpUrl baseUrl = parseBaseUrl(config.getBaseUrl());
        if (Boolean.TRUE.equals(config.getAllowInsecureTls()) && "https".equalsIgnoreCase(baseUrl.scheme())) {
            try {
                TrustManager[] trustManagers = new TrustManager[] { new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                } };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagers, new SecureRandom());
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
                HostnameVerifier hostnameVerifier = (hostname, session) -> true;
                builder.sslSocketFactory(sslSocketFactory, trustManager);
                builder.hostnameVerifier(hostnameVerifier);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException("Unable to configure insecure TLS for Nextcloud", ex);
            }
        }
        return builder.build();
    }

    private HttpUrl buildResourceUrl(String relativePath, boolean directory) {
        NextcloudPluginConfig config = getConfig();
        HttpUrl baseUrl = parseBaseUrl(config.getBaseUrl());
        HttpUrl.Builder builder = Objects.requireNonNull(baseUrl).newBuilder();
        builder.addPathSegment(REMOTE_PHP_SEGMENT);
        builder.addPathSegment(DAV_SEGMENT);
        builder.addPathSegment(FILES_SEGMENT);
        builder.addPathSegment(config.getUsername());
        for (String segment : splitPath(config.getRootPath())) {
            builder.addPathSegment(segment);
        }
        for (String segment : splitPath(relativePath)) {
            builder.addPathSegment(segment);
        }
        if (directory) {
            builder.addPathSegment("");
        }
        return builder.build();
    }

    private HttpUrl parseBaseUrl(String baseUrl) {
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalStateException("Invalid Nextcloud base URL: " + baseUrl);
        }
        return parsed;
    }

    private List<String> splitPath(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        String normalized = value;
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!hasText(normalized)) {
            return List.of();
        }
        String[] rawSegments = normalized.split("/");
        List<String> segments = new ArrayList<>(rawSegments.length);
        for (String segment : rawSegments) {
            if (hasText(segment)) {
                segments.add(segment);
            }
        }
        return List.copyOf(segments);
    }

    private NextcloudPluginConfig getConfig() {
        return configService.getConfig();
    }

    private String authorizationHeader() {
        NextcloudPluginConfig config = getConfig();
        return Credentials.basic(config.getUsername(), config.getAppPassword(), StandardCharsets.UTF_8);
    }

    private String transportMessage(IOException ex) {
        String message = ex.getMessage();
        return hasText(message) ? "Nextcloud transport failed: " + message : "Nextcloud transport failed";
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String displayPath(String path) {
        return hasText(path) ? path : "/";
    }

    private String displayNameForRoot() {
        String rootPath = getConfig().getRootPath();
        if (!hasText(rootPath)) {
            return "/";
        }
        int separatorIndex = rootPath.lastIndexOf('/');
        return separatorIndex >= 0 ? rootPath.substring(separatorIndex + 1) : rootPath;
    }

    private String fileName(String path) {
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex >= 0 ? path.substring(separatorIndex + 1) : path;
    }

    private String detectMimeType(String path) {
        String lowerPath = path.toLowerCase(Locale.ROOT);
        if (lowerPath.endsWith(".txt")) {
            return "text/plain";
        }
        if (lowerPath.endsWith(".md") || lowerPath.endsWith(".markdown")) {
            return "text/markdown";
        }
        if (lowerPath.endsWith(".json")) {
            return "application/json";
        }
        if (lowerPath.endsWith(".xml")) {
            return "application/xml";
        }
        if (lowerPath.endsWith(".yaml") || lowerPath.endsWith(".yml")) {
            return "application/yaml";
        }
        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerPath.endsWith(".gif")) {
            return "image/gif";
        }
        if (lowerPath.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerPath.endsWith(".zip")) {
            return "application/zip";
        }
        return "application/octet-stream";
    }

    private String normalizeRelativePath(String value) {
        return value == null ? "" : value;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

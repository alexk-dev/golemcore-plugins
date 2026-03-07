package me.golemcore.plugins.golemcore.browser.adapter.outbound;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import me.golemcore.plugins.golemcore.browser.BrowserPluginConfig;
import me.golemcore.plugins.golemcore.browser.BrowserPluginConfigService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class PlaywrightBrowserClient {

    private final BrowserPluginConfigService configService;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private BrowserSessionSettings sessionSettings;

    public PlaywrightBrowserClient(BrowserPluginConfigService configService) {
        this.configService = configService;
    }

    @SuppressWarnings("PMD.CloseResource")
    private synchronized void ensureInitialized() {
        BrowserPluginConfig config = configService.getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }

        BrowserSessionSettings desired = new BrowserSessionSettings(
                Boolean.TRUE.equals(config.getHeadless()),
                config.getUserAgent());

        if (browser != null && browser.isConnected() && desired.equals(sessionSettings)) {
            return;
        }

        close();

        Playwright pw = Playwright.create();
        Browser br = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(desired.headless()));
        Browser.NewContextOptions options = new Browser.NewContextOptions();
        if (desired.userAgent() != null && !desired.userAgent().isBlank()) {
            options.setUserAgent(desired.userAgent());
        }

        this.context = br.newContext(options);
        this.browser = br;
        this.playwright = pw;
        this.sessionSettings = desired;
    }

    @PreDestroy
    public synchronized void close() {
        try {
            if (context != null) {
                context.close();
            }
        } catch (Exception ignored) {
            // best-effort cleanup
        }
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (Exception ignored) {
            // best-effort cleanup
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }

    public CompletableFuture<BrowserPageData> navigate(String url) {
        return CompletableFuture.supplyAsync(() -> {
            ensureInitialized();
            if (!isAvailable()) {
                throw new IllegalStateException("Browser plugin is disabled or unavailable");
            }

            try (Page page = context.newPage()) {
                int timeout = configService.getConfig().getTimeoutMs();
                page.setDefaultTimeout(timeout);
                page.navigate(url);
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);
                return new BrowserPageData(
                        page.url(),
                        page.title(),
                        page.content(),
                        extractText(page));
            }
        });
    }

    public CompletableFuture<String> getHtml(String url) {
        return navigate(url).thenApply(BrowserPageData::html);
    }

    public CompletableFuture<byte[]> screenshot(String url) {
        return CompletableFuture.supplyAsync(() -> {
            ensureInitialized();
            if (!isAvailable()) {
                throw new IllegalStateException("Browser plugin is disabled or unavailable");
            }

            try (Page page = context.newPage()) {
                int timeout = configService.getConfig().getTimeoutMs();
                page.setDefaultTimeout(timeout);
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE);
                return page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
            }
        });
    }

    public synchronized boolean isAvailable() {
        BrowserPluginConfig config = configService.getConfig();
        return Boolean.TRUE.equals(config.getEnabled()) && browser != null && browser.isConnected();
    }

    private String extractText(Page page) {
        try {
            Object result = page.evaluate("""
                    (() => {
                        const clone = document.body.cloneNode(true);
                        const scripts = clone.querySelectorAll('script, style, noscript');
                        scripts.forEach(el => el.remove());
                        return clone.innerText;
                    })()
                    """);
            return result != null ? result.toString() : "";
        } catch (RuntimeException e) {
            return page.textContent("body");
        }
    }

    public record BrowserPageData(String url, String title, String html, String text) {
    }

    private record BrowserSessionSettings(boolean headless, String userAgent) {
        @Override
        public boolean equals(Object other) {
            if (!(other instanceof BrowserSessionSettings that)) {
                return false;
            }
            return headless == that.headless && Objects.equals(userAgent, that.userAgent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(headless, userAgent);
        }
    }
}

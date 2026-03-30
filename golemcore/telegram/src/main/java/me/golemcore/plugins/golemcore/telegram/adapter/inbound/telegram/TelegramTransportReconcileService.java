package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.plugin.api.extension.model.TelegramRestartEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramTransportReconcileService {

    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean reconcileScheduled = new AtomicBoolean(false);

    public void requestReconcile() {
        if (!reconcileScheduled.compareAndSet(false, true)) {
            log.debug("Telegram transport reconcile already scheduled");
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                eventPublisher.publishEvent(new TelegramRestartEvent());
            } catch (Exception e) { // NOSONAR
                log.error("Failed to reconcile Telegram transport", e);
            } finally {
                reconcileScheduled.set(false);
            }
        });
    }
}

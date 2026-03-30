package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.plugin.api.extension.model.Message;
import me.golemcore.plugins.golemcore.telegram.support.TelegramMetadataKeys;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@Slf4j
public class TelegramInboundAssembler {

    private final ScheduledExecutorService scheduler;
    private final Map<String, PendingBatch> pendingBatches = new ConcurrentHashMap<>();

    public TelegramInboundAssembler() {
        this(Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "telegram-inbound-assembler");
            thread.setDaemon(true);
            return thread;
        }));
    }

    TelegramInboundAssembler(ScheduledExecutorService scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public void submit(TelegramInboundFragment fragment, int delayMs, Consumer<Message> onFlush) {
        if (fragment == null || fragment.aggregationKey() == null || onFlush == null) {
            return;
        }

        PendingBatch batch = pendingBatches.computeIfAbsent(fragment.aggregationKey(), ignored -> new PendingBatch());
        synchronized (batch) {
            batch.fragments.add(fragment);
            batch.onFlush = onFlush;
            if (batch.future != null) {
                batch.future.cancel(false);
            }
            batch.future = scheduler.schedule(() -> flush(fragment.aggregationKey()), Math.max(delayMs, 0L),
                    TimeUnit.MILLISECONDS);
        }
    }

    public void shutdown() {
        pendingBatches.clear();
        scheduler.shutdownNow();
    }

    @PreDestroy
    void destroy() {
        shutdown();
    }

    private void flush(String aggregationKey) {
        PendingBatch batch = pendingBatches.remove(aggregationKey);
        if (batch == null) {
            return;
        }

        Message aggregated;
        Consumer<Message> consumer;
        synchronized (batch) {
            if (batch.fragments.isEmpty() || batch.onFlush == null) {
                return;
            }
            aggregated = merge(batch.fragments);
            consumer = batch.onFlush;
        }

        try {
            consumer.accept(aggregated);
        } catch (Exception e) {
            log.error("Failed to flush aggregated Telegram inbound message", e);
        }
    }

    private Message merge(List<TelegramInboundFragment> fragments) {
        Message first = fragments.get(0).message();
        Message last = fragments.get(fragments.size() - 1).message();
        Map<String, Object> mergedMetadata = new LinkedHashMap<>();
        if (first.getMetadata() != null) {
            mergedMetadata.putAll(first.getMetadata());
        }

        List<String> contents = new ArrayList<>();
        List<Object> messageIds = new ArrayList<>();
        List<Map<String, Object>> forwardedItems = new ArrayList<>();
        List<Map<String, Object>> attachments = new ArrayList<>();

        for (TelegramInboundFragment fragment : fragments) {
            Message message = fragment.message();
            if (message.getContent() != null && !message.getContent().isBlank()) {
                contents.add(message.getContent());
            }
            if (message.getId() != null && !message.getId().isBlank()) {
                messageIds.add(message.getId());
            }
            mergeListOfMaps(message.getMetadata(), TelegramMetadataKeys.FORWARDED_ITEMS, forwardedItems);
            mergeListOfMaps(message.getMetadata(), "attachments", attachments);
        }

        if (!forwardedItems.isEmpty()) {
            mergedMetadata.put(TelegramMetadataKeys.IS_FORWARDED, true);
            mergedMetadata.put(TelegramMetadataKeys.FORWARDED_ITEMS, forwardedItems);
        }
        if (!attachments.isEmpty()) {
            mergedMetadata.put("attachments", attachments);
        }
        if (fragments.size() > 1) {
            mergedMetadata.put(TelegramMetadataKeys.AGGREGATED, true);
            mergedMetadata.put(TelegramMetadataKeys.AGGREGATED_COUNT, fragments.size());
            mergedMetadata.put(TelegramMetadataKeys.MESSAGE_IDS, messageIds);
        }

        return Message.builder()
                .id(first.getId())
                .channelType(first.getChannelType())
                .chatId(first.getChatId())
                .senderId(first.getSenderId())
                .role(first.getRole())
                .content(String.join("\n\n", contents))
                .metadata(mergedMetadata)
                .timestamp(last.getTimestamp() != null ? last.getTimestamp() : Instant.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private void mergeListOfMaps(Map<String, Object> metadata, String key, List<Map<String, Object>> target) {
        if (metadata == null) {
            return;
        }
        Object value = metadata.get(key);
        if (!(value instanceof List<?> items)) {
            return;
        }
        for (Object item : items) {
            if (item instanceof Map<?, ?> mapItem) {
                target.add(new LinkedHashMap<>((Map<String, Object>) mapItem));
            }
        }
    }

    private static final class PendingBatch {
        private final List<TelegramInboundFragment> fragments = new ArrayList<>();
        private ScheduledFuture<?> future;
        private Consumer<Message> onFlush;
    }
}

package me.golemcore.plugins.golemcore.telegram.adapter.inbound.telegram;

import me.golemcore.plugin.api.extension.model.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelegramInboundAssemblerTest {

    private ScheduledExecutorService scheduler;
    private TelegramInboundAssembler assembler;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        assembler = new TelegramInboundAssembler(scheduler);
    }

    @AfterEach
    void tearDown() {
        assembler.shutdown();
        scheduler.shutdownNow();
    }

    @Test
    void shouldMergeSequentialMessagesWithinAggregationWindow() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> emitted = new AtomicReference<>();

        assembler.submit(
                new TelegramInboundFragment("chat:100:user:123", buildMessage("1", "First", Map.of())),
                25,
                message -> {
                    emitted.set(message);
                    latch.countDown();
                });
        assembler.submit(
                new TelegramInboundFragment("chat:100:user:123", buildMessage("2", "Second", Map.of())),
                25,
                message -> {
                    emitted.set(message);
                    latch.countDown();
                });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals("First\n\nSecond", emitted.get().getContent());
        assertEquals(true, emitted.get().getMetadata().get("telegram.aggregated"));
        assertEquals(2, emitted.get().getMetadata().get("telegram.aggregatedCount"));
        assertEquals(List.of("1", "2"), emitted.get().getMetadata().get("telegram.messageIds"));
    }

    @Test
    void shouldMergeForwardMetadataAndAttachmentsAcrossFragments() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Message> emitted = new AtomicReference<>();

        assembler.submit(
                new TelegramInboundFragment("chat:100:user:123", buildMessage("1", "[Forwarded message]\nOne",
                        buildMetadata(true, "channel", "photo-a"))),
                25,
                message -> {
                    emitted.set(message);
                    latch.countDown();
                });
        assembler.submit(
                new TelegramInboundFragment("chat:100:user:123", buildMessage("2", "[Forwarded message]\nTwo",
                        buildMetadata(true, "user", "photo-b"))),
                25,
                message -> {
                    emitted.set(message);
                    latch.countDown();
                });

        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(true, emitted.get().getMetadata().get("telegram.isForwarded"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forwardedItems = (List<Map<String, Object>>) emitted.get().getMetadata()
                .get("telegram.forwardedItems");
        assertEquals(2, forwardedItems.size());
        assertEquals("channel", forwardedItems.get(0).get("originType"));
        assertEquals("user", forwardedItems.get(1).get("originType"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) emitted.get().getMetadata()
                .get("attachments");
        assertEquals(2, attachments.size());
    }

    private Message buildMessage(String id, String content, Map<String, Object> metadata) {
        return Message.builder()
                .id(id)
                .channelType("telegram")
                .chatId("conv-1")
                .senderId("123")
                .role("user")
                .content(content)
                .metadata(new LinkedHashMap<>(metadata))
                .timestamp(Instant.parse("2026-03-30T12:00:00Z"))
                .build();
    }

    private Map<String, Object> buildMetadata(boolean forwarded, String originType, String attachmentName) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("telegram.isForwarded", forwarded);

        Map<String, Object> forwardedItem = new LinkedHashMap<>();
        forwardedItem.put("originType", originType);
        metadata.put("telegram.forwardedItems", new ArrayList<>(List.of(forwardedItem)));

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("name", attachmentName);
        metadata.put("attachments", new ArrayList<>(List.of(attachment)));
        return metadata;
    }
}

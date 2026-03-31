package me.golemcore.plugins.golemcore.notion.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class NotionReindexCoordinatorTest {

    @Test
    void shouldScheduleNextRunForFriendlyPreset() {
        NotionPluginConfigService configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(NotionPluginConfig.builder()
                .localIndexEnabled(true)
                .reindexSchedulePreset("hourly")
                .build());
        NotionLocalIndexService indexService = mock(NotionLocalIndexService.class);
        try (RecordingScheduler scheduler = new RecordingScheduler()) {
            NotionReindexCoordinator coordinator = new NotionReindexCoordinator(
                    configService,
                    indexService,
                    mock(NotionRagSyncService.class),
                    new NotionReindexScheduleResolver());
            coordinator.setSchedulerForTest(scheduler);
            coordinator.setClockForTest(Clock.fixed(Instant.parse("2026-03-30T15:30:00Z"), ZoneId.of("UTC")));

            coordinator.refreshSchedule();

            assertTrue(scheduler.lastDelayMillis > 0);
        }
    }

    @Test
    void shouldDelegateManualReindexWhenIndexEnabled() {
        NotionPluginConfigService configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(NotionPluginConfig.builder()
                .localIndexEnabled(true)
                .ragSyncEnabled(true)
                .reindexSchedulePreset("disabled")
                .build());
        NotionLocalIndexService indexService = mock(NotionLocalIndexService.class);
        when(indexService.reindexAll()).thenReturn(new NotionReindexSummary(3, 9));
        NotionRagSyncService ragSyncService = mock(NotionRagSyncService.class);
        when(ragSyncService.reindexAll()).thenReturn(4);

        try (RecordingScheduler scheduler = new RecordingScheduler()) {
            NotionReindexCoordinator coordinator = new NotionReindexCoordinator(
                    configService,
                    indexService,
                    ragSyncService,
                    new NotionReindexScheduleResolver());
            coordinator.setSchedulerForTest(scheduler);
            coordinator.setClockForTest(Clock.systemUTC());

            NotionReindexSummary summary = coordinator.reindexNow();

            assertEquals(3, summary.pagesIndexed());
            assertEquals(9, summary.chunksIndexed());
            assertEquals(4, summary.documentsSynced());
            verify(indexService).reindexAll();
            verify(ragSyncService).reindexAll();
        }
    }

    @Test
    void shouldRejectManualReindexWhenNoIndexTargetIsEnabled() {
        NotionPluginConfigService configService = mock(NotionPluginConfigService.class);
        when(configService.getConfig()).thenReturn(NotionPluginConfig.builder()
                .localIndexEnabled(false)
                .ragSyncEnabled(false)
                .build());

        try (RecordingScheduler scheduler = new RecordingScheduler()) {
            NotionReindexCoordinator coordinator = new NotionReindexCoordinator(
                    configService,
                    mock(NotionLocalIndexService.class),
                    mock(NotionRagSyncService.class),
                    new NotionReindexScheduleResolver());
            coordinator.setSchedulerForTest(scheduler);
            coordinator.setClockForTest(Clock.systemUTC());

            IllegalStateException error = assertThrows(IllegalStateException.class, coordinator::reindexNow);
            assertEquals("No indexing target is enabled.", error.getMessage());
        }
    }

    private static final class RecordingScheduler extends AbstractExecutorService implements ScheduledExecutorService {

        private long lastDelayMillis = -1;

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastDelayMillis = TimeUnit.MILLISECONDS.convert(delay, unit);
            return new CompletedScheduledFuture();
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void close() {
            shutdownNow();
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command,
                long initialDelay,
                long delay,
                TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class CompletedScheduledFuture implements ScheduledFuture<Object> {

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CompletedScheduledFuture;
        }

        @Override
        public int hashCode() {
            return CompletedScheduledFuture.class.hashCode();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
            return null;
        }
    }
}

package me.golemcore.plugins.golemcore.notion.support;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfig;
import me.golemcore.plugins.golemcore.notion.NotionPluginConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class NotionReindexCoordinator {

    private final NotionPluginConfigService configService;
    private final NotionLocalIndexService localIndexService;
    private final NotionRagSyncService ragSyncService;
    private final NotionReindexScheduleResolver scheduleResolver;
    private final ScheduledExecutorService scheduler;
    private final Clock clock;

    private Optional<ScheduledFuture<?>> scheduledFuture = Optional.empty();

    @Autowired
    public NotionReindexCoordinator(
            NotionPluginConfigService configService,
            NotionLocalIndexService localIndexService,
            NotionRagSyncService ragSyncService,
            NotionReindexScheduleResolver scheduleResolver) {
        this(
                configService,
                localIndexService,
                ragSyncService,
                scheduleResolver,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "notion-reindex");
                    thread.setDaemon(true);
                    return thread;
                }),
                Clock.systemDefaultZone());
    }

    NotionReindexCoordinator(
            NotionPluginConfigService configService,
            NotionLocalIndexService localIndexService,
            NotionRagSyncService ragSyncService,
            NotionReindexScheduleResolver scheduleResolver,
            ScheduledExecutorService scheduler,
            Clock clock) {
        this.configService = configService;
        this.localIndexService = localIndexService;
        this.ragSyncService = ragSyncService;
        this.scheduleResolver = scheduleResolver;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @PostConstruct
    public void start() {
        refreshSchedule();
    }

    @PreDestroy
    public synchronized void stop() {
        cancelScheduledFuture();
        scheduler.shutdownNow();
    }

    public synchronized void refreshSchedule() {
        cancelScheduledFuture();
        Optional<String> cronExpression = scheduleResolver.resolveCronExpression(configService.getConfig());
        if (cronExpression.isEmpty()) {
            return;
        }
        scheduleNext(cronExpression.get());
    }

    public NotionReindexSummary reindexNow() {
        NotionPluginConfig config = configService.getConfig();
        if (!Boolean.TRUE.equals(config.getLocalIndexEnabled()) && !Boolean.TRUE.equals(config.getRagSyncEnabled())) {
            throw new IllegalStateException("No indexing target is enabled.");
        }
        NotionReindexSummary localSummary = new NotionReindexSummary(0, 0, 0);
        if (Boolean.TRUE.equals(config.getLocalIndexEnabled())) {
            localSummary = localIndexService.reindexAll();
        }
        int documentsSynced = Boolean.TRUE.equals(config.getRagSyncEnabled())
                ? ragSyncService.reindexAll()
                : 0;
        return new NotionReindexSummary(
                localSummary.pagesIndexed(),
                localSummary.chunksIndexed(),
                documentsSynced);
    }

    private synchronized void scheduleNext(String cronExpression) {
        CronExpression cron = CronExpression.parse(cronExpression);
        ZonedDateTime now = ZonedDateTime.ofInstant(clock.instant(), ZoneId.systemDefault());
        ZonedDateTime next = cron.next(now);
        if (next == null) {
            return;
        }
        long delayMillis = Math.max(1L, Duration.between(now, next).toMillis());
        scheduledFuture = Optional
                .of(scheduler.schedule(this::runScheduledReindex, delayMillis, TimeUnit.MILLISECONDS));
    }

    private void runScheduledReindex() {
        try {
            reindexNow();
        } catch (RuntimeException ignored) {
            // Best effort background task; failures should not break the scheduler loop.
        } finally {
            refreshSchedule();
        }
    }

    private void cancelScheduledFuture() {
        scheduledFuture.ifPresent(future -> future.cancel(false));
        scheduledFuture = Optional.empty();
    }
}

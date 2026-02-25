package com.fastasyncworldedit.bukkit.util;

import com.fastasyncworldedit.core.util.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class BukkitTaskManager extends TaskManager {

    private final Plugin plugin;
    private final AtomicInteger foliaTaskCounter = new AtomicInteger();
    private final Map<Integer, Runnable> foliaTaskCancels = new ConcurrentHashMap<>();

    public BukkitTaskManager(final Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int repeat(@Nonnull final Runnable runnable, final int interval) {
        try {
            return this.plugin.getServer().getScheduler().scheduleSyncRepeatingTask(this.plugin, runnable, interval, interval);
        } catch (UnsupportedOperationException ignored) {
            return scheduleFoliaRepeatingTask(runnable, interval);
        }
    }

    @Override
    public int repeatAsync(@Nonnull final Runnable runnable, final int interval) {
        return this.plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(this.plugin, runnable, interval, interval);
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, runnable).getTaskId();
    }

    @Override
    public void task(@Nonnull final Runnable runnable) {
        try {
            this.plugin.getServer().getScheduler().runTask(this.plugin, runnable).getTaskId();
        } catch (UnsupportedOperationException ignored) {
            executeFoliaNow(runnable);
        }
    }

    @Override
    public void later(@Nonnull final Runnable runnable, final int delay) {
        try {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, runnable, delay).getTaskId();
        } catch (UnsupportedOperationException ignored) {
            scheduleFoliaDelayedTask(runnable, delay);
        }
    }

    @Override
    public void laterAsync(@Nonnull final Runnable runnable, final int delay) {
        this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(this.plugin, runnable, delay);
    }

    @Override
    public void cancel(final int task) {
        if (task != -1) {
            Runnable foliaCancel = foliaTaskCancels.remove(task);
            if (foliaCancel != null) {
                foliaCancel.run();
            } else {
                Bukkit.getScheduler().cancelTask(task);
            }
        }
    }

    private void executeFoliaNow(final Runnable runnable) {
        try {
            Object globalRegionScheduler = getGlobalRegionScheduler();
            Method execute = globalRegionScheduler.getClass().getMethod("execute", Plugin.class, Runnable.class);
            execute.invoke(globalRegionScheduler, this.plugin, runnable);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule sync task on Folia", e);
        }
    }

    private void scheduleFoliaDelayedTask(final Runnable runnable, final long delay) {
        try {
            Object globalRegionScheduler = getGlobalRegionScheduler();
            Method runDelayed = globalRegionScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
            Object scheduledTask = runDelayed.invoke(globalRegionScheduler, this.plugin, new FoliaTaskConsumer(runnable), delay);
            storeFoliaTaskCancel(scheduledTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule delayed sync task on Folia", e);
        }
    }

    private int scheduleFoliaRepeatingTask(final Runnable runnable, final long interval) {
        try {
            Object globalRegionScheduler = getGlobalRegionScheduler();
            Method runAtFixedRate = globalRegionScheduler.getClass()
                    .getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
            Object scheduledTask = runAtFixedRate.invoke(
                    globalRegionScheduler,
                    this.plugin,
                    new FoliaTaskConsumer(runnable),
                    interval,
                    interval
            );
            return storeFoliaTaskCancel(scheduledTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule repeating sync task on Folia", e);
        }
    }

    private int storeFoliaTaskCancel(final Object scheduledTask) {
        try {
            Method cancel = scheduledTask.getClass().getMethod("cancel");
            int taskId = foliaTaskCounter.decrementAndGet();
            foliaTaskCancels.put(taskId, () -> {
                try {
                    cancel.invoke(scheduledTask);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException("Unable to cancel Folia task", e);
                }
            });
            return taskId;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to wire Folia task cancellation", e);
        }
    }

    private Object getGlobalRegionScheduler() throws ReflectiveOperationException {
        Server server = this.plugin.getServer();
        Method method = server.getClass().getMethod("getGlobalRegionScheduler");
        return method.invoke(server);
    }

    private record FoliaTaskConsumer(Runnable runnable) implements java.util.function.Consumer<Object> {

        @Override
        public void accept(final Object ignored) {
            runnable.run();
        }

    }

}

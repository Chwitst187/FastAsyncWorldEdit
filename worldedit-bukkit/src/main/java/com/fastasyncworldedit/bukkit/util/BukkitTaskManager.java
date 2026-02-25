package com.fastasyncworldedit.bukkit.util;

import com.fastasyncworldedit.core.util.TaskManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
        try {
            return this.plugin.getServer().getScheduler().scheduleAsyncRepeatingTask(this.plugin, runnable, interval, interval);
        } catch (UnsupportedOperationException ignored) {
            return scheduleFoliaAsyncRepeatingTask(runnable, interval);
        }
    }

    @Override
    public void async(@Nonnull final Runnable runnable) {
        try {
            this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, runnable).getTaskId();
        } catch (UnsupportedOperationException ignored) {
            scheduleFoliaAsyncNow(runnable);
        }
    }

    @Override
    public boolean isMainThread() {
        if (Bukkit.isPrimaryThread()) {
            return true;
        }
        return isFoliaGlobalTickThread();
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
        try {
            this.plugin.getServer().getScheduler().runTaskLaterAsynchronously(this.plugin, runnable, delay);
        } catch (UnsupportedOperationException ignored) {
            scheduleFoliaAsyncDelayedTask(runnable, delay);
        }
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
            Object scheduledTask = runDelayed.invoke(
                    globalRegionScheduler,
                    this.plugin,
                    new FoliaTaskConsumer(runnable),
                    normalizeFoliaTickDelay(delay)
            );
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
                    normalizeFoliaTickDelay(interval),
                    normalizeFoliaTickDelay(interval)
            );
            return storeFoliaTaskCancel(scheduledTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule repeating sync task on Folia", e);
        }
    }

    private void scheduleFoliaAsyncNow(final Runnable runnable) {
        try {
            Object asyncScheduler = getAsyncScheduler();
            Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
            Object scheduledTask = runNow.invoke(asyncScheduler, this.plugin, new FoliaTaskConsumer(runnable));
            storeFoliaTaskCancel(scheduledTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule async task on Folia", e);
        }
    }

    private void scheduleFoliaAsyncDelayedTask(final Runnable runnable, final long delay) {
        try {
            Object asyncScheduler = getAsyncScheduler();
            Method runDelayed = asyncScheduler.getClass().getMethod(
                    "runDelayed",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    TimeUnit.class
            );
            Object scheduledTask = runDelayed.invoke(
                    asyncScheduler,
                    this.plugin,
                    new FoliaTaskConsumer(runnable),
                    normalizeFoliaMillisDelay(delay),
                    TimeUnit.MILLISECONDS
            );
            storeFoliaTaskCancel(scheduledTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule delayed async task on Folia", e);
        }
    }

    private int scheduleFoliaAsyncRepeatingTask(final Runnable runnable, final long interval) {
        try {
            Object asyncScheduler = getAsyncScheduler();
            Method runAtFixedRate = asyncScheduler.getClass().getMethod(
                    "runAtFixedRate",
                    Plugin.class,
                    java.util.function.Consumer.class,
                    long.class,
                    long.class,
                    TimeUnit.class
            );
            long normalizedDelay = normalizeFoliaMillisDelay(interval);
            Object scheduledTask = runAtFixedRate.invoke(
                    asyncScheduler,
                    this.plugin,
                    new FoliaTaskConsumer(runnable),
                    normalizedDelay,
                    normalizedDelay,
                    TimeUnit.MILLISECONDS
            );
            return storeFoliaTaskCancel(scheduledTask);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Unable to schedule repeating async task on Folia", e);
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

    private Object getAsyncScheduler() throws ReflectiveOperationException {
        Server server = this.plugin.getServer();
        Method method = server.getClass().getMethod("getAsyncScheduler");
        return method.invoke(server);
    }

    private boolean isFoliaGlobalTickThread() {
        try {
            Method method = Bukkit.class.getMethod("isGlobalTickThread");
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private long normalizeFoliaTickDelay(final long ticks) {
        return Math.max(1L, ticks);
    }

    private long normalizeFoliaMillisDelay(final long ticks) {
        return normalizeFoliaTickDelay(ticks) * 50L;
    }

    private record FoliaTaskConsumer(Runnable runnable) implements java.util.function.Consumer<Object> {

        @Override
        public void accept(final Object ignored) {
            runnable.run();
        }

    }

}

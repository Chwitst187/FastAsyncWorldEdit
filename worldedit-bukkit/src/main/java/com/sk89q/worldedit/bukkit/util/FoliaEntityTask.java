package com.sk89q.worldedit.bukkit.util;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility methods to execute Bukkit entity interactions on a Folia-safe thread.
 */
public final class FoliaEntityTask {

    private FoliaEntityTask() {
    }

    public static <T> T execute(final Entity entity, final Supplier<T> supplier) {
        if (isEntityThread(entity)) {
            return supplier.get();
        }

        CompletableFuture<T> future = new CompletableFuture<>();
        Plugin plugin = WorldEditPlugin.getInstance();
        Runnable run = () -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };

        if (!scheduleOnEntityScheduler(entity, plugin, run, future)) {
            com.fastasyncworldedit.core.util.TaskManager.taskManager().task(run);
        }

        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    private static boolean scheduleOnEntityScheduler(
            final Entity entity,
            final Plugin plugin,
            final Runnable run,
            final CompletableFuture<?> future
    ) {
        try {
            Method getScheduler = entity.getClass().getMethod("getScheduler");
            Object scheduler = getScheduler.invoke(entity);

            try {
                Method execute = scheduler.getClass().getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
                boolean scheduled = (boolean) execute.invoke(scheduler, plugin, run, (Runnable) () -> {
                    if (!future.isDone()) {
                        future.completeExceptionally(new IllegalStateException("Entity scheduler retired before execution"));
                    }
                }, 1L);
                return scheduled;
            } catch (NoSuchMethodException ignored) {
                Method runMethod = scheduler.getClass().getMethod(
                        "run",
                        Plugin.class,
                        Consumer.class,
                        Runnable.class,
                        long.class
                );
                runMethod.invoke(scheduler, plugin, (Consumer<Object>) ignoredTask -> run.run(), (Runnable) () -> {
                    if (!future.isDone()) {
                        future.completeExceptionally(new IllegalStateException("Entity scheduler retired before execution"));
                    }
                }, 1L);
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isEntityThread(final Entity entity) {
        if (Bukkit.isPrimaryThread()) {
            return true;
        }
        try {
            Method isOwnedByCurrentRegion = Bukkit.getServer().getClass()
                    .getMethod("isOwnedByCurrentRegion", Entity.class);
            return Boolean.TRUE.equals(isOwnedByCurrentRegion.invoke(Bukkit.getServer(), entity));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

}

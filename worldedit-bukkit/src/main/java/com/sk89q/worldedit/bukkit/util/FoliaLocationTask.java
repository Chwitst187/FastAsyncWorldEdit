package com.sk89q.worldedit.bukkit.util;

import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility methods to execute location/world interactions on a Folia-safe region thread.
 */
public final class FoliaLocationTask {

    private FoliaLocationTask() {
    }

    public static <T> T execute(final World world, final int blockX, final int blockY, final int blockZ, final Supplier<T> supplier) {
        if (isOwnedByCurrentRegion(world, blockX, blockY, blockZ)) {
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

        if (!scheduleOnRegionScheduler(world, blockX, blockZ, plugin, run)) {
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

    private static boolean scheduleOnRegionScheduler(
            final World world,
            final int blockX,
            final int blockZ,
            final Plugin plugin,
            final Runnable run
    ) {
        try {
            Object server = Bukkit.getServer();
            Method getRegionScheduler = server.getClass().getMethod("getRegionScheduler");
            Object scheduler = getRegionScheduler.invoke(server);
            int chunkX = blockX >> 4;
            int chunkZ = blockZ >> 4;
            try {
                Method execute = scheduler.getClass().getMethod(
                        "execute",
                        Plugin.class,
                        World.class,
                        int.class,
                        int.class,
                        Runnable.class
                );
                execute.invoke(scheduler, plugin, world, chunkX, chunkZ, run);
                return true;
            } catch (NoSuchMethodException ignored) {
                Method runMethod = scheduler.getClass().getMethod(
                        "run",
                        Plugin.class,
                        World.class,
                        int.class,
                        int.class,
                        Consumer.class
                );
                runMethod.invoke(scheduler, plugin, world, chunkX, chunkZ, (Consumer<Object>) ignoredTask -> run.run());
                return true;
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isOwnedByCurrentRegion(final World world, final int blockX, final int blockY, final int blockZ) {
        if (Bukkit.isPrimaryThread()) {
            return true;
        }
        try {
            Method isOwnedByCurrentRegion = Bukkit.getServer().getClass()
                    .getMethod("isOwnedByCurrentRegion", World.class, int.class, int.class, int.class);
            return Boolean.TRUE.equals(isOwnedByCurrentRegion.invoke(Bukkit.getServer(), world, blockX, blockY, blockZ));
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

}

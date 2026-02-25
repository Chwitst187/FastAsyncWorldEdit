package com.sk89q.worldedit.bukkit.adapter.impl.fawe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class that attempts to determine the current server tick via reflection.
 * Returns {@code 0} on failures and caches discovered fields/methods by class.
 */
public final class MinecraftReflection {

    private static final Logger LOGGER = Logger.getLogger("FAWE-MinecraftReflection");
    private static final ConcurrentMap<Class<?>, Object> CACHE = new ConcurrentHashMap<>();

    private static final List<String> FIELD_CANDIDATES = Arrays.asList(
            "currentTick",
            "tickCount",
            "fullTick",
            "fullTickCount",
            "ticks",
            "currentTicks",
            "au",
            "ac"
    );

    private static final List<String> METHOD_CANDIDATES = Arrays.asList(
            "getCurrentTick",
            "getTickCount",
            "getFullTick",
            "getTicks",
            "getFullTickCount",
            "getTick"
    );

    private MinecraftReflection() {
    }

    public static int getCurrentTick(Object serverOrClass) {
        try {
            Class<?> clazz = resolveClass(serverOrClass);
            if (clazz == null) {
                LOGGER.fine("MinecraftReflection: could not resolve MinecraftServer class");
                return 0;
            }

            Object cached = CACHE.get(clazz);
            if (cached instanceof Field) {
                return readField((Field) cached, serverOrClass);
            }
            if (cached instanceof Method) {
                return invokeMethod((Method) cached, serverOrClass);
            }

            for (String name : FIELD_CANDIDATES) {
                try {
                    Field field = clazz.getDeclaredField(name);
                    field.setAccessible(true);
                    CACHE.put(clazz, field);
                    return readField(field, serverOrClass);
                } catch (NoSuchFieldException ignored) {
                } catch (Throwable t) {
                    LOGGER.log(Level.FINE, "Error while reading field '" + name + "'", t);
                }
            }

            for (String methodName : METHOD_CANDIDATES) {
                try {
                    Method method = clazz.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    CACHE.put(clazz, method);
                    return invokeMethod(method, serverOrClass);
                } catch (NoSuchMethodException ignored) {
                } catch (Throwable t) {
                    LOGGER.log(Level.FINE, "Error while invoking method '" + methodName + "'", t);
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "MinecraftReflection unexpected error", t);
        }

        LOGGER.fine("MinecraftReflection: returning fallback tick 0");
        return 0;
    }

    private static Class<?> resolveClass(Object serverOrClass) {
        if (serverOrClass instanceof Class) {
            return (Class<?>) serverOrClass;
        }
        if (serverOrClass != null) {
            return serverOrClass.getClass();
        }
        try {
            return Class.forName("net.minecraft.server.MinecraftServer");
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static int readField(Field field, Object serverOrClass) {
        try {
            Object value;
            if (Modifier.isStatic(field.getModifiers())) {
                value = field.get(null);
            } else {
                if (serverOrClass instanceof Class || serverOrClass == null) {
                    return 0;
                }
                value = field.get(serverOrClass);
            }

            if (value instanceof Integer) {
                return (Integer) value;
            }
            if (value instanceof Long) {
                long longValue = (Long) value;
                return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, longValue));
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            LOGGER.log(Level.FINE, "Error while reading tick field", e);
        }
        return 0;
    }

    private static int invokeMethod(Method method, Object serverOrClass) {
        try {
            Object target = Modifier.isStatic(method.getModifiers()) ? null : serverOrClass;
            if (target instanceof Class || target == null && !Modifier.isStatic(method.getModifiers())) {
                return 0;
            }

            Object result = method.invoke(target);
            if (result instanceof Integer) {
                return (Integer) result;
            }
            if (result instanceof Long) {
                long longValue = (Long) result;
                return (int) Math.min(Integer.MAX_VALUE, Math.max(Integer.MIN_VALUE, longValue));
            }
            if (result instanceof Number) {
                return ((Number) result).intValue();
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOGGER.log(Level.FINE, "Error while invoking tick method", e);
        }
        return 0;
    }
}

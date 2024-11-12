package com.floweytf.coro.ap.util;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;

public class ReflectTool {
    public static <T extends AccessibleObject> T setAccessible(@NotNull T accessor) {
        accessor.setAccessible(true);
        return accessor;
    }

    public static Method getMethod(@NotNull Class<?> clazz, @NotNull String mName,
                                   @NotNull Class<?>... parameterTypes) {
        Method method = null;
        Class<?> original = clazz;
        while (clazz != null) {
            try {
                method = clazz.getDeclaredMethod(mName, parameterTypes);
                break;
            } catch (NoSuchMethodException ignored) {
            }
            clazz = clazz.getSuperclass();
        }

        if (method == null) {
            throw new RuntimeException(original.getName() + " :: " + mName + "(args)");
        }

        return setAccessible(method);
    }

    public static Field getField(@NotNull Class<?> clazz, @NotNull String fName) {
        Field field = null;
        Class<?> original = clazz;
        while (clazz != null) {
            try {
                field = clazz.getDeclaredField(fName);
                break;
            } catch (NoSuchFieldException ignored) {
            }
            clazz = clazz.getSuperclass();
        }

        if (field == null) {
            throw new RuntimeException(original.getName() + " :: " + fName);
        }

        return setAccessible(field);
    }
}

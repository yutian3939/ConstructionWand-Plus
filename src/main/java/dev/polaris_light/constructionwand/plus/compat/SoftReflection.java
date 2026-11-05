package dev.polaris_light.constructionwand.plus.compat;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Reaches into a mod that is only installed at runtime, without a compile time dependency on it.
 * <p>
 * The readers of this package are about one small question per mod ("which block of this machine does
 * the ticking?"), and the answer lives in a method or two of an optional mod. Compiling against those
 * mods would tie the build to their version numbers, their own dependencies and their maven; a missing
 * or renamed signature would then either break the build or, worse, refuse to load the game. Every call
 * here therefore degrades to {@code null}, which the caller reads as "this is not a machine of that
 * mod" and the wand stays the ordinary wand.
 * <p>
 * Only public members are looked up, and only on classes the mod itself makes public.
 */
public final class SoftReflection
{
    private SoftReflection() {
    }

    /**
     * @return the class, or {@code null} when the mod is not installed (or no longer has that name)
     */
    @Nullable
    public static Class<?> type(String name) {
        try {
            // Not initialised on lookup: a machine class of an optional mod is not always safe to load
            // before the mod itself had its say.
            return Class.forName(name, false, SoftReflection.class.getClassLoader());
        } catch(Throwable missing) {
            return null;
        }
    }

    /**
     * @return the public method, or {@code null} when this version of the mod does not have it
     */
    @Nullable
    public static Method method(@Nullable Class<?> owner, String name, Class<?>... parameters) {
        if(owner == null) return null;
        try {
            return owner.getMethod(name, parameters);
        } catch(Throwable missing) {
            return null;
        }
    }

    /**
     * @return what the call answered, or {@code null} when the method is absent or the call failed
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T call(@Nullable Method method, @Nullable Object target, Object... arguments) {
        if(method == null || target == null) return null;
        try {
            return (T) method.invoke(target, arguments);
        } catch(Throwable failed) {
            return null;
        }
    }
}

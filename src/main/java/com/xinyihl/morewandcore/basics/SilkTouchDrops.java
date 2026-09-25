package com.xinyihl.morewandcore.basics;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the silk touch drop of a block.
 * <p>
 * {@code Block.getSilkTouchDrop} is protected and several vanilla blocks (logs, leaves, quartz and
 * friends) override it, so the default implementation cannot simply be copied. The method is
 * resolved reflectively once per block class and cached; both the MCP name and the SRG name are
 * tried so the helper keeps working in a deobfuscated dev environment and in production.
 */
public final class SilkTouchDrops {
    private static final String[] METHOD_NAMES = {"getSilkTouchDrop", "func_180643_i"};
    private static final Map<Class<?>, Optional<Method>> CACHE = new ConcurrentHashMap<>();

    private SilkTouchDrops() {
    }

    public static ItemStack drop(Block block, IBlockState state) {
        Method method = resolve(block.getClass());
        if (method == null) {
            return fallback(block, state);
        }
        try {
            Object result = method.invoke(block, state);
            return result instanceof ItemStack ? ((ItemStack) result).copy() : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return fallback(block, state);
        }
    }

    @Nullable
    private static Method resolve(Class<?> blockClass) {
        Optional<Method> cached = CACHE.get(blockClass);
        if (cached != null) {
            return cached.orElse(null);
        }
        Method found = null;
        try {
            for (Class<?> current = blockClass; current != null && Block.class.isAssignableFrom(current); current = current.getSuperclass()) {
                found = declaredMethod(current);
                if (found != null) {
                    break;
                }
            }
        } catch (RuntimeException exception) {
            found = null;
        }
        CACHE.put(blockClass, Optional.ofNullable(found));
        return found;
    }

    @Nullable
    private static Method declaredMethod(Class<?> type) {
        for (String name : METHOD_NAMES) {
            try {
                Method method = type.getDeclaredMethod(name, IBlockState.class);
                if (ItemStack.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // try the next known name
            }
        }
        return null;
    }

    private static ItemStack fallback(Block block, IBlockState state) {
        Item item = Item.getItemFromBlock(block);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int meta = item.getHasSubtypes() ? block.getMetaFromState(state) : 0;
        return new ItemStack(item, 1, meta);
    }
}

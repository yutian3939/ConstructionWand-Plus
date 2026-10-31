package dev.polaris_light.constructionwand.plus;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import dev.polaris_light.constructionwand.ConstructionWand;
import dev.polaris_light.constructionwand.basics.ConfigServer;

/**
 * Runtime configuration of the two limits of the wand, so an operator can change them without editing the
 * configuration file. The values are written back through the config system, which persists them.
 * <p>
 * The commands are named after what they limit rather than after the field they write, and they are grouped
 * under the part of the wand they belong to:
 * <ul>
 *   <li>{@code /cwand storage bindings <count>} - how many containers the storage core may hold a binding for;</li>
 *   <li>{@code /cwand time maxblocks <count>} - how many blocks one player may hold accelerated at once.</li>
 * </ul>
 * There are deliberately no short aliases: a name that does not say what it limits is what this replaced.
 */
@EventBusSubscriber(modid = ConstructionWand.MODID)
public final class PlusCommands
{
    private PlusCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(storageCommand("constructionwand"));
        event.getDispatcher().register(storageCommand("cwand"));
        event.getDispatcher().register(timeCommand("constructionwand"));
        event.getDispatcher().register(timeCommand("cwand"));


    }

    /**
     * {@code /cwand storage bindings [count]} - the containers the storage core may be bound to.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> storageCommand(String name) {
        return Commands.literal(name)
                .then(Commands.literal("storage")
                        .then(bindingLimit("bindings")));
    }

    /**
     * {@code /cwand time maxblocks [count]} - the blocks one player may hold accelerated at once.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> timeCommand(String name) {
        return Commands.literal(name)
                .then(Commands.literal("time")
                        .then(activeLimit("maxblocks")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bindingLimit(String literal) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            ConstructionWand.MODID + ".command.bindings.show",
                            describe(ConfigServer.MAX_BOUND_CONTAINERS.get())), false);
                    return 1;
                })
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 4096))
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "count");
                            ConfigServer.MAX_BOUND_CONTAINERS.set(value);
                            ConfigServer.SPEC.save();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    ConstructionWand.MODID + ".command.bindings.set", describe(value)), true);
                            return 1;
                        }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> activeLimit(String literal) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    context.getSource().sendSuccess(() -> Component.translatable(
                            ConstructionWand.MODID + ".command.maxblocks.show",
                            describe(ConfigServer.TIME_CORE_MAX_ACTIVE.get())), false);
                    return 1;
                })
                .then(Commands.argument("count", IntegerArgumentType.integer(0, 4096))
                        .executes(context -> {
                            int value = IntegerArgumentType.getInteger(context, "count");
                            ConfigServer.TIME_CORE_MAX_ACTIVE.set(value);
                            ConfigServer.SPEC.save();
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    ConstructionWand.MODID + ".command.maxblocks.set", describe(value)), true);
                            return 1;
                        }));
    }

    private static Object describe(int value) {
        if(value <= 0) return Component.translatable(ConstructionWand.MODID + ".command.unlimited");
        return value;
    }
}
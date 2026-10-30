package thetadev.constructionwand.plus;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import thetadev.constructionwand.ConstructionWand;
import thetadev.constructionwand.basics.ConfigServer;

/**
 * Runtime configuration of the storage core's bind limit, so a server operator can raise or lower it
 * without editing the configuration file. The value is written back through the Forge config system,
 * which persists it and syncs it to clients.
 */
@Mod.EventBusSubscriber(modid = ConstructionWand.MODID)
public final class PlusCommands
{
    private PlusCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(limitCommand("constructionwand"));
        // Short alias, like the 1.12.2 version had.
        event.getDispatcher().register(limitCommand("cwand"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> limitCommand(String name) {
        return Commands.literal(name)
                .then(Commands.literal("limit")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.translatable(
                                    ConstructionWand.MODID + ".command.limit.show", describe(ConfigServer.MAX_BOUND_CONTAINERS.get())), false);
                            return 1;
                        })
                        .then(Commands.argument("value", IntegerArgumentType.integer(-1, 4096))
                                .executes(context -> {
                                    int value = IntegerArgumentType.getInteger(context, "value");
                                    if(value == 0) {
                                        context.getSource().sendFailure(Component.translatable(
                                                ConstructionWand.MODID + ".command.usage"));
                                        return 0;
                                    }
                                    ConfigServer.MAX_BOUND_CONTAINERS.set(value);
                                    ConfigServer.SPEC.save();
                                    CommandSourceStack source = context.getSource();
                                    source.sendSuccess(() -> Component.translatable(
                                            ConstructionWand.MODID + ".command.limit.set", describe(value)), true);
                                    return 1;
                                })));
    }

    private static Object describe(int value) {
        if(value <= 0) return Component.translatable(ConstructionWand.MODID + ".command.limit.unlimited");
        return value;
    }
}

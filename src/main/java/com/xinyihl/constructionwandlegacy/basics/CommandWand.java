package com.xinyihl.constructionwandlegacy.basics;

import com.xinyihl.constructionwandlegacy.Tags;
import com.xinyihl.constructionwandlegacy.config.ModConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Runtime configuration of the storage core bind limit, so a server operator can raise or lower it
 * without editing the configuration file. The value is written back through the Forge config system,
 * which persists it and syncs it to clients.
 */
public class CommandWand extends CommandBase {
    @Override
    public String getName() {
        return "constructionwand";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("cwand");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return Tags.MOD_ID + ".command.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1 || !"limit".equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        if (args.length == 1) {
            sender.sendMessage(new TextComponentTranslation(Tags.MOD_ID + ".command.limit.show", describeLimit(ModConfig.storage.maxBoundContainers)));
            return;
        }
        int value = parseInt(args[1]);
        if (value < -1 || value == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        ModConfig.storage.maxBoundContainers = value;
        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        sender.sendMessage(new TextComponentTranslation(Tags.MOD_ID + ".command.limit.set", describeLimit(value)));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "limit");
        }
        return Collections.emptyList();
    }

    private static Object describeLimit(int value) {
        return value <= 0
                ? new TextComponentTranslation(Tags.MOD_ID + ".command.limit.unlimited")
                : value;
    }
}

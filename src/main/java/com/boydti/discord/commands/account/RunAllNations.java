package com.boydti.discord.commands.account;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.CommandManager;
import com.boydti.discord.commands.manager.Noformat;
import com.boydti.discord.commands.manager.dummy.DelegateMessage;
import com.boydti.discord.commands.manager.dummy.DelegateMessageEvent;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class RunAllNations extends Command implements Noformat {

    public RunAllNations() {
        super(CommandCategory.INTERNAL_AFFAIRS, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " <nations> <command>";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Roles.ADMIN.has(user, server);
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 2) return usage(event);
        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));

        if (nations.size() == 0) return "No nations found for `" + args.get(0) + "`";
        if (nations.size() > 200 && !Roles.ADMIN.hasOnRoot(author)) return ">200 nations";

        String cmd = args.get(1);
        MessageChannel channel = event.getChannel();
        for (DBNation nation : nations) {
            String formatted = DiscordUtil.format(guild, channel, author, nation, cmd);

            Message message = new DelegateMessage(event.getMessage()) {
                @Nonnull
                @Override
                public String getContentRaw() {
                    return formatted;
                }

                @Nonnull
                @Override
                public User getAuthor() {
                    return author;
                }

                @Nullable
                @Override
                public Member getMember() {
                    return getGuild().getMember(getAuthor());
                }
            };
            MessageReceivedEvent finalEvent = new DelegateMessageEvent(guild, event.getResponseNumber(), message);
            Locutus.imp().getCommandManager().run(finalEvent, false, true);
        }
        return "Done!";
    }
}

package com.boydti.discord.commands.external.guild;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.war.WarCategory;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class WarRemove extends Command {
    public WarRemove() {
        super(CommandCategory.MILCOM);
    }
    @Override
    public String help() {
        return super.help() + " <nation>";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);
        GuildDB db = Locutus.imp().getGuildDB(event);
        WarCategory warChannels = db.getWarChannel();
        if (warChannels == null) return "War channels are not enabled";

        WarCategory.WarRoom waRoom = warChannels.getWarRoom(event.getGuildChannel());
        if (waRoom == null) return "This command must be run in a war room";

        Set<DBNation> nation = DiscordUtil.parseNations(guild, args.get(0));
        return super.onCommand(event, guild, author, me, args, flags);
    }
}

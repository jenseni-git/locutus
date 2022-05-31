package com.boydti.discord.commands.alliance;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.db.entities.PendingTreaty;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.offshore.Auth;
import com.boydti.discord.apiv1.enums.Rank;
import com.boydti.discord.apiv1.enums.TreatyType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.Set;

public class ModifyTreaty extends Command {
    private final boolean value;

    public ModifyTreaty(String name, boolean value) {
        super(name, CommandCategory.GOV, CommandCategory.FOREIGN_AFFAIRS);
        this.value = value;
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return Locutus.imp().getGuildDB(server).hasAuth() && Roles.FOREIGN_AFFAIRS.has(user, server);
    }

    @Override
    public String help() {
        return super.help() + " <treaty-id>";
    }

    @Override
    public String desc() {
        return "Use `!treaties` to list the current treaties";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 1) return usage(event);

        int treatyOrAAId = PnwUtil.parseAllianceId(args.get(0));
        GuildDB db = Locutus.imp().getGuildDB(guild);
        Auth auth = db.getAuth();
        if (auth == null) return "No authentication enabled for this guild";

        List<PendingTreaty> treaties = auth.getTreaties();
        treaties.removeIf(treaty -> treaty.status != PendingTreaty.TreatyStatus.ACTIVE);
        treaties.removeIf(treaty -> treaty.from != treatyOrAAId && treaty.to != treatyOrAAId && treaty.treatyId != treatyOrAAId);
        if (treaties.isEmpty()) return "There are no active treaties";

        boolean admin = Roles.ADMIN.has(author, db.getGuild()) || (me.getAlliance_id() == db.getAlliance_id() && me.getPosition() >= Rank.HEIR.id);

        for (PendingTreaty treaty : treaties) {
            if (!admin && treaty.type.getStrength() >= TreatyType.PROTECTORATE.getStrength()) {
                return "You need to be an admin to cancel a defensive treaty";
            }
            return auth.modifyTreaty(treaty.treatyId, false);
        }
        return "No treaty found";
    }
}

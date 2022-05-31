package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.manager.Noformat;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.Alliance;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.user.Roles;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.StringMan;
import com.boydti.discord.util.sheet.SpreadSheet;
import com.boydti.discord.apiv1.enums.Rank;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class NationSheet extends Command implements Noformat {
    public NationSheet() {
        super(CommandCategory.GOV, CommandCategory.GENERAL_INFO_AND_TOOLS);
    }

    @Override
    public String help() {
        return super.help() + " [nations] <column1> <column2> ...";
    }

    @Override
    public String desc() {
        return "Create a nation sheet, with the following column placeholders\n - {" +
                StringMan.join(DiscordUtil.getParser().getPlaceholders(), "}\n - {") + "}\n" +
                "Add `-s` to force update spies";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return super.checkPermission(server, user) && (Roles.MILCOM.has(user, server) || Roles.ECON.has(user, server) || Roles.INTERNAL_AFFAIRS.has(user, server));
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() < 2) return usage(event);

        GuildDB db = Locutus.imp().getGuildDB(guild);
        SpreadSheet sheet = null;
        Iterator<String> iter = args.iterator();
        while (iter.hasNext()) {
            String arg = iter.next();
            if (arg.startsWith("sheet:")) {
                sheet = SpreadSheet.create(arg);
                iter.remove();;
            }
        }

        List<String> header = new ArrayList<>(args);
        header.remove(0);
        for (int i = 0; i < header.size(); i++) {
            String arg = header.get(i);
            arg = arg.replace("{", "").replace("}", "").replace("=", "");
            header.set(i, arg);
        }

        Set<DBNation> nations = DiscordUtil.parseNations(guild, args.get(0));
        if (nations.isEmpty()) return "No nations found for `" + args.get(0) + "`";

        if (sheet == null) {
            sheet = SpreadSheet.create(Locutus.imp().getGuildDB(guild), GuildDB.Key.NATION_SHEET);
        }

        sheet.setHeader(header);
        if (flags.contains('s')) {
            Set<DBNation> toUpdate = new HashSet<>(nations);
            Set<Integer> alliances = new HashSet<>();
            for (DBNation nation : toUpdate) {
                if (nation.getPosition() > Rank.APPLICANT.id) {
                    alliances.add(nation.getAlliance_id());
                }
            }
            for (Integer allianceId : alliances) {
                if (new Alliance(allianceId).updateSpies(false)) {
                    toUpdate.removeIf(f -> f.getPosition() > Rank.APPLICANT.id && f.getAlliance_id() == allianceId);
                }
            }
            for (DBNation nation : toUpdate) {
                nation.updateSpies();
            }
        }

        for (DBNation nation : nations) {
            if (flags.contains('t') && nation.getCityTimerEpoch() != null && nation.getCityTimerEpoch() < TimeUtil.getTurn()) {
                nation.getPnwNation();
            }
            for (int i = 1; i < args.size(); i++) {
                String arg = args.get(i);
                String formatted = DiscordUtil.format(guild, event.getGuildChannel(), author, nation, arg);

                header.set(i - 1, formatted);
            }

            sheet.addRow(new ArrayList<>(header));
        }

        sheet.clear("A:ZZ");
        sheet.set(0, 0);

        return "<" + sheet.getURL() + ">";
//        I need, Nation name, nation link, score, war range, offensive/defensive slots open, military count (planes/tanks/ships/soldiers)
    }
}

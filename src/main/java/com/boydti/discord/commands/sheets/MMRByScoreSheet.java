package com.boydti.discord.commands.sheets;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.db.GuildDB;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.TimeUtil;
import com.boydti.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class MMRByScoreSheet extends Command {

    @Override
    public String help() {
        return super.help() + " <coalition-1> <coalition-2> ...";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {

        if (args.size() < 1) return usage(event);
        int minutesInactive;
        if (false || args.size() == 3) {
            minutesInactive = (int) (TimeUtil.timeToSec(args.get(2)) / 60);
        } else {
            minutesInactive = 2880;
        }

        GuildDB guildDb = checkNotNull(Locutus.imp().getGuildDB(event));
        SpreadSheet sheet = SpreadSheet.create(guildDb, GuildDB.Key.CITY_GRAPH_SHEET);

        List<String> header = new ArrayList<>();
        header.add("soldiers");
        header.add("soldiers");
        header.add("tanks");
        header.add("aircraft");
        header.add("ships");
        header.add("average");
        header.addAll(args);

        sheet.setHeader(header);

        List<String>[] rows = new List[1000];



        for (int column = 0; column < args.size(); column++) {
            String arg = args.get(column);
            Set<Integer> aaIds = DiscordUtil.parseAlliances(null, arg);
            List<DBNation> nations = Locutus.imp().getNationDB().getNations(aaIds);
            nations.removeIf(t -> t.getVm_turns() > 0 || t.getActive_m() > minutesInactive);


            for (int i = 0; i < 50; i++) {
                List<String> row = rows[i];
                int cities = i + 1;
                if (row == null) {
                    rows[i] = row = new ArrayList<>(header);
                    row.set(0, cities + "");
                }
            }
        }

        for (List<String> row : rows) {
            if (row != null) {
                sheet.addRow(row);
            }
        }

        sheet.clearAll();
        sheet.set(0, 0);
        return "<" + sheet.getURL() + ">";

//        return super.onCommand(event, guild, author, me, args, flags);
    }
}

package com.boydti.discord.commands.rankings;

import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.table.TimeDualNumericTable;
import com.boydti.discord.db.entities.DBWar;
import com.boydti.discord.db.entities.WarAttackParser;
import com.boydti.discord.db.entities.WarParser;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class WarsByTier extends Command {
    public WarsByTier() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public String help() {
        return super.help() + " <alliance|coalition> <alliance|coalition> <days>";
    }

    @Override
    public String desc() {
        return "Graph of total wars between two coalitions by city count\n" +
                "Add -o to only include offensives\n" +
                "Add -d to only include defensives\n" +
                "Add `-a` to only include active wars";
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.size() != 3) return usage();

        long start = System.currentTimeMillis() - TimeUtil.timeToSec(args.get(2)) * 1000L;
        if (flags.contains('a')) start = Math.max(start, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5));

        WarParser offensive = WarParser.of(guild, args.get(0), args.get(1), start, Long.MAX_VALUE);
        WarParser defensive = WarParser.of(guild, args.get(1), args.get(0), start, Long.MAX_VALUE);

        Map<Integer, Integer> col1 = new HashMap<>();
        Map<Integer, Integer> col2 = new HashMap<>();

        for (DBWar war : offensive.getWars().values()) {
            if (flags.contains('a') && !war.isActive()) continue;
            DBNation att = war.getNation(true);
            DBNation def = war.getNation(false);
            if (att == null || def == null) continue;
            Map<Integer, Integer> map1 = offensive.getIsPrimary().apply(war) ? col1 : col2;
            Map<Integer, Integer> map2 = map1 == col1 ? col2 : col1;
            if (!flags.contains('d')) map1.put(att.getCities(), map1.getOrDefault(att.getCities(), 0) + 1);
            if (!flags.contains('o')) map2.put(def.getCities(), map2.getOrDefault(def.getCities(), 0) + 1);
        }

        for (DBWar war : defensive.getWars().values()) {
            if (flags.contains('a') && !war.isActive()) continue;
            DBNation att = war.getNation(true);
            DBNation def = war.getNation(false);
            if (att == null || def == null) continue;
            Map<Integer, Integer> map1 = offensive.getIsPrimary().apply(war) ? col1 : col2;
            Map<Integer, Integer> map2 = map1 == col1 ? col2 : col1;
            if (!flags.contains('d')) map1.put(att.getCities(), map1.getOrDefault(att.getCities(), 0) + 1);
            if (!flags.contains('o')) map2.put(def.getCities(), map2.getOrDefault(def.getCities(), 0) + 1);
        }
        int min = 0;
        int max = 50;

        String title = "Wars by city count";
        if (flags.contains('o')) title = "Offensive " + title;
        if (flags.contains('d')) title = "Defensive " + title;
        if (flags.contains('a')) title = "Active " + title;

        TimeDualNumericTable<Void> table = new TimeDualNumericTable<Void>(title, "cities", "wars", "Coalition 1", "Coalition 2") {
            @Override
            public void add(long cities, Void ignore) {
                add(cities, col1.getOrDefault((int) cities, 0), col2.getOrDefault((int) cities, 0));
            }
        };
        for (int cities = min; cities <= max; cities++) {
            table.add(cities, (Void) null);
        }

        table.write(event.getGuildChannel());
        return null;
    }
}

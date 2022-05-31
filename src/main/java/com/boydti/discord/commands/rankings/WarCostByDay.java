package com.boydti.discord.commands.rankings;

import com.boydti.discord.Locutus;
import com.boydti.discord.commands.manager.Command;
import com.boydti.discord.commands.manager.CommandCategory;
import com.boydti.discord.commands.rankings.table.TimeDualNumericTable;
import com.boydti.discord.db.entities.DBWar;
import com.boydti.discord.db.entities.AttackCost;
import com.boydti.discord.pnw.DBNation;
import com.boydti.discord.util.discord.DiscordUtil;
import com.boydti.discord.util.MathMan;
import com.boydti.discord.util.PnwUtil;
import com.boydti.discord.util.TimeUtil;
import de.erichseifert.gral.data.DataTable;
import de.erichseifert.gral.data.Row;
import com.boydti.discord.apiv1.domains.subdomains.DBAttack;
import com.boydti.discord.apiv1.enums.AttackType;
import com.boydti.discord.apiv1.enums.MilitaryUnit;
import com.boydti.discord.apiv1.enums.ResourceType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class WarCostByDay extends Command {
    public WarCostByDay() {
        super(CommandCategory.GAME_INFO_AND_TOOLS, CommandCategory.MILCOM);
    }

    @Override
    public boolean checkPermission(Guild server, User user) {
        return true;
    }

    @Override
    public String help() {
        return "`" + super.help() + " <alliance|coalition> <alliance|coalition> <days>` OR `" + super.help() + " <war-url>`";
    }

    @Override
    public String desc() {
        return "Get a war breakdown by day\n" +
                "Add `-b` to show breakdown by attack type\n" +
                "Add `-f` to show Full cost\n" +
                "Add `-l` to show loot\n" +
                "Add `-c` to show consumption\n" +
                "Add `-a` to show ammunition usage\n" +
                "Add `-g` to show gasoline usage\n" +
                "Add `-u` to show unit losses\n" +
                "Add `-h` to show H-Bomb (nuke) losses\n" +
                "Add `-m` to show Missile losses\n" +
                "Add `-p` to show Plane losses\n" +
                "Add `-t` to show Tank losses\n" +
                "Add `-s` to show Soldier losses\n" +
                "Add `-i` to show Infra losses\n" +
                "Add `-o` to graph a running total";
    }

    @Override
    public String onCommand(MessageReceivedEvent event, Guild guild, User author, DBNation me, List<String> args, Set<Character> flags) throws Exception {
        if (args.isEmpty() || args.size() > 3 || (args.size() == 3 && args.get(0).equalsIgnoreCase(args.get(1))) || flags.isEmpty()) {
            return usage(event);
        }

        String arg0 = args.get(0);

        List<DBAttack> attacks = new LinkedList<>();
        Function<DBAttack, Boolean> isPrimary = null;
        Function<DBAttack, Boolean> isSecondary = null;
        String nameA = "Unknown";
        String nameB = "Unknown";

        if (args.size() == 1) {
            if (arg0.contains("/war=")) {
                arg0 = arg0.split("war=")[1];
                int warId = Integer.parseInt(arg0);
                DBWar war = Locutus.imp().getWarDb().getWar(warId);
                if (war == null) return "War not found (out of sync?)";

                attacks = Locutus.imp().getWarDb().getAttacksByWarId(warId);

                nameA = PnwUtil.getName(war.attacker_id, false);
                nameB = PnwUtil.getName(war.defender_id, false);
                isPrimary = a -> a.attacker_nation_id == war.attacker_id;
                isSecondary = b -> b.attacker_nation_id == war.defender_id;
            }
        } else if (args.size() == 2) {
            args = new ArrayList<>(args);
            args.add("*");
        }

        Map<Integer, DBNation> nations = Locutus.imp().getNationDB().getNations();

        if (args.size() == 3) {
            if (!MathMan.isInteger(args.get(2))) {
                return usage(event);
            }
            int days = MathMan.parseInt(args.get(2));
            long cutoffTurn = TimeUtil.getTurn(ZonedDateTime.now(ZoneOffset.UTC).minusDays(days).toEpochSecond() * 1000L);
            long cutoffMs = TimeUtil.getTimeFromTurn(cutoffTurn - cutoffTurn % 12);
            long warCutoff = cutoffMs - TimeUnit.DAYS.toMillis(5);

            Set<Integer> aaIdss1 = DiscordUtil.parseAlliances(guild, args.get(0));
            Set<Integer> aaIdss2 = DiscordUtil.parseAlliances(guild, args.get(1));
            if (aaIdss1 != null && aaIdss2 != null && !aaIdss1.isEmpty() && !aaIdss2.isEmpty()) {
                HashSet<Integer> alliances = new HashSet<>();
                alliances.addAll(aaIdss1);
                alliances.addAll(aaIdss2);
                List<DBWar> wars = Locutus.imp().getWarDb().getWars(alliances, warCutoff);
                Map<Integer, DBWar> warMap = new HashMap<>();
                for (DBWar war : wars) warMap.put(war.warId, war);
                attacks = Locutus.imp().getWarDb().getAttacksByWars(wars, cutoffMs);
                isPrimary = a -> {
                    DBWar war = warMap.get(a.war_id);
                    int aa1 = war.attacker_id == a.attacker_nation_id ? war.attacker_aa : war.defender_aa;
                    int aa2 = war.attacker_id == a.attacker_nation_id ? war.defender_aa : war.attacker_aa;
                    return aaIdss1.contains(aa1) && aaIdss2.contains(aa2);
                };
                isSecondary = a -> {
                    DBWar war = warMap.get(a.war_id);
                    int aa1 = war.attacker_id == a.attacker_nation_id ? war.attacker_aa : war.defender_aa;
                    int aa2 = war.attacker_id == a.attacker_nation_id ? war.defender_aa : war.attacker_aa;
                    return aaIdss2.contains(aa1) && aaIdss1.contains(aa2);
                };
                nameA = args.get(0);
                nameB = args.get(1);
            } else {
                Set<DBNation> alliances1 = DiscordUtil.parseNations(DiscordUtil.getDefaultGuild(event), args.get(0));
                Set<DBNation> alliances2 = DiscordUtil.parseNations(DiscordUtil.getDefaultGuild(event), args.get(1));
                Set<Integer> allIds = new HashSet<>();

                for (DBNation nation : alliances1) allIds.add(nation.getNation_id());
                for (DBNation nation : alliances2) allIds.add(nation.getNation_id());

                nameA = alliances1.size() == 1 ? alliances1.iterator().next().getNation() : args.get(0);
                nameB = alliances2.size() == 1 ? alliances2.iterator().next().getNation() : args.get(1);

                if (alliances1 == null || alliances1.isEmpty()) {
                    return "Invalid alliance: `" + args.get(0) + "`";
                }
                if (alliances2 == null || alliances2.isEmpty()) {
                    return "Invalid alliance: `" + args.get(1) + "`";
                }


                if (alliances1.size() == 1) {
                    attacks = Locutus.imp().getWarDb().getAttacks(alliances1.iterator().next().getNation_id(), cutoffMs);
                } else if (alliances2.size() == 1) {
                    attacks = Locutus.imp().getWarDb().getAttacks(alliances2.iterator().next().getNation_id(), cutoffMs);
                } else {
                    attacks = Locutus.imp().getWarDb().getAttacks(allIds, cutoffMs);
                }

                isPrimary = a -> {
                    DBNation n1 = nations.get(a.attacker_nation_id);
                    DBNation n2 = nations.get(a.defender_nation_id);
                    return n1 != null && n2 != null && alliances1.contains(n1) && alliances2.contains(n2);
                };
                isSecondary = a -> {
                    DBNation n1 = nations.get(a.attacker_nation_id);
                    DBNation n2 = nations.get(a.defender_nation_id);
                    return n1 != null && n2 != null && alliances1.contains(n2) && alliances2.contains(n1);
                };
            }
        }

        Map<Long, AttackCost> warCostByDay = new LinkedHashMap<>();

        Collections.sort(attacks, new Comparator<DBAttack>() {
            @Override
            public int compare(DBAttack o1, DBAttack o2) {
                return Long.compare(o1.epoch, o2.epoch);
            }
        });

        String finalNameA = nameA;
        String finalNameB = nameB;

        long now = System.currentTimeMillis();
        for (DBAttack attack : attacks) {
            if (attack.epoch > now) continue;
            long turn = TimeUtil.getTurn(attack.epoch);
            long day = turn / 12;
            AttackCost cost = warCostByDay.computeIfAbsent(day, f -> new AttackCost(finalNameA, finalNameB));
            cost.addCost(attack, isPrimary, isSecondary);
        }

        long min = Collections.min(warCostByDay.keySet());
        long max = Collections.max(warCostByDay.keySet());
        boolean total = flags.contains('o');
        List<TimeDualNumericTable<AttackCost>> tables = new ArrayList<>();
        if (flags.contains('i')) tables.add(new TimeDualNumericTable<AttackCost>("Infra Loss", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getInfraLost(true), cost.getInfraLost(false));
                processTotal(total, this);
            }
        });
        if (flags.contains('s')) tables.add(new TimeDualNumericTable<AttackCost>("Soldier Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.SOLDIER, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.SOLDIER, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('t')) tables.add(new TimeDualNumericTable<AttackCost>("Tank Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.TANK, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.TANK, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('p')) tables.add(new TimeDualNumericTable<AttackCost>("Plane Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.AIRCRAFT, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.AIRCRAFT, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('n')) tables.add(new TimeDualNumericTable<AttackCost>("Naval Ship Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.SHIP, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.SHIP, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('m')) tables.add(new TimeDualNumericTable<AttackCost>("Missile Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.MISSILE, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.MISSILE, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('h')) tables.add(new TimeDualNumericTable<AttackCost>("H-Bomb (nuke) Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getUnitsLost(true).getOrDefault(MilitaryUnit.NUKE, 0), cost.getUnitsLost(false).getOrDefault(MilitaryUnit.NUKE, 0));
                processTotal(total, this);
            }
        });
        if (flags.contains('u')) tables.add(new TimeDualNumericTable<AttackCost>("Unit Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, PnwUtil.convertedTotal(cost.getUnitCost(true)), PnwUtil.convertedTotal(cost.getUnitCost(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('g')) tables.add(new TimeDualNumericTable<AttackCost>("Gasoline", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getConsumption(true).getOrDefault(ResourceType.GASOLINE, 0d), cost.getConsumption(false).getOrDefault(ResourceType.GASOLINE, 0d));
                processTotal(total, this);
            }
        });
        if (flags.contains('a')) tables.add(new TimeDualNumericTable<AttackCost>("Ammunition", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, cost.getConsumption(true).getOrDefault(ResourceType.MUNITIONS, 0d), cost.getConsumption(false).getOrDefault(ResourceType.MUNITIONS, 0d));
                processTotal(total, this);
            }
        });
        if (flags.contains('c')) tables.add(new TimeDualNumericTable<AttackCost>("Consumption", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, PnwUtil.convertedTotal(cost.getConsumption(true)), PnwUtil.convertedTotal(cost.getConsumption(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('l')) tables.add(new TimeDualNumericTable<AttackCost>("Looted", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, -PnwUtil.convertedTotal(cost.getLoot(true)), -PnwUtil.convertedTotal(cost.getLoot(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('f')) tables.add(new TimeDualNumericTable<AttackCost>("Full Losses", "day", null, nameA, nameB) {
            @Override
            public void add(long day, AttackCost cost) {
                add(day, PnwUtil.convertedTotal(cost.getTotal(true)), PnwUtil.convertedTotal(cost.getTotal(false)));
                processTotal(total, this);
            }
        });
        if (flags.contains('b')) {
            for (AttackType attType : AttackType.values) {
                tables.add(new TimeDualNumericTable<AttackCost>("Num " + attType.getName(), "day", null, nameA, nameB) {
                    @Override
                    public void add(long day, AttackCost cost) {
                        ArrayList<DBAttack> a = new ArrayList<>(cost.getAttacks(true));
                        ArrayList<DBAttack> b = new ArrayList<>(cost.getAttacks(false));
                        a.removeIf(f -> f.attack_type != attType);
                        b.removeIf(f -> f.attack_type != attType);
                        add(day, a.size(), b.size());
                        processTotal(total, this);
                    }
                });
            }
        }

        AttackCost nullCost = new AttackCost();
        for (long day = min; day <= max; day++) {
            long dayOffset = day - min;
            AttackCost cost = warCostByDay.get(day);
            if (cost == null) {
                cost = nullCost;
            }

            for (TimeDualNumericTable<AttackCost> table : tables) {
                table.add(dayOffset, cost);
            }
        }

        for (TimeDualNumericTable<AttackCost> table : tables) {
            table.write(event.getGuildChannel());
        }

        return null;
    }

    private void processTotal(boolean total, TimeDualNumericTable table) {
        if (!total) return;
        DataTable data = table.getData();
        if (data.getRowCount() <= 1) return;
        Row row1 = data.getRow(data.getRowCount() - 2);
        Row row2 = data.getRow(data.getRowCount() - 1);

        Long day = (Long) row2.get(0);
        Double cost1A = (Double) row1.get(1);
        Double cost1B = (Double) row1.get(2);
        Double cost2A = (Double) row2.get(1);
        Double cost2B = (Double) row2.get(2);

        data.removeLast();
        data.add(day, cost1A + cost2A, cost1B + cost2B);
    }
}

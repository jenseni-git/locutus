package link.locutus.discord.db.entities.grant;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.city.JavaCity;
import link.locutus.discord.apiv1.enums.city.building.Building;
import link.locutus.discord.apiv1.enums.city.building.Buildings;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBCity;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.MMRInt;
import link.locutus.discord.pnw.json.CityBuild;
import link.locutus.discord.util.TimeUtil;
import link.locutus.discord.util.math.ArrayUtil;
import link.locutus.discord.util.offshore.Grant;
import org.jooq.meta.derby.sys.Sys;

import javax.annotation.Nullable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class BuildTemplate extends AGrantTemplate<Map<Integer, CityBuild>> {
    private final byte[] build;
    private final boolean onlyNewCities;
    private final MMRInt mmr;
    private final long allow_switch_after_days;
    private final boolean allow_switch_after_offensive;
    private final boolean allow_switch_after_infra;
    private final boolean allow_switch_after_land_or_project;
    private final boolean allow_all;

    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, ResultSet rs) throws SQLException {
        this(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, rs.getLong("date_created"), rs.getBytes("build"), rs.getBoolean("only_new_cities"),
                rs.getInt("mmr"),
                rs.getLong("allow_switch_after_days"),
                rs.getBoolean("allow_switch_after_offensive"),
                rs.getBoolean("allow_switch_after_infra"),
                rs.getBoolean("allow_switch_after_land_or_project"),
                rs.getBoolean("allow_all"),
                rs.getLong("expire"),
                rs.getBoolean("allow_ignore"),
                rs.getBoolean("repeatable")
        );
    }

    // create new constructor  with typed parameters instead of resultset
    public BuildTemplate(GuildDB db, boolean isEnabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal, long dateCreated, byte[] build, boolean onlyNewCities, int mmr,
                         long allow_switch_after_days,
                         boolean allow_switch_after_offensive,
                         boolean allow_switch_after_infra,
                         boolean allow_switch_after_land_or_project,
                         boolean allow_all, long expiryOrZero, boolean allowIgnore,
                         boolean repeatable
    ) {
        super(db, isEnabled, name, nationFilter, econRole, selfRole, fromBracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, dateCreated, expiryOrZero, allowIgnore, repeatable);
        this.build = build == null || build.length == 0 ? null : build;
        this.onlyNewCities = onlyNewCities;
        this.mmr = mmr <= 0 ? null : MMRInt.fromString(String.format("%04d", mmr));
        this.allow_switch_after_days = allow_switch_after_days;
        this.allow_switch_after_offensive = allow_switch_after_offensive;
        this.allow_switch_after_infra = allow_switch_after_infra;
        this.allow_switch_after_land_or_project = allow_switch_after_land_or_project;
        this.allow_all = allow_all;
    }

    @Override
    public String toInfoString(DBNation sender, DBNation receiver,  Map<Integer, CityBuild> parsed) {

        StringBuilder message = new StringBuilder();

        if(build != null)
            message.append("build: ```json\n" + new JavaCity().fromBytes(build).toJson() + "\n```\n");

        message.append("Only New Cities: `" + onlyNewCities + "`\n");
        if (mmr != null) {
            message.append("MMR: `" + String.format("%04d", mmr) + "`\n");
        }
        message.append("Allow Switch After Days: `" + allow_switch_after_days + "`\n");
        message.append("Allow Switch After Offensive: `" + allow_switch_after_offensive + "`\n");
        message.append("Allow Switch After Infra: `" + allow_switch_after_infra + "`\n");
        message.append("Allow All: `" + allow_all + "`\n");

        return message.toString();
    }

    @Override
    public String getCommandString(String name, String allowedRecipients, String econRole, String selfRole, String bracket, String useReceiverBracket, String maxTotal, String maxDay, String maxGranterDay, String maxGranterTotal, String allowExpire, String allowIgnore, String repeatable) {
        return CM.grant_template.create.build.cmd.create(name, allowedRecipients,
                build != null ? JavaCity.fromBytes(build).toJson() : null,
                mmr == null ? null : mmr.toString(),
                onlyNewCities ? "true" : null,
                allow_switch_after_days >0 ? allow_switch_after_days + "" : null,
                allow_switch_after_offensive ? "true" : null,
                allow_switch_after_infra ? "true" : null,
                allow_all ? "true" : null,
                allow_switch_after_land_or_project ? "true" : null,
                econRole, selfRole, bracket, useReceiverBracket, maxTotal, maxDay, maxGranterDay, maxGranterTotal, allowExpire, allowIgnore, repeatable, null).toString();
    }

    @Override
    public String toListString() {
        StringBuilder result = new StringBuilder(super.toListString());
        if (mmr != null) {
            // format int to 4 digits with 0 padding (before the number)
            String mmrString = mmr.toString();
            result.append(" | MMR=").append(mmrString);
        }
        if (onlyNewCities) {
            result.append(" | new_cities=true");
        }
        if (allow_switch_after_offensive) {
            result.append(" | damaged=allow");
        }
        return result.toString();
    }

    @Override
    public TemplateTypes getType() {
        return TemplateTypes.BUILD;
    }

    @Override
    public List<String> getQueryFields() {
        List<String> list = getQueryFieldsBase();
        list.add("build");
        list.add("only_new_cities");
        list.add("mmr");
        list.add("allow_switch_after_days");
        list.add("allow_switch_after_offensive");
        list.add("allow_switch_after_infra");
        list.add("allow_switch_after_land_or_project");
        list.add("allow_all");
        return list;
    }

    @Override
    public void setValues(PreparedStatement stmt) throws SQLException {
        stmt.setBytes(16, build == null ? new byte[0] : build);
        stmt.setBoolean(17, onlyNewCities);
        stmt.setLong(18, mmr == null ? -1 : mmr.toNumber());
        stmt.setLong(19, allow_switch_after_days);
        stmt.setBoolean(20, allow_switch_after_offensive);
        stmt.setBoolean(21, allow_switch_after_infra);
        stmt.setBoolean(22, allow_switch_after_land_or_project);
        stmt.setBoolean(23, allow_all);
    }

    @Override
    public Map<Integer, CityBuild> parse(DBNation receiver, String value) {
        CityBuild build;
        if (value == null) {
            JavaCity city;
            boolean isEmpty = this.build == null;
            if (!isEmpty) {
                for (byte b : this.build) {
                    if (b != 0) {
                        isEmpty = false;
                        break;
                    }
                }
            }
            if (!isEmpty) {
                city = JavaCity.fromBytes(this.build);
                if (mmr != null) {
                    city.setMMR(mmr);
                }
                build = city.toCityBuild();
            } else {
                // get infra in last city
                Map<Integer, DBCity> cities = receiver._getCitiesV3();
                // get city with largest id key
                int lastCity = Collections.max(cities.keySet());
                city = cities.get(lastCity).toJavaCity(receiver);
                // mmr
                if (mmr != null) {
                    city.setMMR(mmr);
                }
                city.zeroNonMilitary();
                city.optimalBuild(receiver, 5000);
                // generate
                build = city.toCityBuild();
            }
        } else {
            build = parse(getDb(), receiver, value, CityBuild.class);
        }

        Set<Integer> grantTo = getCitiesToGrantTo(receiver);

        if (grantTo.isEmpty()) {
            String message = "No eligable cities to grant to. Ensure you have not already received a build grant";
            if (onlyNewCities) {
                message += " and that you have built a city in the past 10 days";
            }
            throw new IllegalArgumentException(message);
        }

        // get max infra
        double maxInfra = 0;
        for (Map.Entry<Integer, DBCity> entry : receiver._getCitiesV3().entrySet()) {
            maxInfra = Math.max(maxInfra, entry.getValue().getInfra());
        }
        // ensure build matches infra level
        {
            JavaCity jc = new JavaCity(build);
            if (jc.getRequiredInfra() > maxInfra) {
                throw new IllegalArgumentException("Build requires more infra than the receiver has: " + jc.getRequiredInfra() + " > " + maxInfra);
            }
            // ensure no buildings are negative
            for (Building building : Buildings.values()) {
                if (jc.get(building) < 0) {
                    throw new IllegalArgumentException("Build has negative " + building.name() + " buildings");
                }
            }
            // no more than 2 power plants
            if (jc.get(Buildings.NUCLEAR_POWER) > 2) {
                throw new IllegalArgumentException("Build has more than 2 nuclear power plants");
            }
            if (jc.get(Buildings.WIND_POWER) > 2) {
                throw new IllegalArgumentException("Build has more than 2 wind power plants");
            }
            if (jc.get(Buildings.COAL_MINE) > 8) {
                throw new IllegalArgumentException("Build has more than 8 coal mines");
            }
            if (jc.get(Buildings.OIL_POWER) > 8) {
                throw new IllegalArgumentException("Build has more than 8 oil power");
            }
            // 5,5,5,3 max military buildings
            if (jc.get(Buildings.BARRACKS) > 5) {
                throw new IllegalArgumentException("Build has more than 5 barracks");
            }
            if (jc.get(Buildings.FACTORY) > 5) {
                throw new IllegalArgumentException("Build has more than 5 factories");
            }
            if (jc.get(Buildings.HANGAR) > 5) {
                throw new IllegalArgumentException("Build has more than 5 hangars");
            }
            if (jc.get(Buildings.DRYDOCK) > 3) {
                throw new IllegalArgumentException("Build has more than 3 drydocks");
            }
        }
        // return map of city and build
        Map<Integer, CityBuild> map = new HashMap<>();
        for (Integer city : grantTo) {
            map.put(city, build);
        }
        return map;
    }

    @Override
    public List<Grant.Requirement> getDefaultRequirements(@Nullable DBNation sender, @Nullable DBNation receiver, Map<Integer, CityBuild> build) {
            List<Grant.Requirement> list = super.getDefaultRequirements(sender, receiver, build);
            list.addAll(getRequirements(sender, receiver, this, build));
            return list;
    }

    public static List<Grant.Requirement> getRequirements(DBNation sender, DBNation receiver, BuildTemplate template, Map<Integer, CityBuild> parsed) {
        List<Grant.Requirement> list = new ArrayList<>();

        // cap at 4k infra worth of buildings
        list.add(new Grant.Requirement("Build must NOT have more than 80 buildings (4k infra)", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                for (CityBuild value : parsed.values()) {
                    if (new JavaCity(value).getImpTotal() > 80) {
                        return false;
                    }
                }
                return true;
            }
        }));

        if (template == null || template.onlyNewCities) {
            list.add(new Grant.Requirement("Must have purchased a city in the past 10 days (when `onlyNewCities: True`)", true, new Function<DBNation, Boolean>() {
                @Override
                public Boolean apply(DBNation receiver) {
                    return receiver.getCitiesSince(TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119)) > 0;
                }
            }));
        }

        list.add(new Grant.Requirement("Nation must NOT have received a new city build grant (when `repeatable: False`)", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation receiver) {
                if (template.isRepeatable()) return true;
                if (template.allow_switch_after_offensive || template.allow_switch_after_infra || template.allow_switch_after_land_or_project) return true;

                List<GrantTemplateManager.GrantSendRecord> records = template.getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId());

                for(GrantTemplateManager.GrantSendRecord record : records) {
                    if (template.allow_switch_after_days > 0 && record.date < System.currentTimeMillis() - TimeUnit.DAYS.toMillis(template.allow_switch_after_days)) {
                        continue;
                    }
                    if (receiver.getCitiesSince(record.date) == 0 && record.grant_type == TemplateTypes.BUILD) {
                        return false;
                    }
                }

                return true;
            }
        }));

        return list;
    }

    private Set<Integer> getCitiesToGrantTo(DBNation receiver) {
        Map<Integer, DBCity> cities = receiver._getCitiesV3();
        Map<Integer, Long> createDate = new HashMap<>();

        long buildDate = 0;
        long infraDate = 0;
        long projectOrLandDate = 0;
        for (Map.Entry<Integer, DBCity> entry : cities.entrySet()) {
            DBCity city = entry.getValue();
            createDate.put(entry.getKey(), city.created);
        }

        long lastAttackDate = 0;
        if (allow_switch_after_offensive) {
            // get attacks where attacker
            lastAttackDate = getLatestAttackDate(receiver);
        }

        // get grants
        for (GrantTemplateManager.GrantSendRecord record : getDb().getGrantTemplateManager().getRecordsByReceiver(receiver.getId())) {
            // buildDate = Math.max(buildDate, record.date);
            switch (record.grant_type) {
                case BUILD -> buildDate = Math.max(buildDate, record.date);
                case INFRA -> infraDate = Math.max(infraDate, record.date);
                case PROJECT, LAND -> projectOrLandDate = Math.max(projectOrLandDate, record.date);
            }
        }
        Set<Integer> citiesToGrant = new HashSet<>();
        long cutoff = onlyNewCities ? TimeUtil.getTimeFromTurn(TimeUtil.getTurn() - 119) : 0;
        for (Map.Entry<Integer, Long> entry : createDate.entrySet()) {
            long date = entry.getValue();
            if (date < cutoff) continue;
            boolean allowGrant = true;
            if (buildDate > date) {
                allowGrant = infraDate > buildDate && allow_switch_after_infra;
                if (projectOrLandDate > buildDate && allow_switch_after_land_or_project) {
                    allowGrant = true;
                }
                if (lastAttackDate > date && allow_switch_after_offensive) {
                    allowGrant = true;
                }
                if (allow_all) {
                    allowGrant = true;
                }
            }
            if (allowGrant) {
                citiesToGrant.add(entry.getKey());
            }
        }
        return citiesToGrant;
    }

    @Override
    public double[] getCost(GuildDB db, DBNation sender, DBNation receiver, Map<Integer, CityBuild> builds) {
        double[] cost = ResourceType.getBuffer();
        Map<Integer, JavaCity> existing = receiver.getCityMap(true);
        for (Map.Entry<Integer, JavaCity> entry : existing.entrySet()) {
            JavaCity from = entry.getValue();
            CityBuild build = builds.get(entry.getKey());
            if (build == null) continue;
            JavaCity to = new JavaCity(build);

            to.calculateCost(from, cost, false, false);
        }
        return cost;
    }

    @Override
    public DepositType.DepositTypeInfo getDepositType(DBNation receiver, Map<Integer, CityBuild> build) {
        int cities = 0;
        if (onlyNewCities) {
            cities = build.size();
        } else {
            cities = receiver.getCities();
        }
        return DepositType.BUILD.withAmount(cities);
    }

    @Override
    public String getInstructions(DBNation sender, DBNation receiver, Map<Integer, CityBuild> parsed) {
        StringBuilder instructions = new StringBuilder();
        if (parsed.size() == 1) {
            int id = parsed.keySet().iterator().next();
            instructions.append("Go to <https://politicsandwar.com/city/improvements/import/id=" + id + "> and import the build:\n");
        } else if (parsed.size() == receiver.getCities()) {
            instructions.append("Go to <https://politicsandwar.com/city/improvements/bulk-import/> and import the build:\n");
        } else {
            Set<Integer> ids = parsed.keySet();
            instructions.append("Go to <https://politicsandwar.com/city/improvements/import/id=> with the following city ids:\n");
            for (int id : ids) {
                instructions.append("- ").append(id).append("\n");
            }
            instructions.append("and import the build:\n");
        }
        instructions.append("```json\n");
        if (!parsed.isEmpty()) {
            CityBuild cityBuild = parsed.values().iterator().next();
            instructions.append(cityBuild.toString());
        }
        instructions.append("\n```");
        return instructions.toString();
    }

    @Override
    public Class<Map<Integer, CityBuild>> getParsedType() {
        return (Class<Map<Integer, CityBuild>>) TypeToken.getParameterized(Map.class, Integer.class, CityBuild.class).getRawType();
    }
}

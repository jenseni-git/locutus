package link.locutus.discord.db.entities.grant;

import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.NationFilter;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.db.entities.NationFilterString;
import link.locutus.discord.db.entities.Transaction2;
import link.locutus.discord.util.offshore.Grant;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class AGrantTemplate {

    private final GuildDB db;
    private boolean enabled;
    private String name;
    private NationFilter nationFilter;
    private long econRole;
    private long selfRole;
    private int fromBracket;
    private boolean useReceiverBracket;
    private int maxTotal;
    private int maxDay;
    private int maxGranterTotal;
    private int maxGranterDay;

    public AGrantTemplate(GuildDB db, boolean enabled, String name, NationFilter nationFilter, long econRole, long selfRole, int fromBracket, boolean useReceiverBracket, int maxTotal, int maxDay, int maxGranterDay, int maxGranterTotal) {
        this.db = db;
        this.enabled = enabled;
        this.name = name;
        this.nationFilter = nationFilter;
        this.econRole = econRole;
        this.selfRole = selfRole;
        this.fromBracket = fromBracket;
        this.useReceiverBracket = useReceiverBracket;
        this.maxTotal = maxTotal;
        this.maxDay = maxDay;
        this.maxGranterDay = maxGranterDay;
        this.maxGranterTotal = maxGranterTotal;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public abstract String toListString();

    public List<Transaction2> getGrantedTotal() {
        return getGranted(Long.MAX_VALUE);
    }

    public List<Transaction2> getGranted(long time) {
        return getGranted(time, null);
    }

    public List<Transaction2> getGranted(long time, DBNation sender) {
        // TODO
    }

    public List<Transaction2> getGrantedTotal(DBNation sender) {
        return getGranted(Long.MAX_VALUE, sender);
    }

    public String toFullString(DBNation sender, DBNation receiver) {
        // sender or receiver may be null
    }

    public boolean hasRole(Member author) {
        List<Role> roles = author.getRoles();
        for (Role role : roles) {
            if (role.getIdLong() == econRole) {
                return true;
            }
        }
        return false;
    }

    public boolean hasSelfRole(Member author) {
        List<Role> roles = author.getRoles();
        for (Role role : roles) {
            if (role.getIdLong() == selfRole) {
                return true;
            }
        }
        return false;
    }

    public GuildDB getDb() {
        return db;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NationFilter getNationFilter() {
        return nationFilter;
    }

    public long getEconRoleId() {
        return econRole;
    }

    public long getSelfRoleId() {
        return selfRole;
    }

    public int getFromBracket() {
        return fromBracket;
    }

    public boolean isUseReceiverBracket() {
        return useReceiverBracket;
    }

    public int getMaxTotal() {
        return maxTotal;
    }

    public int getMaxDay() {
        return maxDay;
    }

    public int getMaxGranterDay() {
        return maxGranterDay;
    }

    public int getMaxGranterTotal() {
        return maxGranterTotal;
    }

    public abstract TemplateTypes getType();

    public List<Grant.Requirement> getDefaultRequirements() {
        List<Grant.Requirement> list = new ArrayList<>();

       // errors.computeIfAbsent(nation, f -> new ArrayList<>()).add("Nation was not found in guild");
        list.add(new Grant.Requirement("Nation is not verified: " + CM.register.cmd.toSlashMention(), false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                User user = nation.getUser();
                return user != null;
            }
        }));
        list.add(new Grant.Requirement("Nation is not a member of an alliance", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getAlliance_id() == 0;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is not a member of an alliance", econGov, f -> f.getPosition() > 1));
        list.add(new Grant.Requirement("Nation is not a member of an alliance", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getPosition() > 1;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is in VM", econGov, f -> f.getVm_turns() == 0));
        list.add(new Grant.Requirement("Nation is in VM", false, new Function<DBNation, Boolean>() {
            @Override
            public Boolean apply(DBNation nation) {
                return nation.getVm_turns() == 0;
            }
        }));
//                grant.addRequirement(new Grant.Requirement("Nation is not in the alliance: " + alliance, econGov, f -> alliance != null && f.getAlliance_id() == alliance.getAlliance_id()));
//
//                Role temp = Roles.TEMP.toRole(db.getGuild());
//                grant.addRequirement(new Grant.Requirement("Nation not eligible for grants (has role: " + temp.getName() + ")", econStaff, f -> !member.getRoles().contains(temp)));
//
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 24h", econStaff, f -> f.getActive_m() < 1440));
//                grant.addRequirement(new Grant.Requirement("Nation is not active in past 7d", econGov, f -> f.getActive_m() < 10000));
//
//                grant.addRequirement(new Grant.Requirement("Nation does not have 5 raids going", econStaff, f -> f.getCities() >= 10 || f.getOff() >= 5));
//
//                if (nation.getNumWars() > 0) {
//                    // require max barracks
//                    grant.addRequirement(new Grant.Requirement("Nation does not have 5 barracks in each city (raiding)", econStaff, f -> f.getMMRBuildingStr().charAt(0) == '5'));
//                }
//
//                if (nation.getCities() >= 10 && nation.getNumWars() == 0) {
//                    // require 5 hangars
//                    grant.addRequirement(new Grant.Requirement("Nation does not have 5 hangars in each city (peacetime)", econStaff, f -> f.getMMRBuildingStr().charAt(2) == '5'));
//                    if (type == TemplateTypes.CITY || type == TemplateTypes.INFRA || type == TemplateTypes.LAND) {
//                        grant.addRequirement(new Grant.Requirement("Nation does not have 0 factories in each city (peacetime)", econStaff, f -> f.getMMRBuildingStr().charAt(1) == '0'));
//                        grant.addRequirement(new Grant.Requirement("Nation does not have max aircraft", econStaff, f -> f.getMMR().charAt(2) == '5'));
//                    }
//                }
//
//                if (type != TemplateTypes.WARCHEST) grant.addRequirement(new Grant.Requirement("Nation is beige", econStaff, f -> !f.isBeige()));
//                grant.addRequirement(new Grant.Requirement("Nation is gray", econStaff, f -> !f.isGray()));
//                grant.addRequirement(new Grant.Requirement("Nation is blockaded", econStaff, f -> !f.isBlockaded()));
//
//                // TODO no disburse past 5 days during wartime
//                // TODO 2d seniority and 5 won wars for initial 1.7k infra grants
//                grant.addRequirement(new Grant.Requirement("Nation does not have 10d seniority", econStaff, f -> f.allianceSeniority() >= 10));

        return list;
    }

    protected List<String> getQueryFieldsBase() {
        List<String> list = new ArrayList<>();
        list.add("enabled");
        list.add("name");
        list.add("nation_filter");
        list.add("econ_role");
        list.add("self_role");
        list.add("from_bracket");
        list.add("use_receiver_bracket");
        list.add("max_total");
        list.add("max_day");
        list.add("max_granter_day");
        list.add("max_granter_total");
        return list;
    }

    public abstract List<String> getQueryFields();

    public String createQuery() {
        List<String> fields = getQueryFields();
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO `" + this.getType().getTable() + "` (");
        for (int i = 0; i < fields.size(); i++) {
            sb.append("`" + fields.get(i) + "`");
            if (i < fields.size() - 1) sb.append(", ");
        }
        sb.append(") VALUES (");
        for (int i = 0; i < fields.size(); i++) {
            sb.append("?");
            if (i < fields.size() - 1) sb.append(", ");
        }
        sb.append(")");
        return sb.toString();
    }

    protected void setValuesBase(PreparedStatement stmt) throws SQLException {
        stmt.setBoolean(1, this.isEnabled());
        stmt.setString(2, this.getName());
        stmt.setString(3, this.getNationFilter().getFilter());
        stmt.setLong(4, this.getEconRoleId());
        stmt.setLong(5, this.getSelfRoleId());
        stmt.setLong(6, this.getFromBracket());
        stmt.setBoolean(7, this.isUseReceiverBracket());
        stmt.setInt(8, this.getMaxTotal());
        stmt.setInt(9, this.getMaxDay());
        stmt.setInt(10, this.getMaxGranterDay());
        stmt.setInt(11, this.getMaxGranterTotal());
    }

    public abstract void setValues(PreparedStatement stmt) throws SQLException;
}

package link.locutus.discord.commands.manager.v2.impl.pw.filter;

import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.NoFormat;
import link.locutus.discord.commands.manager.v2.binding.annotation.Switch;
import link.locutus.discord.commands.manager.v2.binding.bindings.Placeholders;
import link.locutus.discord.commands.manager.v2.binding.bindings.TypedFunction;
import link.locutus.discord.commands.manager.v2.binding.validator.ValidatorStore;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.CommandUsageException;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.impl.discord.permission.RolePermission;
import link.locutus.discord.commands.manager.v2.impl.pw.refs.CM;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.DefaultPlaceholders;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.SheetTemplate;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.entities.DBNation;
import link.locutus.discord.pnw.PNWUser;
import link.locutus.discord.user.Roles;
import link.locutus.discord.util.MathMan;
import link.locutus.discord.util.PnwUtil;
import link.locutus.discord.util.discord.DiscordUtil;
import link.locutus.discord.util.sheet.SpreadSheet;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NationPlaceholders extends Placeholders<DBNation> {
    private final Map<String, NationAttribute> customMetrics = new HashMap<>();

    public NationPlaceholders(ValueStore store, ValidatorStore validators, PermissionHandler permisser) {
        super(DBNation.class, store, validators, permisser);
        this.getCommands().registerCommands(new DefaultPlaceholders());
    }

    @NoFormat
    @Command(desc = "Add an alias for a selection of Nations")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addSelectionAlias(@Me JSONObject command, @Me GuildDB db, String name, Set<DBNation> nations) {
        return _addSelectionAlias(command, db, name, nations, "nations");
    }

    @NoFormat
    @Command(desc = "Add columns to a Nation sheet")
    @RolePermission(value = {Roles.INTERNAL_AFFAIRS_STAFF, Roles.MILCOM, Roles.ECON_STAFF, Roles.FOREIGN_AFFAIRS_STAFF, Roles.ECON, Roles.FOREIGN_AFFAIRS}, any = true)
    public String addColumns(@Me JSONObject command, @Me GuildDB db, @Me IMessageIO io, @Me User author, @Switch("s") SheetTemplate sheet,
                             @Default TypedFunction<DBNation, String> column1,
                             @Default TypedFunction<DBNation, String> column2,
                             @Default TypedFunction<DBNation, String> column3,
                             @Default TypedFunction<DBNation, String> column4,
                             @Default TypedFunction<DBNation, String> column5,
                             @Default TypedFunction<DBNation, String> column6,
                             @Default TypedFunction<DBNation, String> column7,
                             @Default TypedFunction<DBNation, String> column8,
                             @Default TypedFunction<DBNation, String> column9,
                             @Default TypedFunction<DBNation, String> column10,
                             @Default TypedFunction<DBNation, String> column11,
                             @Default TypedFunction<DBNation, String> column12,
                             @Default TypedFunction<DBNation, String> column13,
                             @Default TypedFunction<DBNation, String> column14,
                             @Default TypedFunction<DBNation, String> column15,
                             @Default TypedFunction<DBNation, String> column16,
                             @Default TypedFunction<DBNation, String> column17,
                             @Default TypedFunction<DBNation, String> column18,
                             @Default TypedFunction<DBNation, String> column19,
                             @Default TypedFunction<DBNation, String> column20,
                             @Default TypedFunction<DBNation, String> column21,
                             @Default TypedFunction<DBNation, String> column22,
                             @Default TypedFunction<DBNation, String> column23,
                             @Default TypedFunction<DBNation, String> column24) throws GeneralSecurityException, IOException {
        return Placeholders._addColumns(this, command,db, io, author, sheet,
                column1, column2, column3, column4, column5, column6, column7, column8, column9, column10,
                column11, column12, column13, column14, column15, column16, column17, column18, column19, column20,
                column21, column22, column23, column24);
    }

    @Override
    public String getDescription() {
        return CM.help.find_nation_placeholder.cmd.toSlashMention();
    }

    public List<NationAttribute> getMetrics(ValueStore store) {
        List<NationAttribute> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            try {
                TypedFunction<DBNation, ?> typeFunction = formatRecursively(store, id, null, 0, false);
                if (typeFunction == null) continue;

                NationAttribute metric = new NationAttribute(cmd.getPrimaryCommandId(), cmd.simpleDesc(), typeFunction.getType(), typeFunction);
                result.add(metric);
            } catch (IllegalStateException | CommandUsageException ignore) {
                continue;
            }
        }
        return result;
    }

    public NationAttributeDouble getMetricDouble(ValueStore<?> store, String id) {
        return getMetricDouble(store, id, false);
    }

    public NationAttribute getMetric(ValueStore<?> store, String id, boolean ignorePerms) {
        TypedFunction<DBNation, ?> typeFunction = formatRecursively(store, "{" + id + "}", null, 0, true);
        if (typeFunction == null) return null;
        return new NationAttribute<>(id, "", typeFunction.getType(), typeFunction);
    }

    public NationAttributeDouble getMetricDouble(ValueStore store, String id, boolean ignorePerms) {
        TypedFunction<DBNation, ?> typeFunction = formatRecursively(store, "{" + id + "}", null, 0, true);
        if (typeFunction == null) return null;

        TypedFunction<DBNation, ?> genericFunc = typeFunction;
        Function<DBNation, Double> func;
        Type type = typeFunction.getType();
        if (type == int.class || type == Integer.class) {
            func = nation -> ((Integer) genericFunc.apply(nation)).doubleValue();
        } else if (type == double.class || type == Double.class) {
            func = nation -> (Double) genericFunc.apply(nation);
        } else if (type == short.class || type == Short.class) {
            func = nation -> ((Short) genericFunc.apply(nation)).doubleValue();
        } else if (type == byte.class || type == Byte.class) {
            func = nation -> ((Byte) genericFunc.apply(nation)).doubleValue();
        } else if (type == long.class || type == Long.class) {
            func = nation -> ((Long) genericFunc.apply(nation)).doubleValue();
        } else if (type == boolean.class || type == Boolean.class) {
            func = nation -> ((Boolean) genericFunc.apply(nation)) ? 1d : 0d;
        } else {
            return null;
        }
        return new NationAttributeDouble(id, "", func);
    }

    public List<NationAttributeDouble> getMetricsDouble(ValueStore store) {
        List<NationAttributeDouble> result = new ArrayList<>();
        for (CommandCallable cmd : getFilterCallables()) {
            String id = cmd.aliases().get(0);
            NationAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        for (Map.Entry<String, NationAttribute> entry : customMetrics.entrySet()) {
            String id = entry.getKey();
            NationAttributeDouble metric = getMetricDouble(store, id, true);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    public static Set<DBNation> getByRole(Guild guild, String name, Role role) {
        if (role == null) throw new IllegalArgumentException("Invalid role: `" + name + "`");
        List<Member> members = guild.getMembersWithRoles(role);
        Set<DBNation> nations = new LinkedHashSet<>();
        for (Member member : members) {
            DBNation nation = DBNation.getByUser(member.getUser());
            if (nation != null) nations.add(nation);
        }
        return nations;
    }

    @Override
    public Set<DBNation> parseSingleElem(ValueStore store, String name) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (name.equals("*")) {
            return new ObjectArraySet<>(Locutus.imp().getNationDB().getNations().values());
        } else if (name.contains("tax_id=")) {
            int taxId = PnwUtil.parseTaxId(name);
            return Locutus.imp().getNationDB().getNationsByBracket(taxId);
        } else if (SpreadSheet.isSheet(nameLower)) {
            Set<DBNation> nations = SpreadSheet.parseSheet(name, List.of("nation", "leader"), true,
                    s -> switch (s.toLowerCase(Locale.ROOT)) {
                        case "nation" -> 0;
                        case "leader" -> 1;
                        default -> null;
                    }, (type, input) -> {
                        return switch (type) {
                            case 0 -> Locutus.imp().getNationDB().getNation(input);
                            case 1 -> Locutus.imp().getNationDB().getNationByLeader(input);
                            default -> null;
                        };
                    });
            return nations;
        }  else if (nameLower.startsWith("aa:")) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name.split(":", 2)[1].trim());
            if (alliances == null) throw new IllegalArgumentException("Invalid alliance: `" + name + "`");
            Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(alliances);
            return allianceMembers;
            // role
        } else if (nameLower.startsWith("<@&") && guild != null) {
            Role role = DiscordUtil.getRole(guild, name);
            return getByRole(guild, name, role);
        } else if (MathMan.isInteger(nameLower)) {
            long id = Long.parseLong(nameLower);
            if (id > Integer.MAX_VALUE && guild != null) {
                Role role = DiscordUtil.getRole(guild, name);
                return getByRole(guild, name, role);
            }
            DBNation nation = DBNation.getById((int) id);
            if (nation != null) return Set.of(nation);
            DBAlliance alliance = DBAlliance.get((int) id);
            if (alliance != null) return alliance.getNations();
        }

        Set<DBNation> nations = new LinkedHashSet<>();
        boolean containsAA = nameLower.contains("/alliance/");
        DBNation nation = containsAA ? null : DiscordUtil.parseNation(name, true);
        if (nation == null || containsAA) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name);
            if (alliances == null) {
                Role role = guild != null ? DiscordUtil.getRole(guild, name) : null;
                if (role != null) {
                    return getByRole(guild, name, role);
                } else if (name.contains("#")) {
                    String[] split = name.split("#");
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                    if (user != null) {
                        nation = Locutus.imp().getNationDB().getNation(user.getNationId());
                    }
                    if (nation == null) {
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                    }
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                }
            } else {
                Set<DBNation> allianceMembers = Locutus.imp().getNationDB().getNations(alliances);
                nations.addAll(allianceMembers);
            }
        } else {
            nations.add(nation);
        }
        return nations;
    }

    @Override
    public Predicate<DBNation> parseSingleFilter(ValueStore store, String name) {
        String nameLower = name.toLowerCase(Locale.ROOT);
        Guild guild = (Guild) store.getProvided(Key.of(Guild.class, Me.class), false);
        if (name.equals("*")) {
            return f -> true;
        } else if (name.contains("tax_id=")) {
            int taxId = PnwUtil.parseTaxId(name);
            return f -> f.getTax_id() == taxId;
        } else if (SpreadSheet.isSheet(nameLower)) {
            Set<DBNation> nations = SpreadSheet.parseSheet(name, List.of("nation", "leader"), true,
                    s -> switch (s.toLowerCase(Locale.ROOT)) {
                        case "nation" -> 0;
                        case "leader" -> 1;
                        default -> null;
                    }, (type, input) -> {
                        return switch (type) {
                            case 0 -> Locutus.imp().getNationDB().getNation(input);
                            case 1 -> Locutus.imp().getNationDB().getNationByLeader(input);
                            default -> null;
                        };
                    });
            Set<Integer> ids = nations.stream().map(DBNation::getId).collect(Collectors.toSet());
            return f -> ids.contains(f.getId());
        }  else if (nameLower.startsWith("aa:")) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name.split(":", 2)[1].trim());
            if (alliances == null) throw new IllegalArgumentException("Invalid alliance: `" + name + "`");
            return f -> alliances.contains(f.getAlliance_id());
        } else if (MathMan.isInteger(nameLower)) {
            int id = Integer.parseInt(nameLower);
            return f -> f.getId() == id || f.getAlliance_id() == id;
        }

        boolean containsAA = nameLower.contains("/alliance/");
        DBNation nation = containsAA ? null : DiscordUtil.parseNation(name, true);
        if (nation == null) {
            Set<Integer> alliances = DiscordUtil.parseAllianceIds(guild, name);
            if (alliances == null) {
                Role role = guild != null ? DiscordUtil.getRole(guild, name) : null;
                if (role != null) {
                    return f -> {
                        User user = f.getUser();
                        if (user == null) return false;
                        Member member = role.getGuild().getMember(user);
                        if (member == null) return false;
                        return member.getRoles().contains(role);
                    };
                } else if (name.contains("#")) {
                    String[] split = name.split("#");
                    PNWUser user = Locutus.imp().getDiscordDB().getUser(null, split[0], name);
                    if (user != null) {
                        nation = Locutus.imp().getNationDB().getNation(user.getNationId());
                    }
                    if (nation == null) {
                        throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                    }
                    int id = nation.getId();
                    return f -> f.getId() == id;
                } else {
                    throw new IllegalArgumentException("Invalid nation/aa: `" + name + "`");
                }
            } else {
                return f -> alliances.contains(f.getAlliance_id());
            }
        } else {
            int id = nation.getId();
            return f -> f.getId() == id;
        }
    }
}
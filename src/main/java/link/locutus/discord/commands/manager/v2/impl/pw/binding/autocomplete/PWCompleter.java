package link.locutus.discord.commands.manager.v2.impl.pw.binding.autocomplete;

import com.google.gson.reflect.TypeToken;
import link.locutus.discord.Locutus;
import link.locutus.discord.apiv1.enums.AttackType;
import link.locutus.discord.apiv1.enums.Continent;
import link.locutus.discord.apiv1.enums.DepositType;
import link.locutus.discord.apiv1.enums.MilitaryUnit;
import link.locutus.discord.apiv1.enums.ResourceType;
import link.locutus.discord.apiv1.enums.WarType;
import link.locutus.discord.apiv1.enums.city.project.Project;
import link.locutus.discord.apiv1.enums.city.project.Projects;
import link.locutus.discord.apiv3.enums.AlliancePermission;
import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.FunctionConsumerParser;
import link.locutus.discord.commands.manager.v2.binding.Key;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.command.ArgumentStack;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.GuildCoalition;
import link.locutus.discord.commands.manager.v2.impl.discord.binding.annotation.NationDepositLimit;
import link.locutus.discord.commands.manager.v2.impl.pw.NationPlaceholder;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttribute;
import link.locutus.discord.commands.manager.v2.impl.pw.binding.NationAttributeDouble;
import link.locutus.discord.commands.manager.v2.impl.pw.commands.UnsortedCommands;
import link.locutus.discord.commands.manager.v2.impl.pw.filter.NationPlaceholders;
import link.locutus.discord.db.GuildDB;
import link.locutus.discord.db.entities.*;
import link.locutus.discord.db.entities.DBAlliance;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.db.guild.GuildKey;
import link.locutus.discord.pnw.AllianceList;
import link.locutus.discord.pnw.NationOrAlliance;
import link.locutus.discord.pnw.NationOrAllianceOrGuild;
import link.locutus.discord.util.SpyCount;
import link.locutus.discord.util.StringMan;
import link.locutus.discord.commands.manager.v2.binding.annotation.Autocomplete;
import link.locutus.discord.commands.manager.v2.binding.annotation.AllianceDepositLimit;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.util.task.ia.IACheckup;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.lang.reflect.Type;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PWCompleter extends BindingHelper {

    @Autocomplete
    @Binding(types={Coalition.class})
    public List<String> Coalition(String input) {
        return StringMan.completeEnum(input, Coalition.class);
    }

    @Autocomplete
    @Binding(types={DepositType.DepositTypeInfo.class})
    public List<String> DepositTypeInfo(String input) {
        return StringMan.completeEnum(input, DepositType.class);
    }

    @Autocomplete
    @Binding(types={AlliancePermission.class})
    public List<String> AlliancePermission(String input) {
        return StringMan.completeEnum(input, AlliancePermission.class);
    }

    @Autocomplete
    @Binding(types={DBAlliancePosition.class})
    public List<Map.Entry<String, String>> DBAlliancePosition(@Me GuildDB db, String input) {
        AllianceList alliances = db.getAllianceList();
        List<DBAlliancePosition> options = new ArrayList<>(alliances.getPositions());
        options.add(DBAlliancePosition.REMOVE);
        options.add(DBAlliancePosition.APPLICANT);

        options = StringMan.getClosest(input, options, DBAlliancePosition::getName, OptionData.MAX_CHOICES, true);
        return options.stream().map(new Function<DBAlliancePosition, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(DBAlliancePosition f) {
                return Map.entry(f.getQualifiedName(), f.getInputName());
            }
        }).collect(Collectors.toList());
    }


    @Autocomplete
    @GuildCoalition
    @Binding(types={String.class})
    public List<String> GuildCoalition(@Me GuildDB db, String input) {
        List<String> options = new ArrayList<>();
        for (Coalition coalition : Coalition.values()) {
            options.add(coalition.name());
        }
        for (String coalition : db.getCoalitions().keySet()) {
            if (Coalition.getOrNull(coalition) != null) continue;
            options.add(coalition);
        }
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={DBNation.class})
    public List<String> DBNation(String input) {
        if (input.isEmpty()) return null;

        List<DBNation> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options = StringMan.getClosest(input, options, DBNation::getName, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(DBNation::getNation).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={NationOrAlliance.class})
    public List<Map.Entry<String, String>> NationOrAlliance(String input) {
        if (input.isEmpty()) return null;

        List<NationOrAlliance> options = new ArrayList<>(Locutus.imp().getNationDB().getNations().values());
        options.addAll(Locutus.imp().getNationDB().getAlliances());

        options = StringMan.getClosest(input, options, new Function<NationOrAlliance, String>() {
            @Override
            public String apply(NationOrAlliance f) {
                return f.getName();
            }
        }, OptionData.MAX_CHOICES, true, true);

        return options.stream().map(new Function<NationOrAlliance, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(NationOrAlliance f) {
                return Map.entry(f.getName(), f.getTypePrefix() + ":" + f.getId());
            }
        }).collect(Collectors.toList());
    }

    @Autocomplete
    @Binding(types={AllianceMetric.class})
    public List<String> AllianceMetric(String input) {
        return StringMan.completeEnum(input, AllianceMetric.class);
    }

    @Autocomplete
    @Binding(types={Project.class})
    public List<String> Project(String input) {
        List<Project> options = Arrays.asList(Projects.values);;
        options = StringMan.getClosest(input, options, Project::name, OptionData.MAX_CHOICES, true);
        return options.stream().map(Project::name).collect(Collectors.toList());
    }


    @Autocomplete
    @Binding(types={NationPlaceholder.class})
    public List<String> NationPlaceholder(String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        List<String> options = new ArrayList<>(placeholders.getKeys());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={GuildSetting.class})
    public List<String> setting(String input) {
        List<String> options = Arrays.asList(GuildKey.values()).stream().map(f -> f.name()).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={NationAttributeDouble.class})
    public List<String> NationPlaceholder(ArgumentStack stack, String input) {
        NationPlaceholders placeholders = Locutus.imp().getCommandManager().getV2().getNationPlaceholders();
        List<String> options = placeholders.getMetricsDouble(stack.getStore())
                .stream().map(NationAttribute::getName).collect(Collectors.toList());
        return StringMan.getClosest(input, options, f -> f, OptionData.MAX_CHOICES, true);
    }

    @Autocomplete
    @Binding(types={UnsortedCommands.ClearRolesEnum.class})
    public List<String> ClearRolesEnum(String input) {
        return StringMan.completeEnum(input, UnsortedCommands.ClearRolesEnum.class);
    }

    public final Set<DBAlliance> ALLIANCES_KEY = null;
    public final Set<DBAlliance> NATIONS_KEY = null;
    public final Set<NationOrAllianceOrGuild> NATIONS_OR_ALLIANCE_OR_GUILD_KEY = null;
    public final Set<NationOrAlliance> NATIONS_OR_ALLIANCE_KEY = null;
    public final Set<AllianceMetric> ALLIANCE_METRIC_KEY = null;
    public final Set<NationAttributeDouble> NATION_METRIC_KEY = null;


    {
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, SpyCount.Operation.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(SpyCount.Operation.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, IACheckup.AuditType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(IACheckup.AuditType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, Continent.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(Continent.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, WarStatus.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(WarStatus.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, WarType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(WarType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Set.class, AttackType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    return StringMan.autocompleteCommaEnum(AttackType.class, input.toString(), OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(List.class, ResourceType.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<ResourceType> options = new ArrayList<>(ResourceType.valuesList);
                    return StringMan.autocompleteComma(input.toString(), options, ResourceType::valueOf, ResourceType::getName, ResourceType::getName, OptionData.MAX_CHOICES);
                }));
            });
        }
        {
            Key key = Key.of(TypeToken.getParameterized(Map.class, MilitaryUnit.class, Long.class).getType(), Autocomplete.class);
            addBinding(store -> {
                store.addParser(key, new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<String> options = Arrays.asList(MilitaryUnit.values).stream().map(Enum::name).collect(Collectors.toList());
                    return StringMan.completeMap(options, null, input.toString());
                }));
            });
        }
        {
            Type type = TypeToken.getParameterized(Map.class, ResourceType.class, Double.class).getType();
            Consumer<ValueStore<?>> binding = store -> {
                Key key = Key.of(type, Autocomplete.class);
                FunctionConsumerParser parser = new FunctionConsumerParser(key, (BiFunction<ValueStore, Object, Object>) (valueStore, input) -> {
                    List<String> options = ResourceType.valuesList.stream().map(Enum::name).collect(Collectors.toList());
                    return StringMan.completeMap(options, null, input.toString());
                });
                store.addParser(key, parser);
                store.addParser(Key.of(type, Autocomplete.class, AllianceDepositLimit.class), parser);
                store.addParser(Key.of(type, Autocomplete.class, NationDepositLimit.class), parser);
            };
            addBinding(binding);
        }
    }
}
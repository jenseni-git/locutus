package link.locutus.discord.commands.manager.v2.impl.pw.commands;

import link.locutus.discord.Locutus;
import link.locutus.discord.commands.manager.v2.binding.ValueStore;
import link.locutus.discord.commands.manager.v2.binding.annotation.Command;
import link.locutus.discord.commands.manager.v2.binding.annotation.Default;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import link.locutus.discord.commands.manager.v2.binding.annotation.Range;
import link.locutus.discord.commands.manager.v2.command.CommandCallable;
import link.locutus.discord.commands.manager.v2.command.ICommand;
import link.locutus.discord.commands.manager.v2.command.IMessageBuilder;
import link.locutus.discord.commands.manager.v2.command.IMessageIO;
import link.locutus.discord.commands.manager.v2.command.ParametricCallable;
import link.locutus.discord.commands.manager.v2.impl.pw.CM;
import link.locutus.discord.commands.manager.v2.perm.PermissionHandler;
import link.locutus.discord.db.guild.GuildSetting;
import link.locutus.discord.gpt.CommandEmbedding;
import link.locutus.discord.gpt.EmbeddingType;
import link.locutus.discord.gpt.PWEmbedding;
import link.locutus.discord.gpt.PWGPTHandler;
import link.locutus.discord.gpt.SettingEmbedding;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HelpCommands {
    public HelpCommands() {
    }

    public PWGPTHandler getGPT() {
        return Locutus.imp().getCommandManager().getV2().getPwgptHandler();
    }

//    @Command
//    public void use_command(@Me IMessageIO io, ValueStore store, ParametricCallable command, String query) {
//        StringBuilder prompt = new StringBuilder();
//
//        OpenAiService service = getGPT().getHandler().getService();
//    }
//    @Command
//    public void find_placeholders(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("3") int num_results) {
//
//    }

    @Command
    public String moderation_check(String input) throws IOException {
        JSONObject result = getGPT().getHandler().checkModeration(input);
        return result.toString();
    }

    @Command
    public String command(@Me IMessageIO io, ValueStore store, PermissionHandler permisser, ICommand command) {
        String body = command.toBasicMarkdown(store, permisser, "/", false);
        // todo spoilers
        String title = "/" + command.getFullPath();
        if (body.length() > 4096) {
            return "#" + title + "\n" + body;
        }
        io.create().embed(title, body).send();
        return null;
    }

    @Command
    public void find_setting(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("5") int num_results) {
        try {
            IMessageBuilder msg = io.create();
            msg.append("**All settings: **" + CM.settings.info.cmd.create(null, null, "true") + "\n");
            msg.append("- More Info: " + CM.settings.info.cmd.create("YOUR_KEY_HERE", null, null) + "\n");
            msg.append("- To Delete: " + CM.settings.delete.cmd.create("YOUR_KEY_HERE") + "\n\n");

            List<Map.Entry<PWEmbedding, Double>> closest = getGPT().getClosest(store, query, num_results, Set.of(EmbeddingType.Configuration));
            for (int i = 0; i < closest.size(); i++) {
                Map.Entry<PWEmbedding, Double> entry = closest.get(i);
                SettingEmbedding embed = (SettingEmbedding) entry.getKey();
                GuildSetting obj = embed.getObj();

                msg.append("__**" + (i + 1) + ".**__ ");
                msg.append("**" + obj.name() + "**: " + obj.getCommandMention() + "\n");

                String desc = obj.help();
                int tickIndex = desc.indexOf("```");
                int optionsIndex = desc.toLowerCase().indexOf("options");
                if (tickIndex != -1 || optionsIndex != -1) {
                    if (tickIndex == -1) tickIndex = Integer.MAX_VALUE;
                    if (optionsIndex == -1) optionsIndex = Integer.MAX_VALUE;
                    int first = Math.min(tickIndex, optionsIndex);
                    desc = desc.substring(0, first);
                }
                desc = desc.trim();

                msg.append("> " + desc.replaceAll("\n", "\n > "));
                msg.append("\n");
            }
            msg.send();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Command
    public void find_command(@Me IMessageIO io, ValueStore store, String query, @Range(min = 1, max = 25) @Default("5") int num_results) {
        try {
            IMessageBuilder msg = io.create();
            List<Map.Entry<PWEmbedding, Double>> closest = getGPT().getClosest(store, query, num_results, Set.of(EmbeddingType.Command));
            System.out.println("Results " + num_results + " " + closest.size());
            for (int i = 0; i < closest.size(); i++) {
                Map.Entry<PWEmbedding, Double> entry = closest.get(i);
                CommandEmbedding embed = (CommandEmbedding) entry.getKey();
                ParametricCallable command = embed.getObj();

                // /command [arguments]
                String mention = Locutus.imp().getSlashCommands().getSlashMention(command.getFullPath());
                String path = command.getFullPath();
                if (mention == null) {
                    mention = "**/" + path + "**";
                }
                String help = command.help(store).replaceFirst(path, "").trim();
                msg.append("__**" + (i + 1) + ".**__ ");
                msg.append(mention);
                if (!help.isEmpty()) {
                    msg.append(" " + help);
                }
                msg.append("\n");
                msg.append("> " + command.simpleDesc().replaceAll("\n", "\n > "));
                msg.append("\n");
            }
            msg.send();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

//    @Command
//    public String setting(@Default GuildDB key, @Default String query) {
//        return null;
//    }
//
//    @Command
//    public String test(String question) {
//        return "Not implemented";
//    }
//
//    @Command
//    public String nationStat(@Default NationPlaceholder placeholder, @Default String query) {
//        return null;
//    }

//
//    @Command
//    public String type(@Default @Argument Key type, @Default String query) {
//        /*
//        Help on this argument (description, examples)
//
//        List of commands that use this argument
//         */
//        return null;
//    }
//
//    @Command
//    public String permission(@Default @Permission Key type, @Default String query) {
//        return null;
//    }
//

//
//

}

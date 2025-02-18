package link.locutus.discord.web.commands.binding;

import link.locutus.discord.commands.manager.v2.binding.BindingHelper;
import link.locutus.discord.commands.manager.v2.binding.annotation.Binding;
import io.javalin.http.Context;
import link.locutus.discord.commands.manager.v2.binding.annotation.Me;
import net.dv8tion.jda.api.entities.Guild;

public class JavalinBindings extends BindingHelper {
    @Binding
    public Context context() {
        throw new IllegalStateException("No context set in command locals");
    }
}

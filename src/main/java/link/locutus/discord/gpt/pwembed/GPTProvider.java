package link.locutus.discord.gpt.pwembed;

import link.locutus.discord.db.GuildDB;
import link.locutus.discord.gpt.ISummarizer;
import link.locutus.discord.gpt.imps.IText2Text;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public abstract class GPTProvider implements Closeable {
    private final IText2Text text2Text;

    public GPTProvider(IText2Text text2Text) {
        this.text2Text = text2Text;
    }

    public IText2Text getText2Text() {
        return text2Text;
    }

    public abstract boolean hasPermission(GuildDB db, User user, boolean checkLimits);
    public abstract Future<String> submit(GuildDB db, User user, Map<String, String> options, String input);

    public String getId() {
        return text2Text.getId();
    }

    public Map<String, String> getOptions() {
        return text2Text.getOptions();
    }

    public int getSize(String text) {
        return text2Text.getSize(text);
    }

    public int getSizeCap() {
        return text2Text.getSizeCap();
    }

    public abstract ProviderType getType();
}

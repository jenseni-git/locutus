package link.locutus.discord.gpt.imps;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import link.locutus.discord.db.entities.EmbeddingSource;
import link.locutus.discord.gpt.GptHandler;
import link.locutus.discord.gpt.IEmbeddingDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public abstract class EmbeddingAdapter<T> implements IEmbeddingAdapter<T> {

    private final Map<Long, T> objectsByHash;
    private final Map<T, Long> hashesByObject;
    private final EmbeddingSource source;
    private final Set<T> objects;
    private boolean moderate;

    public EmbeddingAdapter(EmbeddingSource source, Set<T> objects) {
        this.source = source;
        this.objectsByHash = new Long2ObjectOpenHashMap<>();
        this.hashesByObject = new LinkedHashMap<>();
        this.objects = objects;
        this.moderate = true;
    }

    public EmbeddingAdapter setModerate(boolean moderate) {
        this.moderate = moderate;
        return this;
    }

    public Map<Long, T> getObjectsByHash() {
        return objectsByHash;
    }

    public long getHash(T obj) {
        return hashesByObject.get(obj);
    }

    public abstract String getDescription(T obj);
    public abstract String getExpanded(T obj);

    public T getObject(long hash) {
        return objectsByHash.get(hash);
    }

    public EmbeddingAdapter createEmbeddings(GptHandler handler) {
        List<T> values = new ArrayList<>(objects);

        hashesByObject.clear();
        objectsByHash.clear();

        List<Long> hashes = handler.registerEmbeddings(source,
                values.stream().map(new Function<T, Map.Entry<String, String>>() {
            @Override
            public Map.Entry<String, String> apply(T t) {
                return getDescriptionAndExpandedPair(t);
            }
        }), moderate, true);

        for (int i = 0; i < values.size(); i++) {
            long hash = hashes.get(i);
            T value = values.get(i);
            objectsByHash.put(hash, value);
            hashesByObject.put(value, hash);
        }
        return this;
    }

}

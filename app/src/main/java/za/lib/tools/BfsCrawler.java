package za.lib.tools;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import za.lib.HttpClient.Response;

// todo DfsCrawler is the same, but with a stack
public abstract class BfsCrawler<T> extends AsyncRuntime<T> {
    private final Queue<T> queue = new ArrayDeque<>();
    protected final Set<T> seen = new HashSet<>();  // default behavior for seen items

    @Override
    public abstract Collection<T> map(Response res);

    /** custom logic when encountering a new item */
    protected abstract void process(T item);

    /**
     * possible for client to override this with custom logic
     * @return true if the item has been seen before
     */
    protected boolean preview(T item) {
        if (seen.contains(item)) {
            return true;
        }

        seen.add(item);
        return false;
    }

    @Override
    public final void step(Collection<T> items) {
        queue.addAll(items);
        while (!queue.isEmpty()) {
            T item = queue.remove();
            if (!preview(item)) {
                process(item);
            }
        }
    }
}

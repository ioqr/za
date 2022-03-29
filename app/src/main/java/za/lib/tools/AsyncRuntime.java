package za.lib.tools;

import java.util.Collection;
import java.util.function.Consumer;

import za.lib.HttpClient.Response;

/**
 * A general runtime that processes collections of T's
 * Use this for single-node only, cannot handle distributed workloads (yet)
 */
public abstract class AsyncRuntime<T> implements Consumer<Response> {
    /** process a collection of items */
    protected abstract void step(Collection<T> items);

    /** convert a response into a collection of items to be later processed */
    protected abstract Collection<T> map(Response res);

    @Override
    public final void accept(Response res) {
        step(map(res));
    }
}

package za.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import za.lib.Message;
import za.lib.Plugin;
import za.lib.Registry;

public class RegistryImpl implements Registry {
    private final Set<Plugin> plugins = new HashSet<>();
    private final Map<String, List<Consumer<Message>>> subscriptions = new HashMap<>();
    
    @Override
    public void register(Plugin plugin) {
        Objects.requireNonNull(plugin);
        var clazz = plugin.getClass();
        if (plugins.contains(plugin)) {
            throw new RuntimeException("Plugin registered twice: " + clazz.getName());
        }
        plugins.add(plugin);
    }

    @Override
    public void subscribe(String channel, Consumer<Message> onMessage) {
        subscriptions
            .computeIfAbsent(channel, _k -> new ArrayList<>())
            .add(onMessage);
    }

    public List<Consumer<Message>> getSubscribers(String channel) {
        var callbacks = subscriptions.get(channel);
        if (callbacks == null) {
            return List.of();
        }
        return List.copyOf(callbacks);
    }
}

package za.lib;

import java.util.function.Consumer;

public interface Registry {
    void register(Plugin plugin);
    void subscribe(String channel, Consumer<Message> onMessage);
}

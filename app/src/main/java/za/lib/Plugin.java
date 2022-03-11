package za.lib;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Single threaded engine interface
 * 
 * Plugin design tips:
 * -  small (single-peer): Call http() in a loop (see lib.tools.AsyncRuntime)
 * -  large (multi-peer): Do not loop. Use in() and out() apis with only a few http() calls
 */
public abstract class Plugin {
    private Config config_ = null;
   
    // only public for people who want to build their own engine
    // don't construct Config objects in your plugins as the fields
    // will occasionally change
    public record Config(
            Logger log,
            Registry registry,
            HttpClient http,
            Map<String, Object> userSettings,
            BiConsumer<String, Object> in,
            BiConsumer<String, Object> out,
            String id
    ) {}

    public void onEnable() {}

    public void onDisable() {}

    public Logger log() {
        return config_.log();
    }

    public Registry registry() {
        return config_.registry();
    }

    public HttpClient http() {
        return config_.http();
    }

    public Map<String, Object> userSettings() {
        return config_.userSettings();
    }

    public void in(String channel, Object message) {
        config_.in().accept(channel, message);
    }

    public void out(String channel, Object message) {
        config_.out().accept(channel, message);
    }

    public String id() {
        return config_.id();
    }
}

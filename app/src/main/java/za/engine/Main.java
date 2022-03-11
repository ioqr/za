package za.engine;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

import za.engine.http.HttpClientFactory;
import za.lib.*;
//import za.plugins.*;

public class Main {
    private static final String PLUGIN_CONFIG_FIELD_NAME = "config_";
    private static final String BAD_ARGS = "Expected command line args like: --key1 value1 --key2 value2 ...";
    private static final int HTTP_THREADS = 2;
    private static final int HTTP_CONCURRENCY = 32;  // total guess
    private static final Logger LOG = Logger.verbose(Main.class, System.out, System.err);

    public static void main(String[] args) throws Exception {
        var argsMap = toArgsMap(args);
        var plugins = List.<Plugin>of(/*new MyPlugin()*/);  // TODO configurable
        if (plugins.size() == 0) {
            LOG.warn("No plugins installed");
        }
        var registry = new RegistryImpl();
        var httpLogger = Logger.verbose(HttpClient.class);
        var http = HttpClientFactory.apache(HTTP_THREADS, HTTP_CONCURRENCY, httpLogger).get();  // TODO configurable
        var rabbitMQ = getRabbitMQClientFactory(argsMap).get();
        LOG.info("Installing plugins");
        install(plugins, plugin -> {
            var logger = Logger.verbose(plugin.getClass(), System.out, System.err);
            var id = genPluginId(plugin);
            var in = getPluginInCallback(plugin, rabbitMQ);
            var out = getPluginOutCallback(plugin, rabbitMQ);
            return new Plugin.Config(logger, registry, http, Map.of(), in, out, id);
        });
        LOG.info("Enabling plugins");
        plugins.forEach(Plugin::onEnable);
        LOG.info("Listening for traffic on queue");
        var messageHandler = new MessageHandler(registry::getSubscribers);
        rabbitMQ.receiveBlocking(RabbitMQClient.INPUT_QUEUE_NAME, message -> {
            messageHandler.handle(message);
            try {
                http.drainFully();
            } catch (InterruptedException e) {
                LOG.error("Failed to drain pending http requests");
                e.printStackTrace();
                System.exit(1);
            }
        });
    }
    
    private static Map<String, String> toArgsMap(String[] args) {
        Map<String, String> argsMap = new HashMap<>();
        if (args.length % 2 != 0) {
            throw new RuntimeException(BAD_ARGS);
        }
        // args follow format: --param1 value1 --param2 value2 ...
        for (int i = 0; i < args.length; i += 2) {
            var key = args[i];
            var value = args[i+1];
            if (!key.startsWith("--")) {
                throw new RuntimeException(BAD_ARGS);
            }
            argsMap.put(key.substring(2), value);
        }
        return argsMap;
    }
    
    private static void install(List<Plugin> plugins, Function<Plugin, Plugin.Config> configFactory) {
        plugins.stream()
        .map(plugin -> {
            try {
                var config = configFactory.apply(plugin);
                var field = plugin.getClass().getField(PLUGIN_CONFIG_FIELD_NAME);
                field.setAccessible(true);
                field.set(plugin, config);
                field.setAccessible(false);
                return plugin;
            } catch (Exception e) {
                LOG.error("Failed to install plugin: " + plugin.getClass().getName());
                e.printStackTrace();
                return null;
            }
        })
        .filter(plugin -> plugin != null)
        .forEach(plugin -> plugin.registry().register(plugin));
    }
    
    private static RabbitMQClient.Factory getRabbitMQClientFactory(Map<String, String> argsMap) {
        var username = argsMap.getOrDefault("rmq-username", "guest");
        var password = argsMap.getOrDefault("rmq-password", "guest");
        var virtualHost = argsMap.getOrDefault("rmq-virtualhost", "/");
        var host = argsMap.getOrDefault("rmq-host", "localhost");
        var port = Integer.parseInt(argsMap.getOrDefault("rmq-port", "5671"));
        return RabbitMQClient.factory(username, password, virtualHost, host, port);
    }

    private static String genPluginId(Plugin plugin) {
        var random = new SecureRandom();
        var bytes = new byte[16];  // 128-bit for globally unique ids
        random.nextBytes(bytes);
        return plugin.getClass() + "_" + new BigInteger(bytes).toString(16);
    }

    private static BiConsumer<String, Object> getPluginInCallback(Plugin plugin, RabbitMQClient rabbitMQ) {
        return (channel, message) -> {
            try {
                var encoded = MessageHandler.encode("in", channel, plugin, message);
                rabbitMQ.send(RabbitMQClient.INPUT_QUEUE_NAME, encoded);
            } catch (Exception e) {
                LOG.error("Failed to send input for %s", plugin.id());
                e.printStackTrace();
            }
        };
    }

    private static BiConsumer<String, Object> getPluginOutCallback(Plugin plugin, RabbitMQClient rabbitMQ) {
        return (channel, message) -> {
            try {
                var encoded = MessageHandler.encode("out", channel, plugin, message);
                rabbitMQ.send(RabbitMQClient.OUTPUT_QUEUE_NAME, encoded);
            } catch (Exception e) {
                LOG.error("Failed to send output for %s", plugin.id());
                e.printStackTrace();
            }
        };
    }
}

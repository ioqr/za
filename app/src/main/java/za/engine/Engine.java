package za.engine;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import za.engine.http.DrainableHttpClient;
import za.engine.http.HttpClientFactory;
import za.lib.HttpClient;
import za.lib.Logger;
import za.lib.Plugin;

public final class Engine {
    private static final String PLUGIN_CONFIG_FIELD_NAME = "config_";
    private static final String BAD_ARGS = "Expected command line args like: --key1 value1 --key2 value2 ...";

    private volatile boolean started;
    private final Logger log;
    private final List<Plugin> plugins;
    private final Supplier<DrainableHttpClient> httpFactory;
    private final RabbitMQClient.Factory rabbitFactory;

    private Engine(
        Logger log,
        List<Plugin> plugins,
        Supplier<DrainableHttpClient> http,         
        RabbitMQClient.Factory rabbit) {
        this.started = false;
        this.log = log;
        this.plugins = plugins;
        this.httpFactory = http;
        this.rabbitFactory = rabbit;
    }

    /**
     * Za engine factory (library main); with cli arg parsing
     * 
     * args follow format: --param1 value1 --param2 value2 ...
     * 
     * @param args pass command line arguments here
     * @param plugins variadic list of plugins
     * @return a new Za engine
     * @throws EngineRuntimeException
     */
    public static Engine create(String[] args, Plugin... plugins) throws EngineRuntimeException {
        try {
            Map<String, String> argsMap = new LinkedHashMap<>();
            if (args.length % 2 != 0) {
                throw new EngineRuntimeException(BAD_ARGS);
            }
            for (int i = 0; i < args.length; i += 2) {
                var key = args[i];
                var value = args[i+1];
                if (!key.startsWith("--")) {
                    throw new EngineRuntimeException(BAD_ARGS);
                }
                argsMap.put(key.substring(2).toLowerCase(), value);
            }
            return Engine.create(argsMap, plugins);
        } catch (EngineRuntimeException e) {
            throw e;  // re-throw exception thrown by delegate call to Engine.create(Map, Plugin[])
        } catch (Exception e) {
            throw new FailedToCreateEngineException(e);
        }
    }

    /**
     * Za engine factory (library main)
     * 
     * @param args pass command line arguments here
     * @param plugins variadic list of plugins
     * @return a new Za engine
     * @throws EngineRuntimeException
     */
    public static Engine create(Map<String, String> args, Plugin... plugins) throws EngineRuntimeException {
        try {
            var verbose = Boolean.parseBoolean(args.getOrDefault("logger-verbose", "true"));
            var httpThreads = Integer.parseInt(args.getOrDefault("http-threads", "2"));
            var httpConcurrency = Integer.parseInt(args.getOrDefault("http-concurrency", "32"));
            var rmqUsername = args.getOrDefault("rmq-username", "guest");
            var rmqPassword = args.getOrDefault("rmq-password", "guest");
            var rmqVirtualHost = args.getOrDefault("rmq-virtualhost", "/");
            var rmqHost = args.getOrDefault("rmq-host", "localhost");
            var rmqPort = Integer.parseInt(args.getOrDefault("rmq-port", "5671"));
            return new Engine(
                verbose ? Logger.silent() : Logger.verbose(Engine.class),
                List.copyOf(Arrays.asList(plugins)),
                HttpClientFactory.apache(httpThreads, httpConcurrency, Logger.verbose(HttpClient.class)),
                RabbitMQClient.factory(rmqUsername, rmqPassword, rmqVirtualHost, rmqHost, rmqPort));
        } catch (Exception e) {
            throw new FailedToCreateEngineException(e); 
        }
    }

    public synchronized void start() {
        if (started) {
            throw new EngineAlreadyStartedException();
        }
        try {
            started = true;
            if (plugins.size() == 0) {
                log.warn("No plugins installed");
            }
            var registry = new RegistryImpl();
            var http = httpFactory.get();
            var rabbit = rabbitFactory.get();
            log.info("Installing plugins");
            install(plugins, plugin -> {
                var logger = Logger.verbose(plugin.getClass(), System.out, System.err);
                var id = genPluginId(plugin);
                var in = getPluginInCallback(plugin, rabbit);
                var out = getPluginOutCallback(plugin, rabbit);
                return new Plugin.Config(logger, registry, http, Map.of(), in, out, id);
            });
            log.info("Enabling plugins");
            plugins.forEach(Plugin::onEnable);
            log.info("Listening for traffic on queue");
            var messageHandler = new MessageHandler(registry::getSubscribers);
            try {
                rabbit.receiveBlocking(RabbitMQClient.INPUT_QUEUE_NAME, message -> {
                    messageHandler.handle(message);
                    try {
                        http.drainFully();
                    } catch (InterruptedException e) {
                        throw new EngineFailedToDrainHttpException(e);
                    }
                });
            } catch (IOException | TimeoutException e) {
                throw new EngineRuntimeException(e);
            }
        } catch (EngineRuntimeException e) {
            throw new EngineFailedToStartException(e);
        }
    }

    private void install(List<Plugin> plugins, Function<Plugin, Plugin.Config> configFactory) {
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
                log.error("Failed to install plugin: " + plugin.getClass().getName());
                e.printStackTrace();
                return null;
            }
        })
        .filter(plugin -> plugin != null)
        .forEach(plugin -> plugin.registry().register(plugin));
    }
    
    private String genPluginId(Plugin plugin) {
        var random = new SecureRandom();
        var bytes = new byte[16];  // 128-bit for globally unique ids
        random.nextBytes(bytes);
        return plugin.getClass() + "_" + new BigInteger(bytes).toString(16);
    }

    private BiConsumer<String, Object> getPluginInCallback(Plugin plugin, RabbitMQClient rabbitMQ) {
        return (channel, message) -> {
            try {
                var encoded = MessageHandler.encode("in", channel, plugin, message);
                rabbitMQ.send(RabbitMQClient.INPUT_QUEUE_NAME, encoded);
            } catch (Exception e) {
                log.error("Failed to send input for %s", plugin.id());
                e.printStackTrace();
            }
        };
    }

    private BiConsumer<String, Object> getPluginOutCallback(Plugin plugin, RabbitMQClient rabbitMQ) {
        return (channel, message) -> {
            try {
                var encoded = MessageHandler.encode("out", channel, plugin, message);
                rabbitMQ.send(RabbitMQClient.OUTPUT_QUEUE_NAME, encoded);
            } catch (Exception e) {
                log.error("Failed to send output for %s", plugin.id());
                e.printStackTrace();
            }
        };
    }

    public sealed static class EngineRuntimeException extends RuntimeException {
        private EngineRuntimeException(String message) {
            super(message);
        }

        private EngineRuntimeException(Exception e) {
            super(e);
        }
    }

    public final static class FailedToCreateEngineException extends EngineRuntimeException {
        private FailedToCreateEngineException(Exception e) {
            super(e);
        }
    }

    public final class EngineFailedToStartException extends EngineRuntimeException {
        private EngineFailedToStartException(EngineRuntimeException e) {
            super(e);
        }
    }

    public final class EngineFailedToDrainHttpException extends EngineRuntimeException {
        private EngineFailedToDrainHttpException(InterruptedException e) {
            super(e);
        }
    }

    public final class EngineAlreadyStartedException extends EngineRuntimeException {
        private EngineAlreadyStartedException() {
            super("engine.start() should only be called once");
        }
    }
}

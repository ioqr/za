package za.engine;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import za.lib.Logger;
import za.lib.Message;
import za.lib.Plugin;

public class MessageHandler {
    private final Function<String, List<Consumer<Message>>> getSubscribers;
    private final Logger log = Logger.verbose(getClass());

    public MessageHandler(Function<String, List<Consumer<Message>>> getSubscribers) {
        this.getSubscribers = getSubscribers;
    }

    /**
     * Encode a message 
     * 
     * header format:
     * <version>:<context>:<channel>:<plugin_id>:(null | dk:<dk>:<body> | :<body>)
     * examples:
     * -- 
     * 1:in:myChannel:MyPlugin_af749dc3:null
     * 1:in:myChannel:MyPlugin_af749dc3:dk:723894573:eyASDFJSONBASE64
     * 1:in:myChannel:MyPlugin_af749dc3::eyASDFJSONBASE64
     */
    public static String encode(String context, String channel, Plugin plugin, Object message) throws JsonProcessingException {
        var list = new ArrayList<String>();
        list.add("1");  // version number
        list.add(context);
        list.add(channel);
        list.add(plugin.id());
        list.add(encode(message));
        return String.join(":", list);
    }

    private static String encode(Object message) throws JsonProcessingException {
        if (message == null) {
            return "null";
        }
        // figure out the optional data key
        var sb = new StringBuilder();
        if (message instanceof Map m) {
            String dk = null;
            // maps of size 1 automatically set a data key
            if (m.size() == 1) {
                Map.Entry entry = (Entry) m.entrySet().stream().findFirst().get();
                if (entry.getKey() instanceof String keyStr) {
                    dk = keyStr;
                }
            // other maps can set a data key with '_dk'
            } else {
                var val = m.get("_dk");
                if (val != null && val instanceof String valStr) {
                    dk = valStr;
                }
            }
            if (dk != null && m.get(dk) instanceof String interpolatedValStr) {
                sb.append("dk:");
                sb.append(Base64.getEncoder().encodeToString(interpolatedValStr.getBytes()));
            }
        }
        sb.append(":");
        var payload = new ObjectMapper().writeValueAsString(message);
        payload = Base64.getEncoder().encodeToString(payload.getBytes());
        sb.append(payload);
        return sb.toString();
    }

    /**
     * Parse and dispatch message
     */
    // TODO refactor
    public void handle(String message) {
        try {
            String[] parts = message.split(":");
            String version = parts[0];
            if (!"1".equals(version)) {
                log.error("Discarding message with version %s", version);
                return;
            }
            String context = parts[1];
            if (!"in".equals(context)) {
                log.error("Discarding message with context %s", context);
                return;
            }
            String channel = parts[2];
            String pluginId = parts[3];
            String next = parts[4];
            String body = switch (next) {
                case "null" -> null;
                case "dk" -> parts[6];
                case "" -> parts[5];
                default -> null;
            };
            if (null == body) {
                log.error("Discarding message with null or unparsable body %s %s", channel, pluginId);
            }
            body = new String(Base64.getDecoder().decode(body));
            var mapper = new ObjectMapper();
            var typeRef = new TypeReference<HashMap<String, Object>>() {};
            var parsedMessage = messageFor(mapper.readValue(body, typeRef));
            getSubscribers.apply(channel).forEach(onMessage -> {
                onMessage.accept(parsedMessage);
            });
        } catch (Exception e) {
            // TODO what would be good to log here?
            log.error("Failed to parse message!");
            e.printStackTrace();
        }
    }

    // TODO this is wacky
    private static Message messageFor(HashMap<String, Object> map) {
        return new Message() {
            @Override
            public int size() {
                return map.size();
            }

            @Override
            public boolean isEmpty() {
                return map.isEmpty();
            }

            @Override
            public boolean containsKey(Object key) {
                return map.containsKey(key);
            }

            @Override
            public boolean containsValue(Object value) {
                return map.containsValue(value);
            }

            @Override
            public Object get(Object key) {
                return map.get(key);
            }

            @Override
            public Object put(String key, Object value) {
                return map.put(key, value);
            }

            @Override
            public Object remove(Object key) {
                return map.remove(key);
            }

            @Override
            public void putAll(Map<? extends String, ? extends Object> m) {
                map.putAll(m);
            }

            @Override
            public void clear() {
                map.clear();
            }

            @Override
            public Set<String> keySet() {
                return map.keySet();
            }

            @Override
            public Collection<Object> values() {
                return map.values();
            }

            @Override
            public Set<Entry<String, Object>> entrySet() {
                return map.entrySet();
            }
        };
    }
}

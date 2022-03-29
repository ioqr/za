package za.engine;

import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import za.lib.Logger;
import za.lib.Message;

public final class MessageHandler {  // TODO rename to MessageEncodingUtils or something like that
    private MessageHandler() {}

    /**
     * Engine internal message representation
     * 
     * note: when receiving messages, the `Object message` is a za.lib.Message
     */
    // TODO this should be in its own file
    public record Props(String context, String channel, String pluginId, Object message) {}

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
    public static Optional<String> encode(MessageHandler.Props p) {
        try {
            var list = new ArrayList<String>();
            list.add("1");  // version number
            list.add(p.context());
            list.add(p.channel());
            list.add(p.pluginId());
            list.add(encodeMessageObject(p.message()));
            return Optional.of(String.join(":", list));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private static String encodeMessageObject(Object message) throws JsonProcessingException {
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

    public static Optional<MessageHandler.Props> decode(String message) {
        try {
            String[] parts = message.split(":");
            String version = parts[0];
            if (!"1".equals(version)) {
                return Optional.empty();
            }
            String context = parts[1];
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
                return Optional.empty();
            }
            body = new String(Base64.getDecoder().decode(body));
            var mapper = new ObjectMapper();
            var typeRef = new TypeReference<HashMap<String, Object>>() {};
            var parsedMessage = messageFor(mapper.readValue(body, typeRef));
            return Optional.of(new Props(context, channel, pluginId, parsedMessage));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static void dispatch(RegistryImpl registry, MessageHandler.Props message) {
        registry.getSubscribers(message.channel()).forEach(onMessage -> {
            try {
                onMessage.accept((Message) message.message());
            } catch (Exception e) {
                System.err.println("MessageHandler: error in subscriber: " + e);
                e.printStackTrace();
            }
        });
    }

    // TODO this is wacky
    private static Message messageFor(HashMap<String, Object> map) {
        return new Message() {
            @Override
            public String toString() {
                return map.toString();
            }

            @Override
            public boolean equals(Object obj) {
                return map.equals(obj);
            }

            @Override
            public int hashCode() {
                return map.hashCode();
            }

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

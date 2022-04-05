package za.engine;

import java.util.*;
import java.util.Map.Entry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import za.lib.Message;

public final class MessageUtils {
    private MessageUtils() {}

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
    public static Optional<String> encode(InternalMessage p) {
        try {
            var list = new ArrayList<String>();
            list.add("1");  // version number
            list.add(p.context());
            list.add(p.channel());
            list.add(p.pluginId());
            list.add(encodeMessageObject(p.serializableBody()));
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

    public static Optional<InternalMessage> decode(UUID internalKey, String messageId, String message) {
        try {
            Objects.requireNonNull(internalKey);
            Objects.requireNonNull(messageId);
            Objects.requireNonNull(message);
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
            var parsedMessage = messageFor(messageId, mapper.readValue(body, typeRef));
            return Optional.of(new InternalMessage(internalKey, context, channel, pluginId, Optional.of(messageId), parsedMessage));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static void dispatch(RegistryImpl registry, InternalMessage message) {
        registry.getSubscribers(message.channel()).forEach(onMessage -> {
            if (message.messageId().isEmpty()) {
                throw new IllegalStateException("Missing message id for message with internal key " + message.key());
            }
            if (!(message.serializableBody() instanceof Map)) {
                throw new IllegalStateException("Body is not a map: message id " + message.messageId().get() + " with internal key " + message.key());
            }
            try {
                onMessage.accept(messageFor(message.messageId().get(), (Map) message.serializableBody()));
            } catch (Exception e) {
                System.err.println("MessageHandler: failed to dispatch message: " + e);
                e.printStackTrace();
            }
        });
    }

    // TODO this is wacky
    private static Message messageFor(String messageId, Map<String, Object> inputMap) {
        Objects.requireNonNull(messageId);
        Objects.requireNonNull(inputMap);
        final Map<String, Object> map = Map.copyOf(inputMap);
        return new Message() {
            @Override
            public String id() {
                return messageId;
            }

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

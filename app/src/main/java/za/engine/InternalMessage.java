package za.engine;

import java.util.Optional;
import java.util.UUID;

public record InternalMessage (
        UUID key,
        String context,
        String channel,
        String pluginId,
        Optional<String> messageId,
        Object serializableBody
) {
    @Override
    public String toString() {
        return "InternalMessage{" +
                "key=" + key +
                ", context='" + context + '\'' +
                ", channel='" + channel + '\'' +
                ", pluginId='" + pluginId + '\'' +
                ", messageId=" + messageId.orElse("null") +
                ", hasBody=" + (serializableBody != null) +
                '}';
    }
}
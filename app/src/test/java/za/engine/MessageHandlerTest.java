package za.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.jupiter.api.Test;

public class MessageHandlerTest {
    private static final String CONTEXT = "in";
    private static final String CHANNEL = "za.i";
    private static final String PLUGIN_ID = "123";

    @Test
    public void testEncodeWithDefaultDataKey() throws JsonProcessingException {
        var map = Map.of("key", "value");
        var message = MessageHandler.encode(new MessageHandler.Props(CONTEXT, CHANNEL, PLUGIN_ID, map)).get();
        assertEquals("1:in:za.i:123:dk:dmFsdWU=:eyJrZXkiOiJ2YWx1ZSJ9", message);
        assertHasComponent("{\"key\":\"value\"}", message, 6);
        assertHasComponent("value", message, 5);
    }

    @Test
    public void testEncodeNull() throws JsonProcessingException {
        var message = MessageHandler.encode(new MessageHandler.Props(CONTEXT, CHANNEL, PLUGIN_ID, null)).get();
        assertEquals("1:in:za.i:123:null", message);
    }

    @Test
    public void testEncodeWithoutDataKey() throws JsonProcessingException {
        var map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("k2", "v2");
        var message = MessageHandler.encode(new MessageHandler.Props(CONTEXT, CHANNEL, PLUGIN_ID, map)).get();
        assertEquals("1:in:za.i:123::eyJrMSI6InYxIiwiazIiOiJ2MiJ9", message);
        assertHasComponent("{\"k1\":\"v1\",\"k2\":\"v2\"}", message, 5);
    }

    @Test
    public void testEncodeWithCustomDataKey() throws JsonProcessingException {
        var map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("_dk", "k2");
        map.put("k2", "v2");
        map.put("k3", "v3");
        var message = MessageHandler.encode(new MessageHandler.Props(CONTEXT, CHANNEL, PLUGIN_ID, map)).get();
        assertEquals("1:in:za.i:123:dk:djI=:eyJrMSI6InYxIiwiX2RrIjoiazIiLCJrMiI6InYyIiwiazMiOiJ2MyJ9", message);
        assertHasComponent("{\"k1\":\"v1\",\"_dk\":\"k2\",\"k2\":\"v2\",\"k3\":\"v3\"}", message, 6);
        assertHasComponent("v2", message, 5);
    }

    @Test
    public void testEncodeWithInvalidCustomDataKey() throws JsonProcessingException {
        var map = new LinkedHashMap<>();
        map.put("k1", "v1");
        map.put("_dk", "i_am_not_a_key");
        map.put("k2", "v2");
        map.put("k3", "v3");
        var message = MessageHandler.encode(new MessageHandler.Props(CONTEXT, CHANNEL, PLUGIN_ID, map)).get();
        assertEquals("1:in:za.i:123::eyJrMSI6InYxIiwiX2RrIjoiaV9hbV9ub3RfYV9rZXkiLCJrMiI6InYyIiwiazMiOiJ2MyJ9", message);
        assertHasComponent("{\"k1\":\"v1\",\"_dk\":\"i_am_not_a_key\",\"k2\":\"v2\",\"k3\":\"v3\"}", message, 5);
    }

    @Test
    public void testDecode() {
        fail("need to write a test for MessageHandler#decode()");
    }

    // it is hard for us humans to read base64
    private static void assertHasComponent(String expected, String message, int component) {
        assertEquals(expected, new String(Base64.getDecoder().decode(message.split(":")[component])));
    }
}

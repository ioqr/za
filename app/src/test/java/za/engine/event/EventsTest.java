package za.engine.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class EventsTest {
    @Test
    public void testWrap() {
        Event e1 = new Event(Events.MQ_RECEIVE, new String("hello world"));
        Event e2 = Events.MQ_RECEIVE.wrap(new String("hello world"));
        assertEquals(e1, e2);
    }    
}

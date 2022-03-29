package za.engine.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

// using `new String()` to assert that .equals() is called, not object identity
public class EventTest {
    @Test
    public void testEventIdIsNotUsedInComparison() {
        Event e1 = new Event(Events.HTTP_SEND, new String("data"));
        Event e2 = new Event(Events.HTTP_SEND, new String("data"));
        assertEquals(e1, e2);
        assertNotEquals(e1.id(), e2.id()); 
    }

    @Test
    public void testEquals() {
        // same event type & data
        assertEquals(
            new Event(Events.HTTP_SEND, new String("data")),
            new Event(Events.HTTP_SEND, new String("data")));
        // different data
        assertNotEquals(
            new Event(Events.HTTP_SEND, new String("different data")),
            new Event(Events.HTTP_SEND, new String("data")));
        // different event type, same data 
        assertNotEquals(
            new Event(Events.MQ_SEND, new String("data")),
            new Event(Events.HTTP_SEND, new String("data")));
        // different event type, different data 
        assertNotEquals(
            new Event(Events.MQ_SEND, new String("different data")),
            new Event(Events.HTTP_SEND, new String("data")));
    }

    @Test
    public void testHashCode() {
        // same event type & data
        assertEquals(
            new Event(Events.HTTP_SEND, new String("data")).hashCode(),
            new Event(Events.HTTP_SEND, new String("data")).hashCode());
        // we ignore hashcode collisions; this test could fail if hashcode impl is changed; for now its ok
        // different data
        assertNotEquals(
            new Event(Events.HTTP_SEND, new String("different data")).hashCode(),
            new Event(Events.HTTP_SEND, new String("data")).hashCode());
        // different event type, same data 
        assertNotEquals(
            new Event(Events.MQ_SEND, new String("data")).hashCode(),
            new Event(Events.HTTP_SEND, new String("data")).hashCode());
        // different event type, different data 
        assertNotEquals(
            new Event(Events.MQ_SEND, new String("different data")).hashCode(),
            new Event(Events.HTTP_SEND, new String("data")).hashCode());
    }
}

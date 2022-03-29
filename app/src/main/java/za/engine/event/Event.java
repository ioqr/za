package za.engine.event;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class Event {
    public static final long UNASSIGNED_ID = -1;

    private static final AtomicLong nextId = new AtomicLong(0);

    private transient long id = UNASSIGNED_ID;  // not included in equals() or hashCode()
    private final Events type;
    private final Object data;

    public Event(Events type, Object data) {
        this.id = nextId.incrementAndGet();
        this.type = type;
        this.data = data;
    }

    public long id() {
        return this.id;
    }

    public Events type() {
        return this.type;
    }

    public Object data() {
        return this.data;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Event e) {
            return Objects.equals(e.type, this.type) && Objects.equals(e.data, this.data);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.type) ^ Objects.hashCode(this.data);
    }
}

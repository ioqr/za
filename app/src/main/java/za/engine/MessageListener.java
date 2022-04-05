package za.engine;

public interface MessageListener {
    void onSend(InternalMessage message);  // impl must be thread-safe
    void onReceive(InternalMessage message);  // impl must be thread-safe
}

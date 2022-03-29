package za.engine;

public interface MessageQueue {
    void send(MessageHandler.Props message);  // impl must be thread-safe
    void receive(String messageId, MessageHandler.Props message);  // impl must be thread-safe; id is used to track message status
}

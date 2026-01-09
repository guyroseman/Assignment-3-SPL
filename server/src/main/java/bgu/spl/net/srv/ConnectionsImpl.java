package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectionsImpl<T> implements Connections<T> {

    // Mapping between connection ID and its Handler (to send messages via TCP)
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();

    // Mapping between channel name (Topic) and a list of subscribed connection IDs
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Integer>> channels = new ConcurrentHashMap<>();

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            // Sending the message via the handler (thread-safe due to the handler's implementation)
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        // Sending a message to all clients subscribed to the specific channel
        ConcurrentLinkedQueue<Integer> subscribers = channels.get(channel);
        if (subscribers != null) {
            for (Integer connectionId : subscribers) {
                // We use the existing send function to handle the actual sending
                send(connectionId, msg);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // Removing the client from the active connections map
        activeConnections.remove(connectionId);
        
        // Note: We do not necessarily remove the client from all topics here immediately.
        // Usually, the protocol handles cleanup or the 'send' method will just fail 
        // gracefully if the ID is in a topic but not in activeConnections.
    }

    // --- Helper functions (not part of the original interface) to manage registration ---

    /**
     * Adds a new client connection to the map.
     * Should be called when a client connects to the server (Accept phase).
     * @param connectionId The unique ID of the connection
     * @param handler The handler for this connection
     */
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    /**
     * Subscribes a client to a specific channel.
     * Should be called by the protocol when a SUBSCRIBE frame is received.
     * @param channel The name of the channel/topic
     * @param connectionId The ID of the client
     */
    public void subscribe(String channel, int connectionId) {
        // Create the channel if it doesn't exist, and add the user
        channels.computeIfAbsent(channel, k -> new ConcurrentLinkedQueue<>()).add(connectionId);
    }

    /**
     * Unsubscribes a client from a specific channel.
     * Should be called by the protocol when an UNSUBSCRIBE frame is received.
     * @param channel The name of the channel/topic
     * @param connectionId The ID of the client
     */
    public void unsubscribe(String channel, int connectionId) {
        ConcurrentLinkedQueue<Integer> subscribers = channels.get(channel);
        if (subscribers != null) {
            subscribers.remove(connectionId);
        }
    }
}
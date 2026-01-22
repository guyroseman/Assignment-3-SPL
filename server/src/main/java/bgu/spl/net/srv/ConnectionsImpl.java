package bgu.spl.net.srv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    // Mapping: ConnectionID -> ConnectionHandler
    // Holds the physical connection handlers for sending data over the network.
    private final ConcurrentHashMap<Integer, ConnectionHandler<T>> activeConnections = new ConcurrentHashMap<>();

    // Mapping: ChannelName -> ( ConnectionID -> SubscriptionID )
    // Manages topic subscriptions. Used when sending a message to a channel to know who should receive it.
    private final ConcurrentHashMap<String, ConcurrentHashMap<Integer, String>> channelSubscribers = new ConcurrentHashMap<>();

    // Mapping: ConnectionID -> ( SubscriptionID -> ChannelName )
    // Reverse mapping for fast lookup. Used to efficiently unsubscribe a user by ID or clean up on disconnect.
    private final ConcurrentHashMap<Integer, ConcurrentHashMap<String, String>> clientSubscriptions = new ConcurrentHashMap<>();

    @Override
    public boolean send(int connectionId, T msg) {
        ConnectionHandler<T> handler = activeConnections.get(connectionId);
        if (handler != null) {
            handler.send(msg);
            return true;
        }
        return false;
    }

    @Override
    public void send(String channel, T msg) {
        // Retrieve all subscribers for the given channel
        ConcurrentHashMap<Integer, String> subscribers = channelSubscribers.get(channel);
        
        if (subscribers != null) {
            for (Integer connectionId : subscribers.keySet()) {
                String originalFrame = (String) msg;
                String personalizedFrame = originalFrame.replaceFirst("subscription:0", "subscription:" + subscribers.get(connectionId));
                send(connectionId, (T) personalizedFrame);
            }
        }
    }

    @Override
    public void disconnect(int connectionId) {
        // Remove the physical connection
        activeConnections.remove(connectionId);

        // Remove all logical subscriptions associated with this user
        Map<String, String> userSubs = clientSubscriptions.remove(connectionId);
        if (userSubs != null) {
            for (String channel : userSubs.values()) {
                // Clean up the user from each channel's subscriber list
                ConcurrentHashMap<Integer, String> channelSubs = channelSubscribers.get(channel);
                if (channelSubs != null) {
                    channelSubs.remove(connectionId);
                }
            }
        }
    }

    @Override
    public void subscribe(String channel, int connectionId, String subscriptionId) {
        // Register the user to the channel
        channelSubscribers.computeIfAbsent(channel, k -> new ConcurrentHashMap<>())
                          .put(connectionId, subscriptionId);

        // Record the subscription for the user (for reverse lookup)
        clientSubscriptions.computeIfAbsent(connectionId, k -> new ConcurrentHashMap<>())
                           .put(subscriptionId, channel);
    }

    @Override
    public void unsubscribe(String subscriptionId, int connectionId) {
        // Find which channel this subscription ID belongs to
        Map<String, String> userSubs = clientSubscriptions.get(connectionId);
        
        if (userSubs != null) {
            String channel = userSubs.remove(subscriptionId);
            
            // If the channel was found, remove the user from that channel's list
            if (channel != null) {
                ConcurrentHashMap<Integer, String> channelSubs = channelSubscribers.get(channel);
                if (channelSubs != null) {
                    channelSubs.remove(connectionId);
                }
            }
        }
    }
    
    /**
     * Adds a new connection handler. Called by the server when a client connects.
     */
    public void addConnection(int connectionId, ConnectionHandler<T> handler) {
        activeConnections.put(connectionId, handler);
    }

    @Override
    public boolean isSubscribed(String channel, int connectionId) {
        // Get the map of subscribers for this specific channel
        ConcurrentHashMap<Integer, String> subscribers = channelSubscribers.get(channel);
        
        // Check if the map exists AND if the user is in it
        return subscribers != null && subscribers.containsKey(connectionId);
    }
}
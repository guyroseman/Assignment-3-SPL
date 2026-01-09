package bgu.spl.net.srv;

public interface Connections<T> {

    
    boolean send(int connectionId, T msg);

   
    void send(String channel, T msg);


    void disconnect(int connectionId);

    /**
     * Subscribes a client to a channel with a specific subscription ID.
     * @param channel The name of the channel (topic).
     * @param connectionId The ID of the client.
     * @param subscriptionId The unique ID provided by the client for this subscription.
     */
    void subscribe(String channel, int connectionId, String subscriptionId);

    /**
     * Unsubscribes a client from a channel using the subscription ID.
     * @param subscriptionId The unique ID provided by the client during subscription.
     * @param connectionId The ID of the client.
     */
    void unsubscribe(String subscriptionId, int connectionId);

    /**
     * Checks if a specific client is subscribed to a specific channel.
     * @param channel The name of the channel.
     * @param connectionId The ID of the client.
     * @return true if subscribed, false otherwise.
     */
    boolean isSubscribed(String channel, int connectionId);
}
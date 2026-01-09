package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.impl.data.Database;
import bgu.spl.net.impl.data.LoginStatus;
import bgu.spl.net.srv.Connections;

import java.util.HashMap;
import java.util.Map;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;
    private String currentUser = null; 

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    public StompMessagingProtocolImpl(Connections<String> connections) {
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        String[] lines = message.split("\n");
        if (lines.length == 0) return;

        String command = lines[0].trim();
        Map<String, String> headers = parseHeaders(message);
        String body = extractBody(message);

        // Delegate execution based on the STOMP command
        switch (command) {
            case "CONNECT":
                handleConnect(headers);
                break;
            case "SUBSCRIBE":
                handleSubscribe(headers);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(headers);
                break;
            case "SEND":
                handleSend(headers, body);
                break;
            case "DISCONNECT":
                handleDisconnect(headers);
                break;
            default:
                sendError(headers, "Unknown Command", "The command " + command + " is not recognized.");
                break;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // --- Command Handler Methods ---

    private void handleConnect(Map<String, String> headers) {
        String login = headers.get("login");
        String passcode = headers.get("passcode");
        String acceptVersion = headers.get("accept-version");

        // Validate mandatory headers
        if (acceptVersion == null || !acceptVersion.equals("1.2")) {
            sendError(headers, "Malformed Frame", "Supported version is 1.2");
            return;
        }
        if (login == null || passcode == null) {
            sendError(headers, "Malformed Frame", "Missing login or passcode header");
            return;
        }

        // Authenticate user against the database
        LoginStatus status = Database.getInstance().login(connectionId, login, passcode);

        if (status == LoginStatus.LOGGED_IN_SUCCESSFULLY || status == LoginStatus.ADDED_NEW_USER) {
            this.currentUser = login;
            
            // Send success frame
            String response = "CONNECTED\n" +
                              "version:1.2\n" +
                              "\n" +
                              "\u0000";
            connections.send(connectionId, response);
        } else {
            // Handle various login failures
            String errorMsg = "Login failed";
            if (status == LoginStatus.WRONG_PASSWORD) errorMsg = "Wrong password";
            else if (status == LoginStatus.ALREADY_LOGGED_IN) errorMsg = "User already logged in";
            else if (status == LoginStatus.CLIENT_ALREADY_CONNECTED) errorMsg = "Client already connected";
            
            sendError(headers, "Login Failed", errorMsg);
        }
    }

    private void handleSubscribe(Map<String, String> headers) {
        String destination = headers.get("destination");
        String id = headers.get("id");

        if (destination == null || id == null) {
            sendError(headers, "Malformed Frame", "Missing destination or id header");
            return;
        }

        // Register the subscription
        connections.subscribe(destination, connectionId, id);

        sendReceiptIfNeeded(headers);
    }

    private void handleUnsubscribe(Map<String, String> headers) {
        String id = headers.get("id");
        if (id == null) {
             sendError(headers, "Malformed Frame", "Missing id header");
             return;
        }

        // Unsubscribe using the ID directly.
        // ConnectionsImpl will look up the corresponding channel and remove the user.
        connections.unsubscribe(id, connectionId);
        
        sendReceiptIfNeeded(headers);
    }

    private void handleSend(Map<String, String> headers, String body) {
        String destination = headers.get("destination");
        if (destination == null) {
            sendError(headers, "Malformed Frame", "Missing destination header");
            return;
        }
        
        // Ensure the user is logged in before allowing them to send messages
        if (this.currentUser == null) {
            sendError(headers, "Unauthorized", "You must log in first");
            return;
        }
        
        // If the user tries to send to a channel they are not subscribed to -> Error
        if (!connections.isSubscribed(destination, connectionId)) {
            sendError(headers, "Unauthorized", "User is not subscribed to topic " + destination);
            return;
        }
        // Construct the MESSAGE frame for broadcasting
        String messageFrame = "MESSAGE\n" +
                              "subscription:0\n" + 
                              "message-id:" + System.currentTimeMillis() + "\n" +
                              "destination:" + destination + "\n" +
                              "\n" +
                              body + "\n" + 
                              "\u0000";

        // Broadcast to all subscribers of the channel
        connections.send(destination, messageFrame);
        sendReceiptIfNeeded(headers);
    }

    private void handleDisconnect(Map<String, String> headers) {
        // Mark user as logged out in the database
        Database.getInstance().logout(connectionId);
        
        sendReceiptIfNeeded(headers);
        
        // Signal termination and close connection.
        // ConnectionsImpl will automatically clean up all subscriptions for this ID.
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    // --- Helper Methods ---

    private void sendReceiptIfNeeded(Map<String, String> headers) {
        String receiptId = headers.get("receipt");
        if (receiptId != null) {
            String receiptFrame = "RECEIPT\n" +
                                  "receipt-id:" + receiptId + "\n" +
                                  "\n" +
                                  "\u0000";
            connections.send(connectionId, receiptFrame);
        }
    }

    private void sendError(Map<String, String> headers, String message, String description) {
        String errorFrame = "ERROR\n" +
                            "message:" + message + "\n" +
                            (headers.containsKey("receipt") ? "receipt-id:" + headers.get("receipt") + "\n" : "") +
                            "\n" +
                            description + "\n" +
                            "\u0000";
        connections.send(connectionId, errorFrame);
        
        // Protocol requires closing connection after an ERROR frame
        shouldTerminate = true;
        connections.disconnect(connectionId);
    }

    private Map<String, String> parseHeaders(String message) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = message.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) break;
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                headers.put(parts[0].trim(), parts[1].trim());
            }
        }
        return headers;
    }

    private String extractBody(String message) {
        int splitIndex = message.indexOf("\n\n");
        if (splitIndex == -1) return "";
        return message.substring(splitIndex + 2);
    }
}
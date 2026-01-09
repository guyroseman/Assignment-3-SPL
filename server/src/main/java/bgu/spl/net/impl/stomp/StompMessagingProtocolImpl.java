package bgu.spl.net.impl.stomp;

import java.util.HashMap;
import java.util.Map;

import bgu.spl.net.api.StompMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class StompMessagingProtocolImpl implements StompMessagingProtocol<String> {

    private int connectionId;
    private Connections<String> connections;
    private boolean shouldTerminate = false;

    @Override
    public void start(int connectionId, Connections<String> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(String message) {
        // Simple parsing of the message to extract the command
        // Note: In a full implementation, you should parse headers and body properly
        String[] lines = message.split("\n");
        String command = lines[0].trim();

        // Use switch-case to handle different STOMP commands
        switch (command) {
            case "CONNECT":
                handleConnect(message);
                break;
            case "SEND":
                handleSend(message);
                break;
            case "SUBSCRIBE":
                handleSubscribe(message);
                break;
            case "UNSUBSCRIBE":
                handleUnsubscribe(message);
                break;
            case "DISCONNECT":
                handleDisconnect(message);
                break;
            default:
                // Handle unknown command or error
                System.out.println("Unknown command: " + command);
                break;
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    // --- Helper methods for handling specific commands ---

    private void handleConnect(String message) {
        System.out.println("Received CONNECT frame");
        // TODO:
        // 1. Extract 'login' and 'passcode' headers
        // 2. Check if user exists / password is correct
        // 3. If successful, send CONNECTED frame
        // 4. If failed, send ERROR frame and close connection
        Map<String, String> headers = parseHeaders(message);

        String login = headers.get("login");
        String passcode = headers.get("passcode");
        String acceptVersion = headers.get("accept-version");
        String host = headers.get("host");

    }

    private void handleSubscribe(String message) {
        System.out.println("Received SUBSCRIBE frame");
        // TODO:
        // 1. Extract 'destination' and 'id' headers
        // 2. Add client to the topic in 'connections'
        // 3. Send RECEIPT frame if requested
    }

    private void handleUnsubscribe(String message) {
        System.out.println("Received UNSUBSCRIBE frame");
        // TODO:
        // 1. Extract 'id' header
        // 2. Remove client from the topic
        // 3. Send RECEIPT frame if requested
    }

    private void handleSend(String message) {
        System.out.println("Received SEND frame");
        // TODO:
        // 1. Extract 'destination' header
        // 2. Send the message body to all subscribers of that destination
        //    (Using connections.send(channel, msg))
    }

    private void handleDisconnect(String message) {
        System.out.println("Received DISCONNECT frame");
        // TODO:
        // 1. Send RECEIPT frame
        // 2. Set shouldTerminate = true
        // 3. Close the connection via connections.disconnect(connectionId)
    }

    // --- Helper function to parse headers ---
    private Map<String, String> parseHeaders(String message) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = message.split("\n");
        
        // Start from line 1 (because line 0 is the command itself, e.g., CONNECT)
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) break; // Reached end of headers (empty line)
            
            String[] parts = line.split(":", 2); // Split by the first colon only
            if (parts.length == 2) {
                headers.put(parts[0], parts[1]);
            }
        }
        return headers;
    }
    
    // --- Helper function for sending errors and closing connection ---
    private void sendError(Map<String, String> headers, String messageHeader, String descriptionBody) {
        String receiptId = headers.get("receipt"); // If client requested a receipt, return it in the error too
        
        StringBuilder errorFrame = new StringBuilder();
        errorFrame.append("ERROR\n");
        if (receiptId != null) {
            errorFrame.append("receipt-id:").append(receiptId).append("\n");
        }
        errorFrame.append("message:").append(messageHeader).append("\n");
        errorFrame.append("\n"); // End of headers
        errorFrame.append(descriptionBody).append("\n"); // Body of the message
        errorFrame.append("\u0000");

        connections.send(connectionId, errorFrame.toString());
        connections.disconnect(connectionId); // Error in CONNECT requires closing the connection
        shouldTerminate = true; // Signal protocol to terminate
    }
}
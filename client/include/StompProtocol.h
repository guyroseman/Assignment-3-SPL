#pragma once

#include "../include/ConnectionHandler.h"
#include "../include/event.h" // Ensure this file exists in your project
#include <string>
#include <vector>
#include <map>
#include <mutex>

class StompProtocol {
private:
    // --- State Variables ---
    bool isConnected;            // Logical connection status (after CONNECT frame)
    bool isLogoutRequested;      // Flag: Did we send DISCONNECT? (Wait for RECEIPT before closing)
    
    std::string currentUser;     // The currently logged-in username
    int subscriptionIdCounter;   // Auto-incrementing ID for SUBSCRIBE frames
    int receiptIdCounter;        // Auto-incrementing ID for RECEIPT headers

    // --- Data Structures ---
    
    // Maps: Game Name -> Subscription ID
    // Usage: When user types "exit {game}", we look up the ID here to send UNSUBSCRIBE.
    std::map<std::string, int> activeSubscriptions;

    // Maps: Subscription ID -> Game Name
    // Usage: When server sends a MESSAGE frame (which only has sub-id), 
    // we use this to know which game the event belongs to.
    std::map<int, std::string> subscriptionIdToGameName;
    
    // Maps: Receipt ID -> Command/Action description
    // Usage: When we send a frame with "receipt:id", we store it here.
    // When RECEIPT arrives, we check this map to know what action succeeded (e.g., "Joined game X").
    std::map<int, std::string> receiptActions;

    // Maps: Game Name -> Username -> List of Events
    // Usage: Stores all incoming events. Used to generate the "summary" report.
    std::map<std::string, std::map<std::string, std::vector<Event>>> gameUpdates;

    // --- Concurrency ---
    // Protects shared resources (gameUpdates, activeSubscriptions) 
    // because the SocketThread writes to them and the MainThread reads/writes them.
    std::mutex protocolMutex;

public:
    StompProtocol();

    // Entry point for User Input (Main Thread)
    // Parses the user command (e.g., "join sci-fi"), converts to STOMP frame, sends via handler.
    // Returns: false if client should shut down (logout completed).
    bool processKeyboardCommand(const std::string& commandLine, ConnectionHandler& handler);

    // Entry point for Network Input (Socket Thread)
    // Parses the server frame (e.g., MESSAGE), updates state, prints to screen.
    // Returns: false if connection is lost or server sent ERROR.
    bool processServerFrame(const std::string& frame, ConnectionHandler& handler);

    // Getters
    bool shouldTerminate() const; // True if logout sequence is finished
    bool isUserConnected() const;

private:
    // --- Parsing Helpers ---
    std::vector<std::string> split(const std::string& s, char delimiter);
    std::map<std::string, std::string> parseHeaders(const std::string& frame);
    std::string extractBody(const std::string& frame);

    // --- Command Handlers (Client -> Server) ---
    void login(const std::string& hostPort, const std::string& username, const std::string& password, ConnectionHandler& handler);
    void joinGame(const std::string& gameName, ConnectionHandler& handler);
    void exitGame(const std::string& gameName, ConnectionHandler& handler);
    void sendReport(const std::string& file, ConnectionHandler& handler);
    void logout(ConnectionHandler& handler);
    
    // Summary does NOT send network data, only reads local history
    void summary(const std::string& gameName, const std::string& user, const std::string& file);

    // --- Frame Handlers (Server -> Client) ---
    void handleConnected(const std::map<std::string, std::string>& headers);
    void handleMessage(const std::map<std::string, std::string>& headers, const std::string& body);
    void handleReceipt(const std::map<std::string, std::string>& headers, ConnectionHandler& handler);
    void handleError(const std::map<std::string, std::string>& headers, const std::string& body);
};
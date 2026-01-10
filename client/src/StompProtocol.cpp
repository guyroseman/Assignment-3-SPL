#include "../include/StompProtocol.h"
#include <iostream>
#include <sstream>
#include <fstream>

// --- Constructor ---
StompProtocol::StompProtocol() 
    : isConnected(false), 
      isLogoutRequested(false),
      currentUser(""),
      subscriptionIdCounter(0),
      receiptIdCounter(0),
      activeSubscriptions(),
      subscriptionIdToGameName(),
      receiptActions(),
      gameUpdates(),
      protocolMutex() 
{
}
// --- Getters ---
bool StompProtocol::shouldTerminate() const {
    return isLogoutRequested && !isConnected; 
}

bool StompProtocol::isUserConnected() const {
    return isConnected;
}

// --- Threads ---

// Entry point for User Input (Main Thread)
bool StompProtocol::processKeyboardCommand(const std::string& commandLine, ConnectionHandler& handler) {

    std::vector<std::string> args = split(commandLine, ' ');
    if (args.empty()) return true;

    std::string command = args[0];

    // LOGIN COMMAND
    if (command == "login") {
        // Syntax: login {host:port} {username} {password}
        if (args.size() < 4) {
            std::cout << "Usage: login {host:port} {username} {password}" << std::endl;
            return true;
        }
        login(args[1], args[2], args[3], handler);
    } 
    // JOIN COMMAND
    else if (command == "join") {
        if (!isConnected) {
            std::cout << "Please login first" << std::endl;
            return true;
        }
        if (args.size() < 2) {
            std::cout << "Usage: join {game_name}" << std::endl;
            return true;
        }
        joinGame(args[1], handler);
    } 
    // EXIT COMMAND
    else if (command == "exit") {
        if (!isConnected) {
            std::cout << "Please login first" << std::endl;
            return true;
        }
        if (args.size() < 2) {
            std::cout << "Usage: exit {game_name}" << std::endl;
            return true;
        }
        exitGame(args[1], handler);
    } 
    // REPORT COMMAND
    else if (command == "report") {
        if (!isConnected) {
            std::cout << "Please login first" << std::endl;
            return true;
        }
        if (args.size() < 2) {
            std::cout << "Usage: report {file}" << std::endl;
            return true;
        }
        sendReport(args[1], handler);
    } 
    // SUMMARY COMMAND
    else if (command == "summary") {
        if (args.size() < 4) {
            std::cout << "Usage: summary {game_name} {user} {file}" << std::endl;
            return true;
        }
        // Summary is a local operation, no handler needed
        summary(args[1], args[2], args[3]);
    } 
    // LOGOUT COMMAND
    else if (command == "logout") {
        if (!isConnected) {
            std::cout << "Please login first" << std::endl;
            return true;
        }
        logout(handler);
    } 
    else {
        std::cout << "Unknown command: " << command << std::endl;
    }

    return true; // We almost always return true to keep the loop running
}

bool StompProtocol::processServerFrame(const std::string& frame, ConnectionHandler& handler) {
    std::stringstream ss(frame);
    std::string command;
    std::getline(ss, command);
    
    // Clean up command (remove \r if present)
    if (!command.empty() && command.back() == '\r') command.pop_back();

    std::map<std::string, std::string> headers = parseHeaders(frame);
    std::string body = extractBody(frame);

    if (command == "CONNECTED") {
        handleConnected(headers);
    } 
    else if (command == "MESSAGE") {
        handleMessage(headers, body);
    } 
    else if (command == "RECEIPT") {
        handleReceipt(headers, handler);
    } 
    else if (command == "ERROR") {
        handleError(headers, body);
        return false; // Connection should close on ERROR
    }
    
    return true; 
}


// --- Parsing Helpers ---

std::vector<std::string> StompProtocol::split(const std::string& s, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(s);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

// Parse headers from a STOMP frame
std::map<std::string, std::string> StompProtocol::parseHeaders(const std::string& frame) {
    std::map<std::string, std::string> headers;
    std::stringstream ss(frame);
    std::string line;

    // Skip the first line (the Command, e.g., "CONNECTED")
    std::getline(ss, line); 

    // Read lines until an empty line is found
    while (std::getline(ss, line) && !line.empty() && line != "\r") {
        size_t colonPos = line.find(':');
        if (colonPos != std::string::npos) {
            std::string key = line.substr(0, colonPos);
            std::string value = line.substr(colonPos + 1);
            // Remove potential carriage return (\r) at the end
            if (!value.empty() && value.back() == '\r') {
                value.pop_back();
            }
            headers[key] = value;
        }
    }
    return headers;
}

// Extract body from a STOMP frame
std::string StompProtocol::extractBody(const std::string& frame) {
    // The body starts after the first double newline "\n\n"
    size_t splitPos = frame.find("\n\n");
    if (splitPos == std::string::npos) {
        // Fallback for Windows-style line endings "\r\n\r\n"
        splitPos = frame.find("\r\n\r\n");
        if (splitPos != std::string::npos) {
             return frame.substr(splitPos + 4);
        }
        return "";
    }
    return frame.substr(splitPos + 2);
}

// --- Command Handlers ---

// Handle LOGIN command
void StompProtocol::login(const std::string& hostPort, const std::string& username, const std::string& password, ConnectionHandler& handler) {
    // Update local state tentatively (we confirm it only when CONNECTED arrives)
    currentUser = username;

    // Construct the CONNECT frame
    std ::string frame = "CONNECT\n"
                        "accept-version:1.2\n"
                        "host:" + hostPort + "\n"
                        "login:" + username + "\n"
                        "passcode:" + password + "\n"
                        "\n"
                        "\0"; // Important: Null byte at the end

    // Send the frame
    // We send the null byte separately to ensure safety with std::string
    handler.sendLine(frame);
    handler.sendBytes("\u0000", 1);
}

// Handle JOIN command
void StompProtocol::joinGame(const std::string& gameName, ConnectionHandler& handler) {
    // Lock for thread safety
    std::lock_guard<std::mutex> lock(protocolMutex);

    if (activeSubscriptions.count(gameName)) {
        std::cout << "Already subscribed to " << gameName << std::endl;
        return;
    }

    int subId = subscriptionIdCounter++;
    int receiptId = receiptIdCounter++;

    activeSubscriptions[gameName] = subId;
    subscriptionIdToGameName[subId] = gameName;
    receiptActions[receiptId] = "Joined channel " + gameName;

    // Build the frame manually and precisely
    std::string frame = "SUBSCRIBE\n"
                        "destination:/" + gameName + "\n"  // Note: Added slash (common in some servers)
                        "id:" + std::to_string(subId) + "\n"
                        "receipt:" + std::to_string(receiptId) + "\n"
                        "\n"; // Empty line indicating end of headers

    // Debug print - to see exactly what we are sending
    std::cout << "DEBUG: Sending Frame:\n" << frame << "^@" << std::endl;

    // Safe send: sending text + Null byte immediately after
    handler.sendBytes(frame.c_str(), frame.length());
    handler.sendBytes("\u0000", 1);
}

// Handle EXIT command
void StompProtocol::exitGame(const std::string& gameName, ConnectionHandler& handler) {
    // Lock for thread safety
    std::lock_guard<std::mutex> lock(protocolMutex);
    auto it = activeSubscriptions.find(gameName);

    // Check if subscribed
    if (it == activeSubscriptions.end()) {
        std::cout << "Not subscribed to " << gameName << std::endl;
        return;
    }
    int subId = it->second;
    int receiptId = receiptIdCounter++;
    // Remove from state
    activeSubscriptions.erase(it);
    subscriptionIdToGameName.erase(subId);
    // Save receipt action
    receiptActions[receiptId] = "Exited channel " + gameName;
    // Build Frame
    std::string frame = "UNSUBSCRIBE\n"
                        "id:" + std::to_string(subId) + "\n"
                        "receipt:" + std::to_string(receiptId) + "\n"
                        "\n"
                        "\0"; // Null byte
    // Send
    handler.sendLine(frame);
    handler.sendBytes("\u0000", 1);

}

// Handle REPORT command
void StompProtocol::logout(ConnectionHandler& handler) {
    // Lock for thread safety
    std::lock_guard<std::mutex> lock(protocolMutex);
    if (!isConnected || isLogoutRequested) {
        return; // Already logged out or in process
    }
    int receiptId = receiptIdCounter++;
    isLogoutRequested = true;
    receiptActions[receiptId] = "Logged out";
    // Build Frame
    std::string frame = "DISCONNECT\n"
                        "receipt:" + std::to_string(receiptId) + "\n"
                        "\n"
                        "\0"; // Null byte
    // Send
    handler.sendLine(frame);
    handler.sendBytes("\u0000", 1);
}   

// Handle REPORT command
void StompProtocol::sendReport(const std::string& file, ConnectionHandler& handler) {
    names_and_events data;
    try {
        // Parse the events JSON file (function provided in event.cpp)
        data = parseEventsFile(file); 
    } catch (...) {
        std::cout << "Error parsing file: " << file << std::endl;
        return;
    }

    std::string gameName = data.team_a_name + "_" + data.team_b_name;

    // Verify we are subscribed to this game
    {
        std::lock_guard<std::mutex> lock(protocolMutex);
        if (activeSubscriptions.find(gameName) == activeSubscriptions.end()) {
            std::cout << "Error: Not subscribed to " << gameName << std::endl;
            return;
        }
    }

    // Iterate through events and send them
    for (const Event& event : data.events) {
        // Construct the body according to the assignment format
        std::string body = "";
        body += "user:" + currentUser + "\n";
        body += "team a:" + data.team_a_name + "\n";
        body += "team b:" + data.team_b_name + "\n";
        body += "event name:" + event.get_name() + "\n";
        body += "time:" + std::to_string(event.get_time()) + "\n";
        
        body += "general game updates:\n";
        for (const auto& pair : event.get_game_updates()) {
            body += pair.first + ":" + pair.second + "\n";
        }
        
        body += "team a updates:\n";
        for (const auto& pair : event.get_team_a_updates()) {
            body += pair.first + ":" + pair.second + "\n";
        }

        body += "team b updates:\n";
        for (const auto& pair : event.get_team_b_updates()) {
            body += pair.first + ":" + pair.second + "\n";
        }

        body += "description:\n" + event.get_discription(); 

        // Construct the STOMP Frame
        std::string frame = "SEND\n"
                            "destination:/" + gameName + "\n"
                            "\n" + 
                            body + "\n"
                            "\0"; 

        // Send over the socket
        handler.sendLine(frame);
        handler.sendBytes("\u0000", 1);
        
        // Update local state (Summary needs to know about my own reports)
        {
            std::lock_guard<std::mutex> lock(protocolMutex);
            gameUpdates[gameName][currentUser].push_back(event);
        }
        
        // Print confirmation 
        std::cout << "Sent event: " << event.get_name() << std::endl;
    }
}
// Handle SUMMARY command
void StompProtocol::summary(const std::string& gameName, const std::string& user, const std::string& file) {
    // Lock Mutex
    std::lock_guard<std::mutex> lock(protocolMutex);

    // Validate Data
    if (gameUpdates.find(gameName) == gameUpdates.end()) {
        std::cout << "No updates found for game: " << gameName << std::endl;
        return;
    }
    if (gameUpdates[gameName].find(user) == gameUpdates[gameName].end()) {
        std::cout << "No updates found for user " << user << " in game " << gameName << std::endl;
        return;
    }

    const std::vector<Event>& events = gameUpdates[gameName][user];
    if (events.empty()) return;

    // Open File
    std::ofstream outFile(file);
    if (!outFile.is_open()) {
        std::cout << "Error: Could not open file " << file << std::endl;
        return;
    }

    // --- AGGREGATION LOGIC START ---
    // Instead of taking just the last event, we iterate and update maps.
    // This ensures we keep stats from previous events if they weren't overwritten.
    std::map<std::string, std::string> finalGameUpdates;
    std::map<std::string, std::string> finalTeamAUpdates;
    std::map<std::string, std::string> finalTeamBUpdates;

    for (const auto& event : events) {
        for (const auto& pair : event.get_game_updates()) {
            finalGameUpdates[pair.first] = pair.second;
        }
        for (const auto& pair : event.get_team_a_updates()) {
            finalTeamAUpdates[pair.first] = pair.second;
        }
        for (const auto& pair : event.get_team_b_updates()) {
            finalTeamBUpdates[pair.first] = pair.second;
        }
    }
    // --- AGGREGATION LOGIC END ---

    // Get names from the first event 
    const Event& firstEvent = events[0];

    // Section A: Header
    outFile << firstEvent.get_team_a_name() << " vs " << firstEvent.get_team_b_name() << "\n";
    
    // Section B: Game Stats 
    outFile << "Game stats:\n";
    
    outFile << "General stats:\n";
    for (const auto& pair : finalGameUpdates) {
        outFile << pair.first << ": " << pair.second << "\n";
    }

    outFile << firstEvent.get_team_a_name() << " stats:\n";
    for (const auto& pair : finalTeamAUpdates) {
        outFile << pair.first << ": " << pair.second << "\n";
    }

    outFile << firstEvent.get_team_b_name() << " stats:\n";
    for (const auto& pair : finalTeamBUpdates) {
        outFile << pair.first << ": " << pair.second << "\n";
    }

    // Section C: Game Event Reports
    outFile << "Game event reports:\n";
    for (const Event& e : events) {
        outFile << e.get_time() << " - " << e.get_name() << ":\n\n";
        
        outFile << e.get_discription() << "\n\n";
    }

    outFile.close();
}

// --- Frame Handlers ---

// Handle CONNECTED frame from server
void StompProtocol::handleConnected(const std::map<std::string, std::string>& headers) {
    isConnected = true;
    std::cout << "Login successful" << std::endl;
}

// Handle MESSAGE frame from server 
void StompProtocol::handleMessage(const std::map<std::string, std::string>& headers, const std::string& body) {
    // Safety check: Ensure the subscription header exists
    if (headers.count("subscription") == 0) return;

    // Extract the Subscription ID
    int subId;
    try {
        subId = std::stoi(headers.at("subscription"));
    } catch (...) { return; }

    std::string gameName;
    std::string sender;

    {
        // Lock mutex for thread safety (accessing maps)
        std::lock_guard<std::mutex> lock(protocolMutex);

        // Verify we are subscribed to this game using the ID
        auto it = subscriptionIdToGameName.find(subId);
        if (it == subscriptionIdToGameName.end()) {
            return; // We ignore messages for unknown subscriptions
        }
        gameName = subscriptionIdToGameName[subId];

        // Extract the sender's username from the message body
        std::stringstream ss(body);
        std::string line;
        while (std::getline(ss, line)) {
            // Look for the line starting with "user:"
            if (line.find("user:") == 0) {
                sender = line.substr(5); // Skip the "user:" prefix to get the name
                
                // Clean up potential trailing whitespace/newlines
                while (!sender.empty() && (sender.back() == '\r' || sender.back() == '\n' || sender.back() == ' ')) {
                    sender.pop_back();
                }
                break;
            }
        }

        // If we couldn't find a user field, the message body is malformed
        if (sender.empty()) {
            std::cout << "Error: No user field found in message body" << std::endl;
            return;
        }

        // Store the event in memory
        // We pass the full body to the Event constructor so it can parse all fields (stats, description, etc.)
        gameUpdates[gameName][sender].push_back(Event(body));
    }

    // Print to console as required
    std::cout << "Received update in " << gameName << " from " << sender << std::endl;
}

// Handle RECEIPT frame from server
void StompProtocol::handleReceipt(const std::map<std::string, std::string>& headers, ConnectionHandler& handler) {
    // Safety check: Don't crash if header is missing
    if (headers.count("receipt-id") == 0) return;

    int receiptId = std::stoi(headers.at("receipt-id"));
    // Lock for thread safety
    std::lock_guard<std::mutex> lock(protocolMutex);
    auto it = receiptActions.find(receiptId);

    if (it != receiptActions.end()) {
        // Special handling for Logout
        if (it->second == "Logged out") {
            isConnected = false;
            handler.close();
            // Print confirmation
            std::cout << "Disconnected" << std::endl; 
        } 
        else {
            // For all other actions (Join/Exit)
            // Print the description we saved earlier (e.g., "Joined channel sci-fi")
            std::cout << it->second << std::endl;
        }

        // Cleanup
        receiptActions.erase(it);
    }
}

// Handle ERROR frame from 
void StompProtocol::handleError(const std::map<std::string, std::string>& headers, const std::string& body) {
    std::cout << "Received ERROR frame from server:" << std::endl;
    
    if (headers.count("message")) {
        std::cout << "Message: " << headers.at("message") << std::endl;
    }
    
    std::cout << "Body: " << body << std::endl;

    // Error frame usually means the connection is closed by the server
    isConnected = false;
}
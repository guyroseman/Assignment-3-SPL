#include <iostream>
#include <thread>
#include <vector>
#include <string>
#include <sstream>
#include "../include/ConnectionHandler.h"
#include "../include/StompProtocol.h"

// --- Helper: Split String ---
std::vector<std::string> split(const std::string& s, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;
    std::istringstream tokenStream(s);
    while (std::getline(tokenStream, token, delimiter)) {
        tokens.push_back(token);
    }
    return tokens;
}

// --- Worker Thread 1: Server Listener ---
// Responsibilities: Read from server, parse frames, update protocol state.
void runSocketListener(ConnectionHandler* handler, StompProtocol* protocol) {
    while (!protocol->shouldTerminate()) {
        std::string answer;
        
        // Waits here until data arrives from the server
        if (!handler->getFrameAscii(answer, '\0')) {
            std::cout << "Disconnected from server." << std::endl;
            // Force termination so the keyboard thread knows to stop
            break; 
        }

        // Clean up: Remove trailing newline (\n) from the raw network data
        if (answer.length() > 0 && answer[answer.length() - 1] == '\n') {
            answer.resize(answer.length() - 1);
        }

        // Process: Hand the raw string to the protocol logic
        if (!protocol->processServerFrame(answer, *handler)) {
            break; // Protocol signaled a stop (e.g., ERROR frame or Logout Receipt)
        }
    }
}

// --- Worker Thread 2: Keyboard Listener ---
// Responsibilities: Read from user, parse commands, send frames.
void runKeyboardListener(ConnectionHandler* handler, StompProtocol* protocol) {
    const short bufsize = 1024;
    char buf[bufsize];

    while (!protocol->shouldTerminate()) {
        // Blocking call: Waits here until user types something and hits Enter
        std::cin.getline(buf, bufsize);
        std::string line(buf);
        
        // Process: Hand the command string to the protocol logic
        protocol->processKeyboardCommand(line, *handler);
    }
}

// --- Main Thread: Manager ---
// Responsibilities: Parsing login, Connection setup, Thread spawning, Cleanup.
int main(int argc, char *argv[]) {
    
    // Application Loop: Allows reconnecting after logout
    while (true) {
        const short bufsize = 1024;
        char buf[bufsize];

        // 1. Login Phase (Performed by Main Thread)
        // We must parse 'host' and 'port' BEFORE creating threads or handlers.
        std::cin.getline(buf, bufsize);
        std::string line(buf);
        std::vector<std::string> args = split(line, ' ');

        if (args.empty()) continue;

        if (args[0] == "login") {
            if (args.size() < 4) {
                std::cout << "Usage: login {host:port} {username} {password}" << std::endl;
                continue;
            }

            // Parse Host and Port
            std::string hostPort = args[1];
            size_t colonPos = hostPort.find(':');
            if (colonPos == std::string::npos) {
                std::cout << "Invalid host:port format" << std::endl;
                continue;
            }
            std::string host = hostPort.substr(0, colonPos);
            short port = 0;
            try {
                port = std::stoi(hostPort.substr(colonPos + 1));
            } catch (...) {
                std::cout << "Invalid port number" << std::endl;
                continue;
            }

            // 2. Setup Infrastructure
            ConnectionHandler* handler = new ConnectionHandler(host, port);
            if (!handler->connect()) {
                std::cerr << "Cannot connect to " << host << ":" << port << std::endl;
                delete handler;
                continue;
            }

            // 3. Setup Logic
            StompProtocol protocol;

            // 4. Perform Handshake
            // Send the initial CONNECT frame immediately
            protocol.processKeyboardCommand(line, *handler);

            // 5. Spawn Worker Threads
            // The Main Thread launches two employees and then waits.
            std::thread socketThread(runSocketListener, handler, &protocol);
            std::thread keyboardThread(runKeyboardListener, handler, &protocol);

            // 6. Wait for Completion
            // The socket thread will finish when the server sends the RECEIPT for logout
            // or if the connection drops.
            if (socketThread.joinable()) {
                socketThread.join();
            }

            // The keyboard thread will finish when the loop condition !shouldTerminate() fails.
            // Note: If the socket closed unexpectedly, you might need to press Enter once
            // to unblock the cin inside keyboardThread.
            if (keyboardThread.joinable()) {
                keyboardThread.join();
            }

            // 7. Cleanup
            delete handler;
            std::cout << "Client disconnected cleanly. Ready for new login." << std::endl;

        } else {
            std::cout << "Unknown command. Please login first." << std::endl;
        }
    }
    return 0;
}

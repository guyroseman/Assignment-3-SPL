import socket
import sys

HOST = 'localhost'
PORT = 7777

def run_listener():
    try:
        # Establish a TCP connection to the server
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((HOST, PORT))
        print("[Listener] Connected to server.")

        # 1. Login (Send CONNECT frame)
        # We authenticate as 'alice'
        connect_frame = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:alice\npasscode:1234\n\n\x00"
        sock.sendall(connect_frame.encode('utf-8'))
        
        # Read the 'CONNECTED' response from the server
        # This confirms we are logged in before proceeding
        sock.recv(1024) 

        # 2. Subscribe (Send SUBSCRIBE frame)
        # We register to listen for messages on the 'general' topic
        print("[Listener] Subscribing to topic 'general'...")
        sub_frame = "SUBSCRIBE\ndestination:general\nid:100\n\n\x00"
        sock.sendall(sub_frame.encode('utf-8'))

        print("[Listener] Waiting for messages... (Press Ctrl+C to stop)")
        
        # 3. Main Loop
        # Infinite loop to keep the client alive and waiting for incoming messages
        while True:
            data = sock.recv(1024)
            if not data:
                break # Server closed the connection
            
            print("\n[Listener] RECEIVED MESSAGE:")
            print(data.decode('utf-8'))
            print("-----------------------------")

    except KeyboardInterrupt:
        # Handle manual stop (Ctrl+C) gracefully
        print("\n[Listener] Stopped by user.")
    except Exception as e:
        print(f"[Listener] Error: {e}")
    finally:
        # Ensure the socket is closed properly
        sock.close()

if __name__ == "__main__":
    run_listener()
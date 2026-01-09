import socket
import time

HOST = 'localhost'
PORT = 7777

def run_publisher():
    try:
        # 1. Establish TCP connection
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((HOST, PORT))
        print("[Publisher] Connected to server.")

        # 2. Login (CONNECT frame)
        connect_frame = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:bob\npasscode:5678\n\n\x00"
        sock.sendall(connect_frame.encode('utf-8'))
        
        # Read the CONNECTED response to ensure we are logged in
        response = sock.recv(1024)
        print("[Publisher] Logged in successfully.")

        # 3. Subscribe (REQUIRED by your server before sending)
        # We subscribe to 'general' with id 123
        print("[Publisher] Subscribing to 'general' to enable sending...")
        sub_frame = "SUBSCRIBE\ndestination:general\nid:123\n\n\x00"
        sock.sendall(sub_frame.encode('utf-8'))
        
        # Small delay to let the server process the subscription
        time.sleep(0.2)

        # 4. Send Message (SEND frame)
        # Now that we are subscribed, this should work!
        msg_content = "Hello Alice, this is Bob!"
        print(f"[Publisher] Sending message: '{msg_content}' to 'general'")
        
        send_frame = f"SEND\ndestination:general\n\n{msg_content}\n\x00"
        sock.sendall(send_frame.encode('utf-8'))

        # 5. Disconnect
        time.sleep(0.2) # Wait a bit to ensure message is sent
        print("[Publisher] Disconnecting...")
        disc_frame = "DISCONNECT\nreceipt:99\n\n\x00"
        sock.sendall(disc_frame.encode('utf-8'))
        
        # Read the RECEIPT frame
        sock.recv(1024)
        sock.close()
        print("[Publisher] Connection closed.")

    except Exception as e:
        print(f"[Publisher] Error: {e}")

if __name__ == "__main__":
    run_publisher()
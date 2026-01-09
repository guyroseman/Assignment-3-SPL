import socket
import time
import sys

HOST = 'localhost'
PORT = 7777

def run_test_suite():
    print(f"--- Starting STOMP Test Suite on {HOST}:{PORT} ---")
    
    try:
        # יצירת חיבור TCP
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect((HOST, PORT))
        print("[v] TCP Connection established")
    except Exception as e:
        print(f"[x] Failed to connect to server: {e}")
        return

    # ---------------------------------------------------------
    # TEST 1: CONNECT
    # ---------------------------------------------------------
    print("\n--- TEST 1: Sending CONNECT frame ---")
    connect_frame = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:meni\npasscode:films\n\n\x00"
    sock.sendall(connect_frame.encode('utf-8'))
    
    # מחכים לתשובה
    data = sock.recv(1024).decode('utf-8')
    print("Server Response:")
    print(data)
    
    if "CONNECTED" in data:
        print(">>> TEST 1 PASSED: Logged in successfully.")
    else:
        print(">>> TEST 1 FAILED: Did not receive CONNECTED frame.")
        sock.close()
        return

    time.sleep(0.5) # הפסקה קצרה כדי לתת לשרת לעבד

    # ---------------------------------------------------------
    # TEST 2: SUBSCRIBE (הירשמות לנושא 'sci-fi')
    # ---------------------------------------------------------
    print("\n--- TEST 2: Sending SUBSCRIBE frame (topic: sci-fi, id: 78) ---")
    # שים לב: בדרך כלל שרת לא מחזיר תשובה על SUBSCRIBE אלא אם מבקשים receipt
    # אנחנו נניח שזה עובד ונבדוק את זה במבחן הבא (שליחת הודעה)
    sub_frame = "SUBSCRIBE\ndestination:sci-fi\nid:78\n\n\x00"
    sock.sendall(sub_frame.encode('utf-8'))
    print(">>> Subscribe frame sent.")

    time.sleep(0.5)

    # ---------------------------------------------------------
    # TEST 3: SEND & RECEIVE (שליחת הודעה וקבלתה בחזרה)
    # ---------------------------------------------------------
    print("\n--- TEST 3: Sending SEND frame to 'sci-fi' ---")
    msg_body = "May the force be with you"
    send_frame = f"SEND\ndestination:sci-fi\n\n{msg_body}\n\x00"
    sock.sendall(send_frame.encode('utf-8'))
    print(f"Sent message: '{msg_body}'")

    print("...Waiting for server to echo the message back...")
    try:
        sock.settimeout(3) # אם לא נקבל תשובה תוך 3 שניות - נכשל
        response = sock.recv(1024).decode('utf-8')
        print("Server Response:")
        print(response)

        if "MESSAGE" in response and msg_body in response:
            print(">>> TEST 3 PASSED: Server sent the message back to us!")
        else:
            print(">>> TEST 3 FAILED: We didn't get a proper MESSAGE frame.")
    except socket.timeout:
        print(">>> TEST 3 FAILED: Timeout! Server didn't send the message back.")
    except Exception as e:
        print(f"Error reading socket: {e}")

    sock.settimeout(None) # ביטול הטיימר
    time.sleep(0.5)

    # ---------------------------------------------------------
    # TEST 4: DISCONNECT
    # ---------------------------------------------------------
    print("\n--- TEST 4: Sending DISCONNECT frame ---")
    disconnect_frame = "DISCONNECT\nreceipt:77\n\n\x00"
    sock.sendall(disconnect_frame.encode('utf-8'))
    
    try:
        # בדרך כלל מצפים ל-RECEIPT לפני הסגירה
        final_data = sock.recv(1024).decode('utf-8')
        print("Server Response (should be RECEIPT):")
        print(final_data)
        if "RECEIPT" in final_data:
             print(">>> TEST 4 PASSED: Disconnected gracefully.")
    except:
        print("Socket closed or no receipt received.")

    print("\n--- Closing Socket ---")
    sock.close()

if __name__ == "__main__":
    run_test_suite()
#!/usr/bin/env python3
"""
Basic Python Server for STOMP Assignment – Stage 3.3

IMPORTANT:
DO NOT CHANGE the server name or the basic protocol.
Students should EXTEND this server by implementing
the methods below.
"""

import socket
import sqlite3
import sys
import threading


SERVER_NAME = "STOMP_PYTHON_SQL_SERVER"  # DO NOT CHANGE!
DB_FILE = "stomp_server.db"              # DO NOT CHANGE!

db_lock = threading.Lock()

def recv_null_terminated(sock: socket.socket) -> str:
    data = b""
    while True:
        chunk = sock.recv(1024)
        if not chunk:
            return ""
        data += chunk
        if b"\0" in data:
            msg, _ = data.split(b"\0", 1)
            return msg.decode("utf-8", errors="replace")


def init_database():
    conn = sqlite3.connect(DB_FILE)
    c = conn.cursor()

    # Users Table
    c.execute('''
        CREATE TABLE IF NOT EXISTS users (
            username TEXT PRIMARY KEY,
            password TEXT NOT NULL,
            registration_date TEXT DEFAULT (datetime('now'))
        )
    ''')

    # Login History Table
    c.execute('''
        CREATE TABLE IF NOT EXISTS login_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            login_time TEXT NOT NULL,
            logout_time TEXT,
            FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE
        )
    ''')

    # File Tracking Table
    c.execute('''
        CREATE TABLE IF NOT EXISTS file_tracking (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT NOT NULL,
            filename TEXT NOT NULL,
            upload_time TEXT NOT NULL,
            game_channel TEXT NOT NULL,
            FOREIGN KEY(username) REFERENCES users(username) ON DELETE CASCADE
        )
    ''')

    conn.commit()
    conn.close()
    print(f"[{SERVER_NAME}] Database initialized at {DB_FILE}")


def execute_sql_command(sql_command: str) -> str:
    """
    Executes INSERT, UPDATE, DELETE commands.
    """
    try:
        with db_lock:
            conn = sqlite3.connect(DB_FILE)
            c = conn.cursor()
            c.execute(sql_command)
            conn.commit()
            conn.close()
        return "SUCCESS|()"
    except sqlite3.Error as e:
        return f"ERROR|{str(e)}"


def execute_sql_query(sql_query: str) -> str:
    """
    Executes SELECT queries.
    """
    try:
        with db_lock:
            conn = sqlite3.connect(DB_FILE)
            c = conn.cursor()
            c.execute(sql_query)
            rows = c.fetchall()
            conn.close()
        
        # Format: SUCCESS|col1|col2|col3...
        # splits the whole string by '|' and iterates through it linearly.
        flat_list = []
        for row in rows:
            for col in row:
                flat_list.append(str(col))
        
        # If no results, just return SUCCESS| (empty list)
        return "SUCCESS|" + "|".join(flat_list)

    except sqlite3.Error as e:
        return f"ERROR|{str(e)}"


def print_server_report():
    """
    Prints the summary report to standard output (Server Console).
    """
    try:
        with db_lock:
            conn = sqlite3.connect(DB_FILE, timeout=10)
            c = conn.cursor()
            
            print("\n" + "="*40)
            print("       SERVER REPORT       ")
            print("="*40)
            
            print("\n[1] Registered Users:")
            c.execute("SELECT username, registration_date FROM users")
            users = c.fetchall()
            if not users:
                print("    (No users found)")
            for row in users:
                print(f"    - {row[0]} (Registered: {row[1]})")

            print("\n[2] Login History:")
            c.execute("SELECT username, login_time, logout_time FROM login_history ORDER BY username, login_time DESC")
            history = c.fetchall()
            if not history:
                print("    (No history found)")
            
            current_user = None
            for row in history:
                username, login, logout = row
                if username != current_user:
                    print(f"    User: {username}")
                    current_user = username
                logout_str = logout if logout else "Active"
                print(f"      Login: {login} -> Logout: {logout_str}")

            print("\n[3] Uploaded Files:")
            c.execute("SELECT username, filename, game_channel, upload_time FROM file_tracking ORDER BY upload_time DESC")
            files = c.fetchall()
            if not files:
                print("    (No files uploaded)")
            for row in files:
                print(f"    {row[1]} (by {row[0]} in {row[2]}) at {row[3]}")
            
            print("\n" + "="*40 + "\n")
            conn.close()
            return "SUCCESS|Report printed to server console"

    except Exception as e:
        print(f"Error generating report: {e}")
        return f"ERROR|{str(e)}"

def handle_client(client_socket: socket.socket, addr):
    print(f"[{SERVER_NAME}] Client connected from {addr}")

    try:
        while True:
            message = recv_null_terminated(client_socket)
            if message == "":
                break

            print(f"[{SERVER_NAME}] Received:")
            print(message)

            response = ""
            
            # Check the command type
            clean_msg = message.strip().upper()

            # Route to the correct function
            if clean_msg == "REPORT":
                response = print_server_report()

            elif clean_msg.startswith("SELECT"):
                response = execute_sql_query(message)
                
            else:
                response = execute_sql_command(message)

            # Send the response back to Java client
            client_socket.sendall((response + "\0").encode("utf-8"))

    except Exception as e:
        print(f"[{SERVER_NAME}] Error handling client {addr}: {e}")
    finally:
        try:
            client_socket.close()
        except Exception:
            pass
        print(f"[{SERVER_NAME}] Client {addr} disconnected")


def start_server(host="127.0.0.1", port=7778):
    
    init_database() # Create database tables if not exist

    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

    try:
        server_socket.bind((host, port))
        server_socket.listen(5)
        print(f"[{SERVER_NAME}] Server started on {host}:{port}")
        print(f"[{SERVER_NAME}] Waiting for connections...")

        while True:
            client_socket, addr = server_socket.accept()
            t = threading.Thread(
                target=handle_client,
                args=(client_socket, addr),
                daemon=True
            )
            t.start()

    except KeyboardInterrupt:
        print(f"\n[{SERVER_NAME}] Shutting down server...")
    finally:
        try:
            server_socket.close()
        except Exception:
            pass


if __name__ == "__main__":
    port = 7778
    if len(sys.argv) > 1:
        raw_port = sys.argv[1].strip()
        try:
            port = int(raw_port)
        except ValueError:
            print(f"Invalid port '{raw_port}', falling back to default {port}")

    start_server(port=port)

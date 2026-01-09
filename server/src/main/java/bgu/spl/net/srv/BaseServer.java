package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol; // Import the correct protocol

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

public abstract class BaseServer<T> implements Server<T> {

    private final int port;
    // Changed from MessagingProtocol<T> to StompMessagingProtocol<T>
    private final Supplier<StompMessagingProtocol<T>> protocolFactory;
    private final Supplier<MessageEncoderDecoder<T>> encdecFactory;
    private ServerSocket sock;
    
    private final ConnectionsImpl<T> connections = new ConnectionsImpl<>(); 
    private int idCounter = 0;

    public BaseServer(
            int port,
            Supplier<StompMessagingProtocol<T>> protocolFactory, // Update Constructor
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {

        this.port = port;
        this.protocolFactory = protocolFactory;
        this.encdecFactory = encdecFactory;
        this.sock = null;
    }

    @Override
    public void serve() {
        try (ServerSocket serverSock = new ServerSocket(port)) {
			System.out.println("Server started");

            this.sock = serverSock;

            while (!Thread.currentThread().isInterrupted()) {

                Socket clientSock = serverSock.accept();

                // Pass the correct protocol type to the handler
                BlockingConnectionHandler<T> handler = new BlockingConnectionHandler<>(
                        clientSock,
                        encdecFactory.get(),
                        protocolFactory.get(), // This now returns StompMessagingProtocol
                        idCounter,
                        connections
                );

                idCounter++;

                execute(handler);
            }
        } catch (IOException ex) {
        }

        System.out.println("server closed");
    }

    @Override
    public void close() throws IOException {
		if (sock != null)
			sock.close();
    }

    protected abstract void execute(BlockingConnectionHandler<T>  handler);
}
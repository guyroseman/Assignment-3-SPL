package bgu.spl.net.impl.stomp;

import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol;
import java.util.function.Supplier;

public class StompServer {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: StompServer <port> <tpc|reactor>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        String serverType = args[1];

        // Initialize the shared Connections object once.
        // This object holds the map of topics and active users.
        ConnectionsImpl<String> sharedConnections = new ConnectionsImpl<>();

        // Pass the SAME sharedConnections object to every new Protocol instance.
        // This ensures all clients see the same data (subscriptions/topics).
        Supplier<StompMessagingProtocol<String>> protocolFactory = () -> new StompMessagingProtocolImpl(sharedConnections);

        Supplier<MessageEncoderDecoder<String>> encoderFactory = () -> new StompEncoderDecoder();

        if (serverType.equals("tpc")) {
            Server.threadPerClient(
                    port,
                    protocolFactory,
                    encoderFactory
            ).serve();

        } else if (serverType.equals("reactor")) {
            Server.reactor(
                    Runtime.getRuntime().availableProcessors(),
                    port,
                    protocolFactory,
                    encoderFactory
            ).serve();
        }
    }
}
package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol; // Import

import java.io.Closeable;
import java.util.function.Supplier;

public interface Server<T> extends Closeable {

    void serve();

    // Update the factory type to StompMessagingProtocol<T>
    static <T> Server<T> threadPerClient(
            int port,
            Supplier<StompMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encoderDecoderFactory) {

        return new BaseServer<T>(port, protocolFactory, encoderDecoderFactory) {
            @Override
            protected void execute(BlockingConnectionHandler<T> handler) {
                new Thread(handler).start();
            }
        };
    }

    // Update the reactor as well
    static <T> Server<T> reactor(
            int nThreads,
            int port,
            Supplier<StompMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encoderDecoderFactory) {
        
        // Note: You will need to update Reactor implementation similarly to BaseServer
        // to handle StompMessagingProtocol and Connections.
        return new Reactor<T>(nThreads, port, protocolFactory, encoderDecoderFactory);
    }
}
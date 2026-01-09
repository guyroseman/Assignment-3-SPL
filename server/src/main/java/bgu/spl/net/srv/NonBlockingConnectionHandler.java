package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.StompMessagingProtocol; // Correct Import

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NonBlockingConnectionHandler<T> implements ConnectionHandler<T> {

    private static final int BUFFER_ALLOCATION_SIZE = 8192;

    private final StompMessagingProtocol<T> protocol; // Changed type
    private final MessageEncoderDecoder<T> encdec;
    private final Queue<ByteBuffer> writeQueue = new ConcurrentLinkedQueue<>();
    private final SocketChannel chan;
    private final Reactor<T> reactor;

    public NonBlockingConnectionHandler(
            MessageEncoderDecoder<T> reader,
            StompMessagingProtocol<T> protocol,
            SocketChannel chan,
            Reactor<T> reactor,
            int connectionId,       // New Arg
            Connections<T> connections) { // New Arg

        this.chan = chan;
        this.encdec = reader;
        this.protocol = protocol;
        this.reactor = reactor;
        
        // 1. Add this handler to the connections map
        ((ConnectionsImpl<T>) connections).addConnection(connectionId, this);

        // 2. Start the protocol
        protocol.start(connectionId, connections);
    }

    public Runnable continueRead() {
        ByteBuffer buf = ByteBuffer.allocate(BUFFER_ALLOCATION_SIZE);

        try {
            if (chan.read(buf) == -1) {
                close();
            } else {
                buf.flip();
                return () -> {
                    try {
                        while (buf.hasRemaining()) {
                            T nextMessage = encdec.decodeNextByte(buf.get());
                            if (nextMessage != null) {
                                // 3. Process the message (Void return)
                                protocol.process(nextMessage);
                                
                                // Note: We do NOT write response here anymore.
                                // The protocol calls connections.send -> which calls this.send()
                            }
                        }
                    } finally {
                        buf.clear();
                    }
                };
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            close();
        }
        return null;
    }

    public void continueWrite() {
        while (!writeQueue.isEmpty()) {
            try {
                ByteBuffer top = writeQueue.peek();
                chan.write(top);
                if (top.hasRemaining()) {
                    return;
                }
                writeQueue.remove();
            } catch (IOException ex) {
                ex.printStackTrace();
                close();
            }
        }

        if (writeQueue.isEmpty()) {
            if (protocol.shouldTerminate()) close();
            else reactor.updateInterestedOps(chan, SelectionKey.OP_READ);
        }
    }

    @Override
    public void close() {
        try {
            chan.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public boolean isClosed() {
        return !chan.isOpen();
    }

    @Override
    public void send(T msg) {
        // Implementation of send for ConnectionsImpl
        if (msg != null) {
            writeQueue.add(ByteBuffer.wrap(encdec.encode(msg)));
            reactor.updateInterestedOps(chan, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        }
    }
}
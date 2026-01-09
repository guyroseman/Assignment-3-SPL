package bgu.spl.net.impl.stomp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StompEncoderDecoder implements MessageEncoderDecoder<String> {

    private byte[] bytes = new byte[1 << 10]; // start with 1k
    private int len = 0;

    @Override
    public String decodeNextByte(byte nextByte) {
        // In STOMP, the end of the message is indicated solely by the Null character.
        // We do not stop at '\n' because it is a valid character within the message body (e.g. headers, body text).
        if (nextByte == '\u0000') {
            return popString();
        }

        pushByte(nextByte);
        return null; // not a complete frame yet
    }

    @Override
    public byte[] encode(String message) {
        // Adding the Null character at the end of the message as required by the STOMP protocol.
        return (message + "\u0000").getBytes(StandardCharsets.UTF_8);
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private String popString() {
        String result = new String(bytes, 0, len, StandardCharsets.UTF_8);
        len = 0;
        return result;
    }
}
package de.tum.cit.aet.artemis.core.config.websocket;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GzipMessageConverter extends MappingJackson2MessageConverter {

    private static final Logger log = LoggerFactory.getLogger(GzipMessageConverter.class);

    public static final String COMPRESSION_HEADER_KEY = "X-Compressed";

    public static final Map<String, Object> COMPRESSION_HEADER = Map.of(COMPRESSION_HEADER_KEY, true);

    public GzipMessageConverter(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    // Incoming message from client, potentially compressed, needs to be decompressed
    @Override
    protected Object convertFromInternal(Message<?> message, Class<?> targetClass, Object conversionHint) {
        var nativeHeaders = message.getHeaders().get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
        if (nativeHeaders instanceof Map<?, ?> nativeMapHeaders) {
            final var messageIsCompressed = containsCompressionHeader(nativeMapHeaders);
            if (messageIsCompressed) {
                log.info("Decompressing message payload for incoming message");
                Object payload = message.getPayload();
                if (payload instanceof byte[] bytePayload) {
                    byte[] decompressed = decodeAndDecompress(bytePayload);
                    return super.convertFromInternal(new Message<>() {

                        @Override
                        public Object getPayload() {
                            return decompressed;
                        }

                        @Override
                        public MessageHeaders getHeaders() {
                            return message.getHeaders();
                        }
                    }, targetClass, conversionHint);
                }
            }
        }
        return super.convertFromInternal(message, targetClass, conversionHint);
    }

    private static boolean containsCompressionHeader(Map<?, ?> headers) {
        var value = headers.get(COMPRESSION_HEADER_KEY);
        if (value instanceof List<?> list && !list.isEmpty()) {
            return checkSimpleValue(list.getFirst());
        }
        return checkSimpleValue(value);
    }

    private static boolean checkSimpleValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return Boolean.TRUE.equals(booleanValue);
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue);
        }
        return false;
    }

    // Outgoing message to client, potentially compressible, needs to be compressed
    // NOTE: headers is immutable here and cannot be modified
    @Override
    protected Object convertToInternal(Object payload, MessageHeaders headers, Object conversionHint) {
        Object original = super.convertToInternal(payload, headers, conversionHint);
        if (original instanceof byte[] originalBytes) {
            // Check the native headers to see if the message should be compressed
            var nativeHeaders = headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS);
            if (nativeHeaders instanceof Map<?, ?> nativeMapHeaders) {
                boolean shouldCompress = containsCompressionHeader(nativeMapHeaders);
                if (shouldCompress) {
                    String compressedBase64String = compressAndEncode(originalBytes);
                    byte[] compressed = compressedBase64String.getBytes(StandardCharsets.UTF_8);
                    double percentageSaved = 100 * (1 - (double) compressed.length / originalBytes.length);
                    log.debug("Compressed message payload from {} to {} (saved {}% payload size)", originalBytes.length, compressed.length, String.format("%.1f", percentageSaved));
                    return compressed;
                }
            }
            return originalBytes;
        }
        return original;
    }

    // NOTE: we use a hybrid approach here mixing string based and binary data when compression is active.
    // As a compromise, we use Base64 encoding to ensure that the compressed data can be safely transmitted as a string (without interfering with the WebSocket protocol).
    // This can still reduce the payload size by up to 95% (for large payloads) compared to the original binary data (in standard json).
    private String compressAndEncode(byte[] data) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream(); GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteStream)) {
            gzipOutputStream.write(data);
            gzipOutputStream.finish();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to compressAndEncode message payload", e);
        }
    }

    private byte[] decodeAndDecompress(byte[] data) {
        // Step 1: Decode Base64 to binary
        byte[] binaryData = Base64.getDecoder().decode(data);

        // Step 2: Decompress the binary data
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(binaryData);
                GZIPInputStream gzipStream = new GZIPInputStream(byteStream);
                ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            // Efficiently transfers all bytes
            gzipStream.transferTo(outStream);
            return outStream.toByteArray();
        }
        catch (Exception e) {
            throw new RuntimeException("Failed to decodeAndDecompress message payload", e);
        }
    }
}

package de.tum.cit.aet.artemis.core.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.communication.dto.AuthorDTO;

class GzipMessageConverterTest {

    private GzipMessageConverter converter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        converter = new GzipMessageConverter(objectMapper);
    }

    @Test
    void testSupportsAnyClass() {
        assertThat(converter.supports(String.class)).isTrue();
        assertThat(converter.supports(Object.class)).isTrue();
    }

    @Test
    void testConvertFromInternalWithCompressedPayload() throws Exception {
        // Arrange
        var author = new AuthorDTO(1L, "Test", "Test");
        String payload = objectMapper.writeValueAsString(author);
        byte[] compressedPayload = compressAndEncode(payload.getBytes()).getBytes();

        Message<byte[]> message = mock(Message.class);
        MessageHeaders headers = mock(MessageHeaders.class);
        Map<String, Object> nativeHeaders = Map.of(GzipMessageConverter.COMPRESSION_HEADER_KEY, List.of("true"));

        when(message.getPayload()).thenReturn(compressedPayload);
        when(message.getHeaders()).thenReturn(headers);
        when(headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS)).thenReturn(nativeHeaders);

        // Act
        Object result = converter.convertFromInternal(message, AuthorDTO.class, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(author);
    }

    @Test
    void testConvertFromInternalWithoutCompressedPayload() throws Exception {
        // Arrange
        var author = new AuthorDTO(1L, "Test", "Test");
        String payload = objectMapper.writeValueAsString(author);
        Message<String> message = mock(Message.class);
        MessageHeaders headers = mock(MessageHeaders.class);

        when(message.getPayload()).thenReturn(payload);
        when(message.getHeaders()).thenReturn(headers);
        when(headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS)).thenReturn(null);

        // Act
        Object result = converter.convertFromInternal(message, AuthorDTO.class, null);

        // Assert
        assertThat(result).isEqualTo(author);
    }

    @Test
    void testConvertToInternalWithCompressionEnabled() throws Exception {
        // Arrange
        var author = new AuthorDTO(1L, "Test", "Test");
        String payload = objectMapper.writeValueAsString(author);
        byte[] payloadBytes = payload.getBytes();

        MessageHeaders headers = mock(MessageHeaders.class);
        Map<String, Object> nativeHeaders = Map.of(GzipMessageConverter.COMPRESSION_HEADER_KEY, List.of("true"));

        when(headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS)).thenReturn(nativeHeaders);

        // Act
        Object result = converter.convertToInternal(payloadBytes, headers, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(byte[].class);
    }

    @Test
    void testConvertToInternalWithoutCompression() throws Exception {
        // Arrange
        var author = new AuthorDTO(1L, "Test", "Test");
        String payload = objectMapper.writeValueAsString(author);
        byte[] payloadBytes = payload.getBytes();

        MessageHeaders headers = mock(MessageHeaders.class);
        when(headers.get(NativeMessageHeaderAccessor.NATIVE_HEADERS)).thenReturn(null);

        // Act
        Object result = converter.convertToInternal(author, headers, null);

        // Assert
        assertThat(result).isEqualTo(payloadBytes);
    }

    // Utility for compression
    private String compressAndEncode(byte[] data) throws Exception {
        try (var byteStream = new ByteArrayOutputStream(); var gzipStream = new GZIPOutputStream(byteStream)) {
            gzipStream.write(data);
            gzipStream.finish();
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        }
    }
}

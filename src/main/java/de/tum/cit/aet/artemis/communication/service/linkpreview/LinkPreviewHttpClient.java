package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Minimal HTTP client for retrieving bounded link preview responses from previously resolved addresses.
 */
final class LinkPreviewHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private static final int MAX_HEADER_SIZE = 64 * 1024;

    private static final int BUFFER_SIZE = 8192;

    private LinkPreviewHttpClient() {
    }

    /**
     * Retrieves a response from one of the previously resolved addresses.
     *
     * @param validatedUrl        the validated URL and its resolved addresses
     * @param requestDeadline     the deadline shared by the entire redirect chain
     * @param maximumResponseSize the maximum response body size in bytes
     * @return the response status, headers, and body
     * @throws IOException if the request fails or the response is invalid
     */
    static Response get(LinkPreviewUrlValidator.ValidatedUrl validatedUrl, RequestDeadline requestDeadline, int maximumResponseSize) throws IOException {
        return get(validatedUrl, requestDeadline, maximumResponseSize, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    static Response get(LinkPreviewUrlValidator.ValidatedUrl validatedUrl, RequestDeadline requestDeadline, int maximumResponseSize, SSLSocketFactory sslSocketFactory)
            throws IOException {
        try (Socket socket = openSocket(validatedUrl, requestDeadline, sslSocketFactory)) {
            writeRequest(socket.getOutputStream(), validatedUrl.uri());

            try (InputStream inputStream = new BufferedInputStream(new DeadlineInputStream(socket.getInputStream(), socket, requestDeadline))) {
                ResponseHead responseHead = readResponseHead(inputStream);
                byte[] body = responseHead.statusCode() >= 200 && responseHead.statusCode() < 300 ? readResponseBody(inputStream, responseHead, maximumResponseSize) : new byte[0];
                return new Response(responseHead.statusCode(), responseHead.headers(), body);
            }
        }
    }

    private static Socket openSocket(LinkPreviewUrlValidator.ValidatedUrl validatedUrl, RequestDeadline requestDeadline, SSLSocketFactory sslSocketFactory) throws IOException {
        URI uri = validatedUrl.uri();
        int port = getPort(uri);
        IOException lastException = null;

        for (InetAddress address : validatedUrl.addresses()) {
            Socket socket = SocketFactory.getDefault().createSocket();
            try {
                socket.connect(new InetSocketAddress(address, port), requestDeadline.timeoutMillis(CONNECT_TIMEOUT)); // nosemgrep
                if ("https".equalsIgnoreCase(uri.getScheme())) {
                    return createSecureSocket(socket, uri.getHost(), port, requestDeadline, sslSocketFactory);
                }
                return socket;
            }
            catch (IOException e) {
                lastException = e;
                closeQuietly(socket);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
        throw new IOException("The link preview host did not resolve to an address");
    }

    private static Socket createSecureSocket(Socket socket, String host, int port, RequestDeadline requestDeadline, SSLSocketFactory sslSocketFactory) throws IOException {
        SSLSocket secureSocket = (SSLSocket) sslSocketFactory.createSocket(socket, host, port, true);
        try {
            SSLParameters sslParameters = secureSocket.getSSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
            sslParameters.setServerNames(List.of(new SNIHostName(host)));
            sslParameters.setApplicationProtocols(new String[] { "http/1.1" });
            secureSocket.setSSLParameters(sslParameters);
            secureSocket.setSoTimeout(requestDeadline.timeoutMillis(null));
            secureSocket.startHandshake();
            return secureSocket;
        }
        catch (IOException | RuntimeException e) {
            closeQuietly(secureSocket);
            throw e;
        }
    }

    private static void writeRequest(OutputStream outputStream, URI uri) throws IOException {
        String requestTarget = getRequestTarget(uri);
        String hostHeader = getHostHeader(uri);
        String request = "GET " + requestTarget + " HTTP/1.1\r\n" + "Host: " + hostHeader + "\r\n" + "Accept: text/html,application/xhtml+xml\r\n"
                + "User-Agent: Artemis Link Preview\r\n" + "Connection: close\r\n\r\n";
        outputStream.write(request.getBytes(StandardCharsets.US_ASCII));
        outputStream.flush();
    }

    private static String getRequestTarget(URI uri) {
        URI asciiUri = URI.create(uri.toASCIIString());
        String path = asciiUri.getRawPath();
        String requestTarget = path == null || path.isEmpty() ? "/" : path;
        if (asciiUri.getRawQuery() != null) {
            requestTarget += "?" + asciiUri.getRawQuery();
        }
        return requestTarget;
    }

    private static String getHostHeader(URI uri) {
        int port = getPort(uri);
        int defaultPort = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        return port == defaultPort ? uri.getHost() : uri.getHost() + ":" + port;
    }

    private static int getPort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static ResponseHead readResponseHead(InputStream inputStream) throws IOException {
        ResponseHead responseHead;
        do {
            responseHead = readSingleResponseHead(inputStream);
        }
        while (responseHead.statusCode() >= 100 && responseHead.statusCode() < 200 && responseHead.statusCode() != 101);
        return responseHead;
    }

    private static ResponseHead readSingleResponseHead(InputStream inputStream) throws IOException {
        Line statusLine = readLine(inputStream);
        if (statusLine == null) {
            throw new EOFException("The link preview response did not include a status line");
        }
        int headerSize = statusLine.byteCount();
        int statusCode = parseStatusCode(statusLine.value());
        Map<String, List<String>> headers = new HashMap<>();

        while (true) {
            Line headerLine = readLine(inputStream);
            if (headerLine == null) {
                throw new EOFException("The link preview response headers ended unexpectedly");
            }
            headerSize += headerLine.byteCount();
            if (headerSize > MAX_HEADER_SIZE) {
                throw new IOException("The link preview response headers exceeded the maximum size");
            }
            if (headerLine.value().isEmpty()) {
                return new ResponseHead(statusCode, immutableHeaders(headers));
            }

            int separator = headerLine.value().indexOf(':');
            if (separator <= 0) {
                throw new IOException("The link preview response included an invalid header");
            }
            String name = headerLine.value().substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = headerLine.value().substring(separator + 1).trim();
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }
    }

    private static Map<String, List<String>> immutableHeaders(Map<String, List<String>> headers) {
        Map<String, List<String>> immutableHeaders = new HashMap<>();
        headers.forEach((name, values) -> immutableHeaders.put(name, List.copyOf(values)));
        return Map.copyOf(immutableHeaders);
    }

    private static int parseStatusCode(String statusLine) throws IOException {
        String[] parts = statusLine.split("\\s+", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IOException("The link preview response included an invalid status line");
        }
        try {
            int statusCode = Integer.parseInt(parts[1]);
            if (statusCode < 100 || statusCode > 999) {
                throw new IOException("The link preview response included an invalid status code");
            }
            return statusCode;
        }
        catch (NumberFormatException e) {
            throw new IOException("The link preview response included an invalid status code", e);
        }
    }

    private static byte[] readResponseBody(InputStream inputStream, ResponseHead responseHead, int maximumResponseSize) throws IOException {
        if (responseHead.statusCode() == 204 || responseHead.statusCode() == 205) {
            return new byte[0];
        }
        List<String> transferEncoding = responseHead.headers().get("transfer-encoding");
        if (transferEncoding != null) {
            if (transferEncoding.size() != 1 || !"chunked".equalsIgnoreCase(transferEncoding.getFirst())) {
                throw new IOException("The link preview response used an unsupported transfer encoding");
            }
            return readChunkedBody(inputStream, maximumResponseSize);
        }

        Long contentLength = getContentLength(responseHead.headers());
        if (contentLength != null) {
            if (contentLength > maximumResponseSize) {
                throw new IOException("Link preview response exceeded the maximum size");
            }
            return readFixedLengthBody(inputStream, contentLength.intValue());
        }
        return readUntilEnd(inputStream, maximumResponseSize);
    }

    private static Long getContentLength(Map<String, List<String>> headers) throws IOException {
        List<String> values = headers.get("content-length");
        if (values == null) {
            return null;
        }

        Long contentLength = null;
        try {
            for (String value : values) {
                for (String part : value.split(",")) {
                    long parsedLength = Long.parseLong(part.trim());
                    if (parsedLength < 0 || contentLength != null && contentLength != parsedLength) {
                        throw new IOException("The link preview response included an invalid content length");
                    }
                    contentLength = parsedLength;
                }
            }
        }
        catch (NumberFormatException e) {
            throw new IOException("The link preview response included an invalid content length", e);
        }
        return contentLength;
    }

    private static byte[] readFixedLengthBody(InputStream inputStream, int contentLength) throws IOException {
        byte[] body = new byte[contentLength];
        int offset = 0;
        while (offset < contentLength) {
            int read = inputStream.read(body, offset, contentLength - offset);
            if (read < 0) {
                throw new EOFException("The link preview response body ended unexpectedly");
            }
            offset += read;
        }
        return body;
    }

    private static byte[] readUntilEnd(InputStream inputStream, int maximumResponseSize) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            int bytesToRead = Math.min(buffer.length, maximumResponseSize - body.size() + 1);
            int read = inputStream.read(buffer, 0, bytesToRead);
            if (read < 0) {
                return body.toByteArray();
            }
            if (body.size() + read > maximumResponseSize) {
                throw new IOException("Link preview response exceeded the maximum size");
            }
            body.write(buffer, 0, read);
        }
    }

    private static byte[] readChunkedBody(InputStream inputStream, int maximumResponseSize) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            Line chunkHeader = readLine(inputStream);
            if (chunkHeader == null) {
                throw new EOFException("The link preview response body ended unexpectedly");
            }
            String sizeValue = chunkHeader.value().split(";", 2)[0].trim();
            long chunkSize;
            try {
                chunkSize = Long.parseLong(sizeValue, 16);
            }
            catch (NumberFormatException e) {
                throw new IOException("The link preview response included an invalid chunk size", e);
            }
            if (chunkSize < 0 || chunkSize > maximumResponseSize - body.size()) {
                throw new IOException("Link preview response exceeded the maximum size");
            }
            if (chunkSize == 0) {
                readTrailers(inputStream);
                return body.toByteArray();
            }

            byte[] chunk = readFixedLengthBody(inputStream, Math.toIntExact(chunkSize));
            body.write(chunk);
            requireCrLf(inputStream);
        }
    }

    private static void readTrailers(InputStream inputStream) throws IOException {
        int trailerSize = 0;
        while (true) {
            Line trailer = readLine(inputStream);
            if (trailer == null) {
                throw new EOFException("The link preview response trailers ended unexpectedly");
            }
            trailerSize += trailer.byteCount();
            if (trailerSize > MAX_HEADER_SIZE) {
                throw new IOException("The link preview response trailers exceeded the maximum size");
            }
            if (trailer.value().isEmpty()) {
                return;
            }
        }
    }

    private static void requireCrLf(InputStream inputStream) throws IOException {
        if (inputStream.read() != '\r' || inputStream.read() != '\n') {
            throw new IOException("The link preview response included invalid chunk framing");
        }
    }

    private static Line readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (line.size() <= MAX_HEADER_SIZE) {
            int value = inputStream.read();
            if (value < 0) {
                return line.size() == 0 ? null : new Line(line.toString(StandardCharsets.US_ASCII), line.size());
            }
            if (value == '\n') {
                byte[] bytes = line.toByteArray();
                int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new Line(new String(bytes, 0, length, StandardCharsets.US_ASCII), bytes.length + 1);
            }
            line.write(value);
        }
        throw new IOException("The link preview response included an oversized header line");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        }
        catch (IOException ignored) {
            // Preserve the exception that caused the connection attempt to fail.
        }
    }

    record Response(int statusCode, Map<String, List<String>> headers, byte[] body) {

        String firstHeader(String name) {
            List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
    }

    private record ResponseHead(int statusCode, Map<String, List<String>> headers) {
    }

    private record Line(String value, int byteCount) {
    }

    static final class RequestDeadline {

        private final long startedAt = System.nanoTime();

        private final long timeoutNanos;

        RequestDeadline(Duration requestTimeout) {
            if (requestTimeout.isZero() || requestTimeout.isNegative()) {
                throw new IllegalArgumentException("The link preview request timeout must be positive");
            }
            timeoutNanos = requestTimeout.toNanos();
        }

        private int timeoutMillis(Duration maximumTimeout) throws SocketTimeoutException {
            long remainingNanos = timeoutNanos - (System.nanoTime() - startedAt);
            if (remainingNanos <= 0) {
                throw new SocketTimeoutException("Link preview request timed out");
            }
            if (maximumTimeout != null) {
                remainingNanos = Math.min(remainingNanos, maximumTimeout.toNanos());
            }
            long timeoutMillis = Math.max(1, Math.ceilDiv(remainingNanos, 1_000_000));
            return (int) Math.min(Integer.MAX_VALUE, timeoutMillis);
        }
    }

    private static final class DeadlineInputStream extends FilterInputStream {

        private final Socket socket;

        private final RequestDeadline requestDeadline;

        private DeadlineInputStream(InputStream inputStream, Socket socket, RequestDeadline requestDeadline) {
            super(inputStream);
            this.socket = socket;
            this.requestDeadline = requestDeadline;
        }

        @Override
        public int read() throws IOException {
            prepareRead();
            return super.read();
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            prepareRead();
            return super.read(bytes, offset, length);
        }

        private void prepareRead() throws IOException {
            socket.setSoTimeout(requestDeadline.timeoutMillis(null));
        }
    }
}

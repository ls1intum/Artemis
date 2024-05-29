/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.MessageFormat;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_X_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;
import static org.eclipse.jgit.util.HttpSupport.HDR_ETAG;
import static org.eclipse.jgit.util.HttpSupport.TEXT_PLAIN;

/**
 * Common utility functions for servlets.
 */
public final class ServletUtils {

    /** Request attribute which stores the {@link Repository} instance. */
    public static final String ATTRIBUTE_REPOSITORY = "org.eclipse.jgit.Repository";

    /** Request attribute storing either UploadPack or ReceivePack. */
    public static final String ATTRIBUTE_HANDLER = "org.eclipse.jgit.transport.UploadPackOrReceivePack";

    /**
     * Get the selected repository from the request.
     *
     * @param req
     *                the current request.
     * @return the repository; never null.
     * @throws IllegalStateException
     *                                   the repository was not set by the filter, the servlet is
     *                                   being invoked incorrectly and the programmer should ensure
     *                                   the filter runs before the servlet.
     * @see #ATTRIBUTE_REPOSITORY
     */
    public static Repository getRepository(ServletRequest req) {
        Repository db = (Repository) req.getAttribute(ATTRIBUTE_REPOSITORY);
        if (db == null) {
            throw new IllegalStateException(HttpServerText.get().expectedRepositoryAttribute);
        }
        return db;
    }

    /**
     * Open the request input stream, automatically inflating if necessary.
     * <p>
     * This method automatically inflates the input stream if the request
     * {@code Content-Encoding} header was set to {@code gzip} or the legacy
     * {@code x-gzip}.
     *
     * @param req
     *                the incoming request whose input stream needs to be opened.
     * @return an input stream to read the raw, uncompressed request body.
     * @throws IOException
     *                         if an input or output exception occurred.
     */
    public static InputStream getInputStream(HttpServletRequest req) throws IOException {
        InputStream in = req.getInputStream();
        final String enc = req.getHeader(HDR_CONTENT_ENCODING);
        if (ENCODING_GZIP.equals(enc) || ENCODING_X_GZIP.equals(enc)) {
            in = new GZIPInputStream(in);
        }
        else if (enc != null) {
            throw new IOException(MessageFormat.format(HttpServerText.get().encodingNotSupportedByThisLibrary, HDR_CONTENT_ENCODING, enc));
        }
        return in;
    }

    /**
     * Consume the entire request body, if one was supplied.
     *
     * @param req
     *                the request whose body must be consumed.
     */
    public static void consumeRequestBody(HttpServletRequest req) {
        if (0 < req.getContentLength() || isChunked(req)) {
            try {
                consumeRequestBody(req.getInputStream());
            }
            catch (IOException e) {
                // Ignore any errors obtaining the input stream.
            }
        }
    }

    static boolean isChunked(HttpServletRequest req) {
        return "chunked".equals(req.getHeader("Transfer-Encoding"));
    }

    /**
     * Consume the rest of the input stream and discard it.
     *
     * @param in
     *               the stream to discard, closed if not null.
     */
    public static void consumeRequestBody(InputStream in) {
        if (in == null) {
            return;
        }
        try (in) {
            while (0 < in.skip(2048) || 0 <= in.read()) {
                // Discard until EOF.
            }
        }
        catch (IOException err) {
            // Discard IOException during read or skip.
        }
        // Discard IOException during close of input stream.
    }

    /**
     * Send a plain text response to a {@code GET} or {@code HEAD} HTTP request.
     * <p>
     * The text response is encoded in the Git character encoding, UTF-8.
     * <p>
     * If the user agent supports a compressed transfer encoding and the content
     * is large enough, the content may be compressed before sending.
     * <p>
     * The {@code ETag} and {@code Content-Length} headers are automatically set
     * by this method. {@code Content-Encoding} is conditionally set if the user
     * agent supports a compressed transfer. Callers are responsible for setting
     * any cache control headers.
     *
     * @param content
     *                    to return to the user agent as this entity's body.
     * @param req
     *                    the incoming request.
     * @param rsp
     *                    the outgoing response.
     * @throws IOException
     *                         the servlet API rejected sending the body.
     */
    public static void sendPlainText(final String content, final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        final byte[] raw = content.getBytes(UTF_8);
        rsp.setContentType(TEXT_PLAIN);
        rsp.setCharacterEncoding(UTF_8.name());
        send(raw, req, rsp);
    }

    /**
     * Send a response to a {@code GET} or {@code HEAD} HTTP request.
     * <p>
     * If the user agent supports a compressed transfer encoding and the content
     * is large enough, the content may be compressed before sending.
     * <p>
     * The {@code ETag} and {@code Content-Length} headers are automatically set
     * by this method. {@code Content-Encoding} is conditionally set if the user
     * agent supports a compressed transfer. Callers are responsible for setting
     * {@code Content-Type} and any cache control headers.
     *
     * @param content
     *                    to return to the user agent as this entity's body.
     * @param req
     *                    the incoming request.
     * @param rsp
     *                    the outgoing response.
     * @throws IOException
     *                         the servlet API rejected sending the body.
     */
    public static void send(byte[] content, final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        content = sendInit(content, req, rsp);
        try (OutputStream out = rsp.getOutputStream()) {
            out.write(content);
            out.flush();
        }
    }

    private static byte[] sendInit(byte[] content, final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        rsp.setHeader(HDR_ETAG, etag(content));
        if (256 < content.length && acceptsGzipEncoding(req)) {
            content = compress(content);
            rsp.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
        }
        rsp.setContentLength(content.length);
        return content;
    }

    static boolean acceptsGzipEncoding(HttpServletRequest req) {
        return acceptsGzipEncoding(req.getHeader(HDR_ACCEPT_ENCODING));
    }

    static boolean acceptsGzipEncoding(String accepts) {
        if (accepts == null) {
            return false;
        }

        int b = 0;
        while (b < accepts.length()) {
            int comma = accepts.indexOf(',', b);
            int e = 0 <= comma ? comma : accepts.length();
            String term = accepts.substring(b, e).trim();
            if (term.equals(ENCODING_GZIP)) {
                return true;
            }
            b = e + 1;
        }
        return false;
    }

    private static byte[] compress(byte[] raw) throws IOException {
        final int maxLen = raw.length + 32;
        final ByteArrayOutputStream out = new ByteArrayOutputStream(maxLen);
        final GZIPOutputStream gz = new GZIPOutputStream(out);
        gz.write(raw);
        gz.finish();
        gz.flush();
        return out.toByteArray();
    }

    private static String etag(byte[] content) {
        final MessageDigest md = Constants.newMessageDigest();
        md.update(content);
        return ObjectId.fromRaw(md.digest()).getName();
    }

    static String identify(Repository repository) {
        String identifier = repository.getIdentifier();
        if (identifier == null) {
            return "unknown";
        }
        return identifier;
    }

    private ServletUtils() {
        // static utility class only
    }
}

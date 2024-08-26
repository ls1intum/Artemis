/*
 * Copyright (C) 2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;
import org.eclipse.jgit.util.TemporaryBuffer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.eclipse.jgit.http.server.ServletUtils.acceptsGzipEncoding;
import static org.eclipse.jgit.util.HttpSupport.ENCODING_GZIP;
import static org.eclipse.jgit.util.HttpSupport.HDR_CONTENT_ENCODING;

/**
 * Buffers a response, trying to gzip it if the user agent supports that.
 * <p>
 * If the response overflows the buffer, gzip is skipped and the response is
 * streamed to the client as its produced, most likely using HTTP/1.1 chunked
 * encoding. This is useful for servlets that produce mixed-mode content, where
 * smaller payloads are primarily pure text that compresses well, while much
 * larger payloads are heavily compressed binary data. {@link UploadPackServlet}
 * is one such servlet.
 */
class SmartOutputStream extends TemporaryBuffer {

    private static final int LIMIT = 32 * 1024;

    private final HttpServletRequest request;

    private final HttpServletResponse response;

    private final boolean compressStream;

    private boolean startedOutput;

    SmartOutputStream(final HttpServletRequest request, final HttpServletResponse response, boolean compressStream) {
        super(LIMIT);
        this.request = request;
        this.response = response;
        this.compressStream = compressStream;
    }

    @Override
    protected OutputStream overflow() throws IOException {
        startedOutput = true;

        OutputStream out = response.getOutputStream();
        if (compressStream && acceptsGzipEncoding(request)) {
            response.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
            out = new GZIPOutputStream(out);
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        super.close();

        if (!startedOutput) {
            // If output hasn't started yet, the entire thing fit into our
            // buffer. Try to use a proper Content-Length header, and also
            // deflate the response with gzip if it will be smaller.
            if (256 < this.length() && acceptsGzipEncoding(request)) {
                TemporaryBuffer gzbuf = new TemporaryBuffer.Heap(LIMIT);
                try {
                    try (GZIPOutputStream gzip = new GZIPOutputStream(gzbuf)) {
                        this.writeTo(gzip, null);
                    }
                    if (gzbuf.length() < this.length()) {
                        response.setHeader(HDR_CONTENT_ENCODING, ENCODING_GZIP);
                        writeResponse(gzbuf);
                        return;
                    }
                }
                catch (IOException err) {
                    // Most likely caused by overflowing the buffer, meaning
                    // its larger if it were compressed. Discard compressed
                    // copy and use the original.
                }
            }
            writeResponse(this);
        }
    }

    private void writeResponse(TemporaryBuffer out) throws IOException {
        // The Content-Length cannot overflow when cast to an int, our
        // hardcoded LIMIT constant above assures us we wouldn't store
        // more than 2 GiB of content in memory.
        response.setContentLength((int) out.length());
        try (OutputStream os = response.getOutputStream()) {
            out.writeTo(os, null);
            os.flush();
        }
    }
}

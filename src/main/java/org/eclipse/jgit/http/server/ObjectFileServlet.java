/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.time.Instant;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.lib.Repository;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.util.HttpSupport.HDR_ETAG;
import static org.eclipse.jgit.util.HttpSupport.HDR_IF_MODIFIED_SINCE;
import static org.eclipse.jgit.util.HttpSupport.HDR_IF_NONE_MATCH;
import static org.eclipse.jgit.util.HttpSupport.HDR_LAST_MODIFIED;

/** Sends any object from {@code GIT_DIR/objects/??/0 38}, or any pack file. */
abstract class ObjectFileServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    static class Loose extends ObjectFileServlet {

        @Serial
        private static final long serialVersionUID = 1L;

        Loose() {
            super("application/x-git-loose-object");
        }

        @Override
        String etag(FileSender sender) {
            Instant lastModified = sender.getLastModified();
            return Long.toHexString(lastModified.getEpochSecond()) + Long.toHexString(lastModified.getNano());
        }
    }

    private abstract static class PackData extends ObjectFileServlet {

        @Serial
        private static final long serialVersionUID = 1L;

        PackData(String contentType) {
            super(contentType);
        }

        @Override
        String etag(FileSender sender) throws IOException {
            return sender.getTailChecksum();
        }
    }

    static class Pack extends PackData {

        @Serial
        private static final long serialVersionUID = 1L;

        Pack() {
            super("application/x-git-packed-objects");
        }
    }

    static class PackIdx extends PackData {

        @Serial
        private static final long serialVersionUID = 1L;

        PackIdx() {
            super("application/x-git-packed-objects-toc");
        }
    }

    private final String contentType;

    ObjectFileServlet(String contentType) {
        this.contentType = contentType;
    }

    abstract String etag(FileSender sender) throws IOException;

    @Override
    public void doGet(final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        serve(req, rsp, true);
    }

    @Override
    protected void doHead(final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        serve(req, rsp, false);
    }

    private void serve(final HttpServletRequest req, final HttpServletResponse rsp, final boolean sendBody) throws IOException {
        final File obj = new File(objects(req), req.getPathInfo());
        final FileSender sender;
        try {
            sender = new FileSender(obj);
        }
        catch (FileNotFoundException e) {
            rsp.sendError(SC_NOT_FOUND);
            return;
        }

        try {
            final String etag = etag(sender);
            // HTTP header Last-Modified header has a resolution of 1 sec, see
            // https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.29
            final long lastModified = sender.getLastModified().getEpochSecond();

            String ifNoneMatch = req.getHeader(HDR_IF_NONE_MATCH);
            if (etag != null && etag.equals(ifNoneMatch)) {
                rsp.sendError(SC_NOT_MODIFIED);
                return;
            }

            long ifModifiedSince = req.getDateHeader(HDR_IF_MODIFIED_SINCE);
            if (0 < lastModified && lastModified < ifModifiedSince) {
                rsp.sendError(SC_NOT_MODIFIED);
                return;
            }

            if (etag != null) {
                rsp.setHeader(HDR_ETAG, etag);
            }
            if (0 < lastModified) {
                rsp.setDateHeader(HDR_LAST_MODIFIED, lastModified);
            }
            rsp.setContentType(contentType);
            sender.serve(req, rsp, sendBody);
        }
        finally {
            sender.close();
        }
    }

    private static File objects(HttpServletRequest req) {
        final Repository db = getRepository(req);
        return ((ObjectDirectory) db.getObjectDatabase()).getDirectory();
    }
}

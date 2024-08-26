/*
 * Copyright (C) 2011, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.eclipse.jgit.internal.transport.parser.FirstCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RequestNotYetReadException;
import org.eclipse.jgit.transport.SideBandOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jgit.util.StringUtils;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_SIDE_BAND_64K;
import static org.eclipse.jgit.transport.SideBandOutputStream.CH_ERROR;
import static org.eclipse.jgit.transport.SideBandOutputStream.SMALL_BUF;

/**
 * Utility functions for handling the Git-over-HTTP protocol.
 */
public class GitSmartHttpTools {

    private static final String INFO_REFS = Constants.INFO_REFS;

    /** Name of the git-upload-pack service. */
    public static final String UPLOAD_PACK = "git-upload-pack";

    /** Name of the git-receive-pack service. */
    public static final String RECEIVE_PACK = "git-receive-pack";

    /** Content type supplied by the client to the /git-upload-pack handler. */
    public static final String UPLOAD_PACK_REQUEST_TYPE = "application/x-git-upload-pack-request";

    /** Content type returned from the /git-upload-pack handler. */
    public static final String UPLOAD_PACK_RESULT_TYPE = "application/x-git-upload-pack-result";

    /** Content type supplied by the client to the /git-receive-pack handler. */
    public static final String RECEIVE_PACK_REQUEST_TYPE = "application/x-git-receive-pack-request";

    /** Content type returned from the /git-receive-pack handler. */
    public static final String RECEIVE_PACK_RESULT_TYPE = "application/x-git-receive-pack-result";

    /** Git service names accepted by the /info/refs?service= handler. */
    public static final List<String> VALID_SERVICES = List.of(UPLOAD_PACK, RECEIVE_PACK);

    private static final String INFO_REFS_PATH = "/" + INFO_REFS;

    private static final String UPLOAD_PACK_PATH = "/" + UPLOAD_PACK;

    private static final String RECEIVE_PACK_PATH = "/" + RECEIVE_PACK;

    /**
     * Send an error to the Git client or browser.
     * <p>
     * Server implementors may use this method to send customized error messages
     * to a Git protocol client using an HTTP 200 OK response with the error
     * embedded in the payload. If the request was not issued by a Git client,
     * an HTTP response code is returned instead.
     *
     * @param req
     *                       current request.
     * @param res
     *                       current response.
     * @param httpStatus
     *                       HTTP status code to set if the client is not a Git client.
     * @throws IOException
     *                         the response cannot be sent.
     */
    public static void sendError(HttpServletRequest req, HttpServletResponse res, int httpStatus) throws IOException {
        sendError(req, res, httpStatus, null);
    }

    /**
     * Send an error to the Git client or browser.
     * <p>
     * Server implementors may use this method to send customized error messages
     * to a Git protocol client using an HTTP 200 OK response with the error
     * embedded in the payload. If the request was not issued by a Git client,
     * an HTTP response code is returned instead.
     * <p>
     * This method may only be called before handing off the request to
     * {@link org.eclipse.jgit.transport.UploadPack#upload(java.io.InputStream, OutputStream, OutputStream)}
     * or
     * {@link org.eclipse.jgit.transport.ReceivePack#receive(java.io.InputStream, OutputStream, OutputStream)}.
     *
     * @param req
     *                       current request.
     * @param res
     *                       current response.
     * @param httpStatus
     *                       HTTP status code to set if the client is not a Git client.
     * @param textForGit
     *                       plain text message to display on the user's console. This is
     *                       shown only if the client is likely to be a Git client. If null
     *                       or the empty string a default text is chosen based on the HTTP
     *                       response code.
     * @throws IOException
     *                         the response cannot be sent.
     */
    public static void sendError(HttpServletRequest req, HttpServletResponse res, int httpStatus, String textForGit) throws IOException {
        if (StringUtils.isEmptyOrNull(textForGit)) {
            switch (httpStatus) {
                case SC_FORBIDDEN:
                    textForGit = HttpServerText.get().repositoryAccessForbidden;
                    break;
                case SC_NOT_FOUND:
                    textForGit = HttpServerText.get().repositoryNotFound;
                    break;
                case SC_INTERNAL_SERVER_ERROR:
                    textForGit = HttpServerText.get().internalServerError;
                    break;
                default:
                    textForGit = "HTTP " + httpStatus;
                    break;
            }
        }

        if (isInfoRefs(req)) {
            sendInfoRefsError(req, res, textForGit, httpStatus);
        }
        else if (isUploadPack(req)) {
            sendUploadPackError(req, res, textForGit, httpStatus);
        }
        else if (isReceivePack(req)) {
            sendReceivePackError(req, res, textForGit, httpStatus);
        }
        else {
            if (httpStatus < 400) {
                ServletUtils.consumeRequestBody(req);
            }
            res.sendError(httpStatus, textForGit);
        }
    }

    private static void sendInfoRefsError(HttpServletRequest req, HttpServletResponse res, String textForGit, int httpStatus) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        PacketLineOut pck = new PacketLineOut(buf);
        String svc = req.getParameter("service");
        pck.writeString("# service=" + svc + "\n");
        pck.end();
        pck.writeString("ERR " + textForGit);
        send(req, res, infoRefsResultType(svc), buf.toByteArray(), httpStatus);
    }

    private static void sendUploadPackError(HttpServletRequest req, HttpServletResponse res, String textForGit, int httpStatus) throws IOException {
        // Do not use sideband. Sideband is acceptable only while packfile is
        // being sent. Other places, like acknowledgement section, do not
        // support sideband. Use an error packet.
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        PacketLineOut pckOut = new PacketLineOut(buf);
        writePacket(pckOut, textForGit);
        send(req, res, UPLOAD_PACK_RESULT_TYPE, buf.toByteArray(), httpStatus);
    }

    private static void sendReceivePackError(HttpServletRequest req, HttpServletResponse res, String textForGit, int httpStatus) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        PacketLineOut pckOut = new PacketLineOut(buf);

        boolean sideband;
        ReceivePack rp = (ReceivePack) req.getAttribute(ATTRIBUTE_HANDLER);
        if (rp != null) {
            try {
                sideband = rp.isSideBand();
            }
            catch (RequestNotYetReadException e) {
                sideband = isReceivePackSideBand(req);
            }
        }
        else {
            sideband = isReceivePackSideBand(req);
        }

        if (sideband) {
            writeSideBand(buf, textForGit);
        }
        else {
            writePacket(pckOut, textForGit);
        }
        send(req, res, RECEIVE_PACK_RESULT_TYPE, buf.toByteArray(), httpStatus);
    }

    private static boolean isReceivePackSideBand(HttpServletRequest req) {
        try {
            // The client may be in a state where they have sent the sideband
            // capability and are expecting a response in the sideband, but we might
            // not have a ReceivePack, or it might not have read any of the request.
            // So, cheat and read the first line.
            String line = new PacketLineIn(req.getInputStream()).readString();
            FirstCommand parsed = FirstCommand.fromLine(line);
            return parsed.getCapabilities().containsKey(CAPABILITY_SIDE_BAND_64K);
        }
        catch (IOException e) {
            // Probably the connection is closed and a subsequent write will fail, but
            // try it just in case.
            return false;
        }
    }

    private static void writeSideBand(OutputStream out, String textForGit) throws IOException {
        try (OutputStream msg = new SideBandOutputStream(CH_ERROR, SMALL_BUF, out)) {
            msg.write(Constants.encode("error: " + textForGit));
            msg.flush();
        }
    }

    private static void writePacket(PacketLineOut pckOut, String textForGit) throws IOException {
        pckOut.writeString("ERR " + textForGit);
    }

    private static void send(HttpServletRequest req, HttpServletResponse res, String type, byte[] buf, int httpStatus) throws IOException {
        ServletUtils.consumeRequestBody(req);
        res.setStatus(httpStatus);
        res.setContentType(type);
        res.setContentLength(buf.length);
        try (OutputStream os = res.getOutputStream()) {
            os.write(buf);
        }
    }

    static String infoRefsResultType(String svc) {
        return "application/x-" + svc + "-advertisement";
    }

    /**
     * Check if the HTTP request was for the /info/refs?service= Git handler.
     *
     * @param req
     *                current request.
     * @return true if the request is for the /info/refs service.
     */
    public static boolean isInfoRefs(HttpServletRequest req) {
        return req.getRequestURI().endsWith(INFO_REFS_PATH) && VALID_SERVICES.contains(req.getParameter("service"));
    }

    /**
     * Check if the HTTP request path ends with the /git-upload-pack handler.
     *
     * @param pathOrUri
     *                      path or URI of the request.
     * @return true if the request is for the /git-upload-pack handler.
     */
    public static boolean isUploadPack(String pathOrUri) {
        return pathOrUri != null && pathOrUri.endsWith(UPLOAD_PACK_PATH);
    }

    /**
     * Check if the HTTP request was for the /git-upload-pack Git handler.
     *
     * @param req
     *                current request.
     * @return true if the request is for the /git-upload-pack handler.
     */
    public static boolean isUploadPack(HttpServletRequest req) {
        return isUploadPack(req.getRequestURI()) && UPLOAD_PACK_REQUEST_TYPE.equals(req.getContentType());
    }

    /**
     * Check if the HTTP request was for the /git-receive-pack Git handler.
     *
     * @param req
     *                current request.
     * @return true if the request is for the /git-receive-pack handler.
     */
    public static boolean isReceivePack(HttpServletRequest req) {
        String uri = req.getRequestURI();
        return uri != null && uri.endsWith(RECEIVE_PACK_PATH) && RECEIVE_PACK_REQUEST_TYPE.equals(req.getContentType());
    }

    private GitSmartHttpTools() {
    }
}

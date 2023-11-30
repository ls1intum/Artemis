/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import static javax.servlet.http.HttpServletResponse.*;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.*;
import static org.eclipse.jgit.http.server.ServletUtils.*;
import static org.eclipse.jgit.http.server.UploadPackErrorHandler.statusCodeForThrowable;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.http.server.UploadPackErrorHandler.UploadPackRunnable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.*;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;

/** Server side implementation of smart fetch over HTTP. */
class UploadPackServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    static class InfoRefs extends SmartServiceInfoRefs {

        private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

        InfoRefs(UploadPackFactory<HttpServletRequest> uploadPackFactory, List<Filter> filters) {
            super(UPLOAD_PACK, filters);
            this.uploadPackFactory = uploadPackFactory;
        }

        @Override
        protected void begin(HttpServletRequest req, Repository db) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack up = uploadPackFactory.create(req, db);
            InternalHttpServerGlue.setPeerUserAgent(up, req.getHeader(HDR_USER_AGENT));
            req.setAttribute(ATTRIBUTE_HANDLER, up);
        }

        @Override
        protected void advertise(HttpServletRequest req, PacketLineOutRefAdvertiser pck) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
            try {
                up.setBiDirectionalPipe(false);
                up.sendAdvertisedRefs(pck);
            }
            finally {
                // TODO(jonathantanmy): Move responsibility for closing the
                // RevWalk to UploadPack, either by making it AutoCloseable
                // or by making sendAdvertisedRefs clean up after itself.
                up.getRevWalk().close();
            }
        }

        @Override
        protected void respond(HttpServletRequest req, PacketLineOut pckOut, String serviceName) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
            try {
                up.setBiDirectionalPipe(false);
                up.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut), serviceName);
            }
            finally {
                // TODO(jonathantanmy): Move responsibility for closing the
                // RevWalk to UploadPack, either by making it AutoCloseable
                // or by making sendAdvertisedRefs clean up after itself.
                up.getRevWalk().close();
            }
        }
    }

    static class Factory implements Filter {

        private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

        Factory(UploadPackFactory<HttpServletRequest> uploadPackFactory) {
            this.uploadPackFactory = uploadPackFactory;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            HttpServletResponse rsp = (HttpServletResponse) response;
            UploadPack rp;
            try {
                rp = uploadPackFactory.create(req, getRepository(req));
            }
            catch (ServiceNotAuthorizedException e) {
                rsp.sendError(SC_UNAUTHORIZED, e.getMessage());
                return;
            }
            catch (ServiceNotEnabledException e) {
                sendError(req, rsp, SC_FORBIDDEN, e.getMessage());
                return;
            }

            try {
                req.setAttribute(ATTRIBUTE_HANDLER, rp);
                chain.doFilter(req, rsp);
            }
            finally {
                req.removeAttribute(ATTRIBUTE_HANDLER);
            }
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {
            // Nothing.
        }

        @Override
        public void destroy() {
            // Nothing.
        }
    }

    private final UploadPackErrorHandler handler;

    UploadPackServlet(@Nullable UploadPackErrorHandler handler) {
        this.handler = handler != null ? handler : this::defaultUploadPackHandler;
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
        if (!UPLOAD_PACK_REQUEST_TYPE.equals(req.getContentType())) {
            rsp.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        UploadPackRunnable r = () -> {
            upload(req, rsp);
        };

        handler.upload(req, rsp, r);
    }

    private void upload(HttpServletRequest req, HttpServletResponse rsp) throws IOException, ServiceMayNotContinueException {
        // to be explicitly closed by caller
        @SuppressWarnings("resource")
        SmartOutputStream out = new SmartOutputStream(req, rsp, false) {

            @Override
            public void flush() throws IOException {
                doFlush();
            }
        };
        Repository repo = null;
        try (UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER)) {
            up.setBiDirectionalPipe(false);
            rsp.setContentType(UPLOAD_PACK_RESULT_TYPE);
            repo = up.getRepository();
            up.uploadWithExceptionPropagation(getInputStream(req), out, null);
            out.close();
        }
        catch (ServiceMayNotContinueException e) {
            if (e.isOutput()) {
                consumeRequestBody(req);
                out.close();
            }
            throw e;
        }
        catch (UploadPackInternalServerErrorException e) {
            // Special case exception, error message was sent to client.
            log(repo, e.getCause());
            consumeRequestBody(req);
            out.close();
        }
    }

    private void defaultUploadPackHandler(HttpServletRequest req, HttpServletResponse rsp, UploadPackRunnable r) throws IOException {
        try {
            r.upload();
        }
        catch (ServiceMayNotContinueException e) {
            if (!e.isOutput() && !rsp.isCommitted()) {
                rsp.reset();
                sendError(req, rsp, e.getStatusCode(), e.getMessage());
            }
        }
        catch (Throwable e) {
            UploadPack up = (UploadPack) req.getAttribute(ATTRIBUTE_HANDLER);
            log(up.getRepository(), e);
            if (!rsp.isCommitted()) {
                rsp.reset();
                String msg = null;
                if (e instanceof PackProtocolException || e instanceof ServiceNotEnabledException) {
                    msg = e.getMessage();
                }
                sendError(req, rsp, statusCodeForThrowable(e), msg);
            }
        }
    }

    private void log(Repository git, Throwable e) {
        getServletContext().log(MessageFormat.format(HttpServerText.get().internalErrorDuringUploadPack, identify(git)), e);
    }
}

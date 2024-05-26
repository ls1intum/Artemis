/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.IOException;
import java.io.Serial;
import java.text.MessageFormat;
import java.util.List;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.http.server.UploadPackErrorHandler.UploadPackRunnable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.InternalHttpServerGlue;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.UploadPackInternalServerErrorException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.UPLOAD_PACK;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.UPLOAD_PACK_REQUEST_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.UPLOAD_PACK_RESULT_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.http.server.ServletUtils.consumeRequestBody;
import static org.eclipse.jgit.http.server.ServletUtils.getInputStream;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.http.server.UploadPackErrorHandler.statusCodeForThrowable;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;

/** Server side implementation of smart fetch over HTTP. */
class UploadPackServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    static class InfoRefs extends SmartServiceInfoRefs {

        private final UploadPackFactory<HttpServletRequest> uploadPackFactory;

        InfoRefs(UploadPackFactory<HttpServletRequest> uploadPackFactory, List<Filter> filters) {
            super(UPLOAD_PACK, filters);
            this.uploadPackFactory = uploadPackFactory;
        }

        @Override
        protected void begin(HttpServletRequest request, Repository repository) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack up = uploadPackFactory.create(request, repository);
            InternalHttpServerGlue.setPeerUserAgent(up, request.getHeader(HDR_USER_AGENT));
            request.setAttribute(ATTRIBUTE_HANDLER, up);
        }

        @Override
        protected void advertise(HttpServletRequest request, PacketLineOutRefAdvertiser advertiser) throws IOException {
            UploadPack up = (UploadPack) request.getAttribute(ATTRIBUTE_HANDLER);
            try {
                up.setBiDirectionalPipe(false);
                up.sendAdvertisedRefs(advertiser);
            }
            finally {
                up.getRevWalk().close();
            }
        }

        @Override
        protected void respond(HttpServletRequest request, PacketLineOut pckOut, String serviceName)
            throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
            UploadPack uploadPack = (UploadPack) request.getAttribute(ATTRIBUTE_HANDLER);
            try {
                uploadPack.setBiDirectionalPipe(false);
                uploadPack.sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut), serviceName);
            }
            finally {
                uploadPack.getRevWalk().close();
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
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            UploadPack uploadPack;
            try {
                uploadPack = uploadPackFactory.create(httpRequest, getRepository(httpRequest));
            }
            catch (ServiceNotAuthorizedException e) {
                httpResponse.sendError(SC_UNAUTHORIZED, e.getMessage());
                return;
            }
            catch (ServiceNotEnabledException e) {
                sendError(httpRequest, httpResponse, SC_FORBIDDEN, e.getMessage());
                return;
            }

            try {
                httpRequest.setAttribute(ATTRIBUTE_HANDLER, uploadPack);
                chain.doFilter(httpRequest, httpResponse);
            }
            finally {
                httpRequest.removeAttribute(ATTRIBUTE_HANDLER);
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
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!UPLOAD_PACK_REQUEST_TYPE.equals(request.getContentType())) {
            response.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        handler.upload(request, response, () -> upload(request, response));
    }

    private void upload(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // to be explicitly closed by caller
        SmartOutputStream out = new SmartOutputStream(request, response, false) {

            @Override
            public void flush() throws IOException {
                doFlush();
            }
        };
        Repository repo = null;
        try (UploadPack up = (UploadPack) request.getAttribute(ATTRIBUTE_HANDLER)) {
            up.setBiDirectionalPipe(false);
            response.setContentType(UPLOAD_PACK_RESULT_TYPE);
            repo = up.getRepository();
            up.uploadWithExceptionPropagation(getInputStream(request), out, null);
            out.close();
        }
        catch (ServiceMayNotContinueException e) {
            if (e.isOutput()) {
                consumeRequestBody(request);
                out.close();
            }
            throw e;
        }
        catch (UploadPackInternalServerErrorException e) {
            // Special case exception, error message was sent to client.
            log(repo, e.getCause());
            consumeRequestBody(request);
            out.close();
        }
    }

    private void defaultUploadPackHandler(HttpServletRequest request, HttpServletResponse response, UploadPackRunnable runnable) throws IOException {
        try {
            runnable.upload();
        }
        catch (ServiceMayNotContinueException e) {
            if (!e.isOutput() && !response.isCommitted()) {
                response.reset();
                sendError(request, response, e.getStatusCode(), e.getMessage());
            }
        }
        catch (Throwable e) {
            UploadPack up = (UploadPack) request.getAttribute(ATTRIBUTE_HANDLER);
            log(up.getRepository(), e);
            if (!response.isCommitted()) {
                response.reset();
                String msg = null;
                if (e instanceof PackProtocolException || e instanceof ServiceNotEnabledException) {
                    msg = e.getMessage();
                }
                sendError(request, response, statusCodeForThrowable(e), msg);
            }
        }
    }

    private void log(Repository git, Throwable e) {
        getServletContext().log(MessageFormat.format(HttpServerText.get().internalErrorDuringUploadPack, ServletUtils.identify(git)), e);
    }
}

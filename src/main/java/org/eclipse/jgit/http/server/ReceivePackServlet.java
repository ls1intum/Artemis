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
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.PackProtocolException;
import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.InternalHttpServerGlue;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
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
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static jakarta.servlet.http.HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.RECEIVE_PACK;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.RECEIVE_PACK_REQUEST_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.RECEIVE_PACK_RESULT_TYPE;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.http.server.ServletUtils.consumeRequestBody;
import static org.eclipse.jgit.http.server.ServletUtils.getInputStream;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;

/** Server side implementation of smart push over HTTP. */
class ReceivePackServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    static class InfoRefs extends SmartServiceInfoRefs {

        private final ReceivePackFactory<HttpServletRequest> receivePackFactory;

        InfoRefs(ReceivePackFactory<HttpServletRequest> receivePackFactory, List<Filter> filters) {
            super(RECEIVE_PACK, filters);
            this.receivePackFactory = receivePackFactory;
        }

        @Override
        protected void begin(HttpServletRequest request, Repository repository) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
            ReceivePack receivePack = receivePackFactory.create(request, repository);
            InternalHttpServerGlue.setPeerUserAgent(receivePack, request.getHeader(HDR_USER_AGENT));
            request.setAttribute(ATTRIBUTE_HANDLER, receivePack);
        }

        @Override
        protected void advertise(HttpServletRequest request, PacketLineOutRefAdvertiser advertiser) throws IOException {
            ReceivePack receivePack = (ReceivePack) request.getAttribute(ATTRIBUTE_HANDLER);
            try {
                receivePack.sendAdvertisedRefs(advertiser);
            }
            finally {
                receivePack.getRevWalk().close();
            }
        }
    }

    static class Factory implements Filter {

        private final ReceivePackFactory<HttpServletRequest> receivePackFactory;

        Factory(ReceivePackFactory<HttpServletRequest> receivePackFactory) {
            this.receivePackFactory = receivePackFactory;
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            HttpServletRequest httpServletRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            ReceivePack receivePack;
            try {
                receivePack = receivePackFactory.create(httpServletRequest, getRepository(httpServletRequest));
            }
            catch (ServiceNotAuthorizedException e) {
                httpResponse.sendError(SC_UNAUTHORIZED, e.getMessage());
                return;
            }
            catch (ServiceNotEnabledException e) {
                sendError(httpServletRequest, httpResponse, SC_FORBIDDEN, e.getMessage());
                return;
            }

            try {
                httpServletRequest.setAttribute(ATTRIBUTE_HANDLER, receivePack);
                chain.doFilter(httpServletRequest, httpResponse);
            }
            finally {
                httpServletRequest.removeAttribute(ATTRIBUTE_HANDLER);
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

    @Nullable
    private final ReceivePackErrorHandler handler;

    ReceivePackServlet(@Nullable ReceivePackErrorHandler handler) {
        this.handler = handler;
    }

    @Override
    public void doPost(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        if (!RECEIVE_PACK_REQUEST_TYPE.equals(request.getContentType())) {
            response.sendError(SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        SmartOutputStream out = new SmartOutputStream(request, response, false) {

            @Override
            public void flush() throws IOException {
                doFlush();
            }
        };

        ReceivePack receivePack = (ReceivePack) request.getAttribute(ATTRIBUTE_HANDLER);
        receivePack.setBiDirectionalPipe(false);
        response.setContentType(RECEIVE_PACK_RESULT_TYPE);

        if (handler != null) {
            handler.receive(request, response, () -> {
                receivePack.receiveWithExceptionPropagation(getInputStream(request), out, null);
                out.close();
            });
        }
        else {
            try {
                receivePack.receive(getInputStream(request), out, null);
                out.close();
            }
            catch (CorruptObjectException e) {
                // This should be already reported to the client.
                getServletContext().log(MessageFormat.format(HttpServerText.get().receivedCorruptObject, e.getMessage(), ServletUtils.identify(receivePack.getRepository())));
                consumeRequestBody(request);
                out.close();

            }
            catch (UnpackException | PackProtocolException e) {
                // This should be already reported to the client.
                log(receivePack.getRepository(), e.getCause());
                consumeRequestBody(request);
                out.close();

            }
            catch (Throwable e) {
                log(receivePack.getRepository(), e);
                if (!response.isCommitted()) {
                    response.reset();
                    sendError(request, response, SC_INTERNAL_SERVER_ERROR);
                }
            }
        }
    }

    private void log(Repository repository, Throwable e) {
        getServletContext().log(MessageFormat.format(HttpServerText.get().internalErrorDuringReceivePack, ServletUtils.identify(repository)), e);
    }
}

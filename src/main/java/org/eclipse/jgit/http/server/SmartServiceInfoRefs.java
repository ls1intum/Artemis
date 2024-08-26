/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.infoRefsResultType;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_HANDLER;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

/** Filter in front of {@link InfoRefsServlet} to catch smart service requests. */
abstract class SmartServiceInfoRefs implements Filter {

    private final String service;

    private final Filter[] filters;

    SmartServiceInfoRefs(String service, List<Filter> filters) {
        this.service = service;
        this.filters = filters.toArray(new Filter[0]);
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        // Do nothing.
    }

    @Override
    public void destroy() {
        // Do nothing.
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse res = (HttpServletResponse) response;

        if (service.equals(req.getParameter("service"))) {
            final Repository db = getRepository(req);
            try {
                begin(req, db);
            }
            catch (ServiceNotAuthorizedException e) {
                res.sendError(SC_UNAUTHORIZED, e.getMessage());
                return;
            }
            catch (ServiceNotEnabledException e) {
                sendError(req, res, SC_FORBIDDEN, e.getMessage());
                return;
            }

            try {
                if (filters.length == 0) {
                    service(req, response);
                }
                else {
                    new Chain().doFilter(request, response);
                }
            }
            finally {
                req.removeAttribute(ATTRIBUTE_HANDLER);
            }
        }
        else {
            chain.doFilter(request, response);
        }
    }

    private void service(ServletRequest request, ServletResponse response) throws IOException {
        final HttpServletRequest req = (HttpServletRequest) request;
        final HttpServletResponse res = (HttpServletResponse) response;
        final SmartOutputStream buf = new SmartOutputStream(req, res, true);
        try {
            res.setContentType(infoRefsResultType(service));

            final PacketLineOut out = new PacketLineOut(buf);
            respond(req, out, service);
            buf.close();
        }
        catch (ServiceNotAuthorizedException e) {
            res.sendError(SC_UNAUTHORIZED, e.getMessage());
        }
        catch (ServiceNotEnabledException e) {
            sendError(req, res, SC_FORBIDDEN, e.getMessage());
        }
        catch (ServiceMayNotContinueException e) {
            if (e.isOutput()) {
                buf.close();
            }
            else {
                sendError(req, res, e.getStatusCode(), e.getMessage());
            }
        }
    }

    /**
     * Begin service.
     *
     * @param req
     *                request
     * @param db
     *                repository
     * @throws IOException
     *                                           if an IO error occurred
     * @throws ServiceNotEnabledException
     *                                           if a service is not available
     * @throws ServiceNotAuthorizedException
     *                                           if service requires authentication and the current user
     *                                           didn't provide credentials
     */
    protected abstract void begin(HttpServletRequest req, Repository db) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException;

    /**
     * Advertise.
     *
     * @param req
     *                request
     * @param pck
     *                used to frame lines in PacketLineOut format
     * @throws IOException
     *                                           if an IO error occurred
     * @throws ServiceNotEnabledException
     *                                           if a service is not available
     * @throws ServiceNotAuthorizedException
     *                                           if service requires authentication and the current user
     *                                           didn't provide credentials
     */
    protected abstract void advertise(HttpServletRequest req, PacketLineOutRefAdvertiser pck) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException;

    /**
     * Writes the appropriate response to an info/refs request received by a
     * smart service. In protocol v0, this starts with "# service=serviceName"
     * followed by a flush packet, but this is not necessarily the case in other
     * protocol versions.
     * <p>
     * The default implementation writes "# service=serviceName" and a flush
     * packet, then calls {@link #advertise}. Subclasses should override this
     * method if they support protocol versions other than protocol v0.
     *
     * @param req
     *                        request
     * @param pckOut
     *                        destination of response
     * @param serviceName
     *                        service name to be written out in protocol v0; may or may not
     *                        be used in other versions
     * @throws IOException
     *                                           if an IO error occurred
     * @throws ServiceNotEnabledException
     *                                           if a service is not available
     * @throws ServiceNotAuthorizedException
     *                                           if service requires authentication and the current user
     *                                           didn't provide credentials
     */
    protected void respond(HttpServletRequest req, PacketLineOut pckOut, String serviceName) throws IOException, ServiceNotEnabledException, ServiceNotAuthorizedException {
        pckOut.writeString("# service=" + service + '\n'); //$NON-NLS-1$
        pckOut.end();
        advertise(req, new PacketLineOutRefAdvertiser(pckOut));
    }

    private class Chain implements FilterChain {

        private int filterIdx;

        @Override
        public void doFilter(ServletRequest req, ServletResponse rsp) throws IOException, ServletException {
            if (filterIdx < filters.length) {
                filters[filterIdx++].doFilter(req, rsp, this);
            }
            else {
                service(req, rsp);
            }
        }
    }
}

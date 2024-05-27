/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.IOException;
import java.text.MessageFormat;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jgit.util.StringUtils;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static org.eclipse.jgit.http.server.GitSmartHttpTools.sendError;
import static org.eclipse.jgit.http.server.ServletUtils.ATTRIBUTE_REPOSITORY;

/**
 * Open a repository named by the path info through
 * {@link org.eclipse.jgit.transport.resolver.RepositoryResolver}.
 * <p>
 * This filter assumes it is invoked by
 * {@link org.eclipse.jgit.http.server.GitServlet} and is likely to not work as
 * expected if called from any other class. This filter assumes the path info of
 * the current request is a repository name which can be used by the configured
 * {@link org.eclipse.jgit.transport.resolver.RepositoryResolver} to open a
 * {@link org.eclipse.jgit.lib.Repository} and attach it to the current request.
 * <p>
 * This filter sets request attribute
 * {@link org.eclipse.jgit.http.server.ServletUtils#ATTRIBUTE_REPOSITORY} when
 * it discovers the repository, and automatically closes and removes the
 * attribute when the request is complete.
 */
public class RepositoryFilter implements Filter {

    private final RepositoryResolver<HttpServletRequest> resolver;

    private ServletContext context;

    /**
     * Create a new filter.
     *
     * @param resolver
     *                     the resolver which will be used to translate the URL name
     *                     component to the actual
     *                     {@link org.eclipse.jgit.lib.Repository} instance for the
     *                     current web request.
     */
    public RepositoryFilter(RepositoryResolver<HttpServletRequest> resolver) {
        this.resolver = resolver;
    }

    @Override
    public void init(FilterConfig config) throws ServletException {
        context = config.getServletContext();
    }

    @Override
    public void destroy() {
        context = null;
    }

    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        if (request.getAttribute(ATTRIBUTE_REPOSITORY) != null) {
            context.log(MessageFormat.format(HttpServerText.get().internalServerErrorRequestAttributeWasAlreadySet, ATTRIBUTE_REPOSITORY, getClass().getName()));
            sendError(req, res, SC_INTERNAL_SERVER_ERROR);
            return;
        }

        String name = req.getPathInfo();

        while (!StringUtils.isEmptyOrNull(name) && name.charAt(0) == '/') {
            name = name.substring(1);
        }
        if (StringUtils.isEmptyOrNull(name)) {
            sendError(req, res, SC_NOT_FOUND);
            return;
        }

        try (Repository db = resolver.open(req, name)) {
            request.setAttribute(ATTRIBUTE_REPOSITORY, db);
            chain.doFilter(request, response);
        }
        catch (RepositoryNotFoundException e) {
            sendError(req, res, SC_NOT_FOUND);
        }
        catch (ServiceNotEnabledException e) {
            sendError(req, res, SC_FORBIDDEN, e.getMessage());
        }
        catch (ServiceNotAuthorizedException e) {
            res.sendError(SC_UNAUTHORIZED, e.getMessage());
        }
        catch (ServiceMayNotContinueException e) {
            sendError(req, res, e.getStatusCode(), e.getMessage());
        }
        finally {
            request.removeAttribute(ATTRIBUTE_REPOSITORY);
        }
    }
}

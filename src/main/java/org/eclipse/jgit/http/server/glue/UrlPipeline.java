/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Set;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Encapsulates the entire serving stack for a single URL.
 * <p>
 * Subclasses provide the implementation of {@link #match(HttpServletRequest)},
 * which is called by {@link MetaServlet} in registration order to determine the
 * pipeline that will be used to handle a request.
 * <p>
 * The very bottom of each pipeline is a single {@link HttpServlet} that will
 * handle producing the response for this pipeline's URL. {@link Filter}s may
 * also be registered and applied around the servlet's processing, to manage
 * request attributes, set standard response headers, or completely override the
 * response generation.
 */
abstract class UrlPipeline {

    /** Filters to apply around {@link #servlet}; may be empty but never null. */
    private final Filter[] filters;

    /** Instance that must generate the response; never null. */
    private final HttpServlet servlet;

    UrlPipeline(Filter[] filters, HttpServlet servlet) {
        this.filters = filters;
        this.servlet = servlet;
    }

    /**
     * Initialize all contained filters and servlets.
     *
     * @param context
     *                    the servlet container context our {@link MetaServlet} is
     *                    running within.
     * @param inited
     *                    <i>(input/output)</i> the set of filters and servlets which
     *                    have already been initialized within the container context. If
     *                    those same instances appear in this pipeline they are not
     *                    initialized a second time. Filters and servlets that are first
     *                    initialized by this pipeline will be added to this set.
     * @throws ServletException
     *                              a filter or servlet is unable to initialize.
     */
    void init(ServletContext context, Set<Object> inited) throws ServletException {
        for (Filter ref : filters) {
            initFilter(ref, context, inited);
        }
        initServlet(servlet, context, inited);
    }

    private static void initFilter(final Filter ref, final ServletContext context, final Set<Object> inited) throws ServletException {
        if (!inited.contains(ref)) {
            ref.init(new NoParameterFilterConfig(ref.getClass().getName(), context));
            inited.add(ref);
        }
    }

    private static void initServlet(final HttpServlet ref, final ServletContext context, final Set<Object> inited) throws ServletException {
        if (!inited.contains(ref)) {
            ref.init(new ServletConfig() {

                @Override
                public String getInitParameter(String name) {
                    return null;
                }

                @Override
                public Enumeration<String> getInitParameterNames() {
                    return new Enumeration<>() {

                        @Override
                        public boolean hasMoreElements() {
                            return false;
                        }

                        @Override
                        public String nextElement() {
                            throw new NoSuchElementException();
                        }
                    };
                }

                @Override
                public ServletContext getServletContext() {
                    return context;
                }

                @Override
                public String getServletName() {
                    return ref.getClass().getName();
                }
            });
            inited.add(ref);
        }
    }

    /**
     * Destroy all contained filters and servlets.
     *
     * @param destroyed
     *                      <i>(input/output)</i> the set of filters and servlets which
     *                      have already been destroyed within the container context. If
     *                      those same instances appear in this pipeline they are not
     *                      destroyed a second time. Filters and servlets that are first
     *                      destroyed by this pipeline will be added to this set.
     */
    void destroy(Set<Object> destroyed) {
        for (Filter ref : filters) {
            destroyFilter(ref, destroyed);
        }
        destroyServlet(servlet, destroyed);
    }

    private static void destroyFilter(Filter ref, Set<Object> destroyed) {
        if (!destroyed.contains(ref)) {
            ref.destroy();
            destroyed.add(ref);
        }
    }

    private static void destroyServlet(HttpServlet ref, Set<Object> destroyed) {
        if (!destroyed.contains(ref)) {
            ref.destroy();
            destroyed.add(ref);
        }
    }

    /**
     * Determine if this pipeline handles the request's URL.
     * <p>
     * This method should match on the request's {@code getPathInfo()} method,
     * as {@link MetaServlet} passes the request along as-is to each pipeline's
     * match method.
     *
     * @param req
     *                current HTTP request being considered by {@link MetaServlet}.
     * @return {@code true} if this pipeline is configured to handle the
     *         request; {@code false} otherwise.
     */
    abstract boolean match(HttpServletRequest req);

    /**
     * Execute the filters and the servlet on the request.
     * <p>
     * Invoked by {@link MetaServlet} once {@link #match(HttpServletRequest)}
     * has determined this pipeline is the correct pipeline to handle the
     * current request.
     *
     * @param req
     *                current HTTP request.
     * @param rsp
     *                current HTTP response.
     * @throws ServletException
     *                              request cannot be completed.
     * @throws IOException
     *                              IO error prevents the request from being completed.
     */
    void service(HttpServletRequest req, HttpServletResponse rsp) throws ServletException, IOException {
        if (0 < filters.length) {
            new Chain(filters, servlet).doFilter(req, rsp);
        }
        else {
            servlet.service(req, rsp);
        }
    }

    private static class Chain implements FilterChain {

        private final Filter[] filters;

        private final HttpServlet servlet;

        private int filterIdx;

        Chain(Filter[] filters, HttpServlet servlet) {
            this.filters = filters;
            this.servlet = servlet;
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse rsp) throws IOException, ServletException {
            if (filterIdx < filters.length) {
                filters[filterIdx++].doFilter(req, rsp, this);
            }
            else {
                servlet.service(req, rsp);
            }
        }
    }
}

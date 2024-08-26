/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.http.server.HttpServerText;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Generic container filter to manage routing to different pipelines.
 * <p>
 * Callers can create and configure a new processing pipeline by using one of
 * the {@link #serve(String)} or {@link #serveRegex(String)} methods to allocate
 * a binder for a particular URL pattern.
 * <p>
 * Registered filters and servlets are initialized lazily, usually during the
 * first request. Once initialized the bindings in this servlet cannot be
 * modified without destroying the servlet and thereby destroying all registered
 * filters and servlets.
 */
public class MetaFilter implements Filter {

    static final String REGEX_GROUPS = "org.eclipse.jgit.http.server.glue.MetaServlet.serveRegex";

    private ServletContext servletContext;

    private final List<ServletBinderImpl> bindings;

    private volatile UrlPipeline[] pipelines;

    /**
     * Empty filter with no bindings.
     */
    public MetaFilter() {
        this.bindings = new ArrayList<>();
    }

    /**
     * Construct a binding for a specific path.
     *
     * @param path
     *                 pattern to match.
     * @return binder for the passed path.
     */
    public ServletBinder serve(String path) {
        if (path.startsWith("*")) {
            return register(new SuffixPipeline.Binder(path.substring(1)));
        }
        throw new IllegalArgumentException(MessageFormat.format(HttpServerText.get().pathNotSupported, path));
    }

    /**
     * Construct a binding for a regular expression.
     *
     * @param expression
     *                       the regular expression to pattern match the URL against.
     * @return binder for the passed expression.
     */
    public ServletBinder serveRegex(String expression) {
        return register(new RegexPipeline.Binder(expression));
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();
    }

    @Override
    public void destroy() {
        if (pipelines != null) {
            Set<Object> destroyed = newIdentitySet();
            for (UrlPipeline p : pipelines) {
                p.destroy(destroyed);
            }
            pipelines = null;
        }
    }

    private static Set<Object> newIdentitySet() {
        final IdentityHashMap<Object, Object> m = new IdentityHashMap<>();
        return new AbstractSet<>() {

            @Override
            public boolean add(Object o) {
                return m.put(o, o) == null;
            }

            @Override
            public boolean contains(Object o) {
                return m.containsKey(o);
            }

            @Override
            public Iterator<Object> iterator() {
                return m.keySet().iterator();
            }

            @Override
            public int size() {
                return m.size();
            }
        };
    }

    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        UrlPipeline p = find(req);
        if (p != null) {
            p.service(req, res);
        }
        else {
            chain.doFilter(req, res);
        }
    }

    private UrlPipeline find(HttpServletRequest req) throws ServletException {
        for (UrlPipeline p : getPipelines()) {
            if (p.match(req)) {
                return p;
            }
        }
        return null;
    }

    private ServletBinder register(ServletBinderImpl b) {
        synchronized (bindings) {
            if (pipelines != null) {
                throw new IllegalStateException(HttpServerText.get().servletAlreadyInitialized);
            }
            bindings.add(b);
        }
        return register((ServletBinder) b);
    }

    /**
     * Configure a newly created binder.
     *
     * @param b
     *              the newly created binder.
     * @return binder for the caller, potentially after adding one or more
     *         filters into the pipeline.
     */
    protected ServletBinder register(ServletBinder b) {
        return b;
    }

    private UrlPipeline[] getPipelines() throws ServletException {
        UrlPipeline[] r = pipelines;
        if (r == null) {
            synchronized (bindings) {
                r = pipelines;
                if (r == null) {
                    r = createPipelines();
                    pipelines = r;
                }
            }
        }
        return r;
    }

    private UrlPipeline[] createPipelines() throws ServletException {
        UrlPipeline[] array = new UrlPipeline[bindings.size()];

        for (int i = 0; i < bindings.size(); i++) {
            array[i] = bindings.get(i).create();
        }

        Set<Object> inited = newIdentitySet();
        for (UrlPipeline p : array) {
            p.init(servletContext, inited);
        }
        return array;
    }
}

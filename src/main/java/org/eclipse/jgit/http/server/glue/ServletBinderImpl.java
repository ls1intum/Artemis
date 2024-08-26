/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.http.server.HttpServerText;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletResponse;

abstract class ServletBinderImpl implements ServletBinder {

    private final List<Filter> filters;

    private HttpServlet httpServlet;

    ServletBinderImpl() {
        this.filters = new ArrayList<>();
    }

    @Override
    public ServletBinder through(Filter filter) {
        if (filter == null) {
            throw new NullPointerException(HttpServerText.get().filterMustNotBeNull);
        }
        filters.add(filter);
        return this;
    }

    @Override
    public void with(HttpServlet servlet) {
        if (servlet == null) {
            throw new NullPointerException(HttpServerText.get().servletMustNotBeNull);
        }
        if (httpServlet != null) {
            throw new IllegalStateException(HttpServerText.get().servletWasAlreadyBound);
        }
        httpServlet = servlet;
    }

    /**
     * Get the servlet
     *
     * @return the configured servlet, or singleton returning 404 if none.
     */
    protected HttpServlet getServlet() {
        if (httpServlet != null) {
            return httpServlet;
        }
        return new ErrorServlet(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Get filters
     *
     * @return the configured filters; zero-length array if none.
     */
    protected Filter[] getFilters() {
        return filters.toArray(new Filter[0]);
    }

    /**
     * Create UrlPipeline
     *
     * @return the pipeline that matches and executes this chain.
     */
    abstract UrlPipeline create();
}

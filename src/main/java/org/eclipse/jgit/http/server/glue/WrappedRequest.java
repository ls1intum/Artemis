/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Overrides the path and path info.
 */
public class WrappedRequest extends HttpServletRequestWrapper {

    private final String path;

    private final String pathInfo;

    /**
     * Create a new request with different path and path info properties.
     *
     * @param originalRequest
     *                            the original HTTP request.
     * @param path
     *                            new servlet path to report to callers.
     * @param pathInfo
     *                            new path info to report to callers.
     */
    public WrappedRequest(final HttpServletRequest originalRequest, final String path, final String pathInfo) {
        super(originalRequest);
        this.path = path;
        this.pathInfo = pathInfo;
    }

    @Override
    public String getPathTranslated() {
        final String p = getPathInfo();
        return p != null ? getSession().getServletContext().getRealPath(p) : null;
    }

    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    @Override
    public String getServletPath() {
        return path;
    }
}

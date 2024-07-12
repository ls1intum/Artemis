/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Selects requests by matching the suffix of the URI.
 * <p>
 * The suffix string is literally matched against the path info of the servlet
 * request, as this class assumes it is invoked by {@link MetaServlet}. Suffix
 * strings may include path components. Examples include {@code /info/refs}, or
 * just simple extension matches like {@code .txt}.
 * <p>
 * When dispatching to the rest of the pipeline the HttpServletRequest is
 * modified so that {@code getPathInfo()} does not contain the suffix that
 * caused this pipeline to be selected.
 */
class SuffixPipeline extends UrlPipeline {

    static class Binder extends ServletBinderImpl {

        private final String suffix;

        Binder(String suffix) {
            this.suffix = suffix;
        }

        @Override
        UrlPipeline create() {
            return new SuffixPipeline(suffix, getFilters(), getServlet());
        }
    }

    private final String suffix;

    private final int suffixLen;

    SuffixPipeline(final String suffix, final Filter[] filters, final HttpServlet servlet) {
        super(filters, servlet);
        this.suffix = suffix;
        this.suffixLen = suffix.length();
    }

    @Override
    boolean match(HttpServletRequest req) {
        final String pathInfo = req.getPathInfo();
        return pathInfo != null && pathInfo.endsWith(suffix);
    }

    @Override
    void service(HttpServletRequest req, HttpServletResponse rsp) throws ServletException, IOException {
        String curInfo = req.getPathInfo();
        String newPath = req.getServletPath() + curInfo;
        String newInfo = curInfo.substring(0, curInfo.length() - suffixLen);
        super.service(new WrappedRequest(req, newPath, newInfo), rsp);
    }

    @Override
    public String toString() {
        return "Pipeline[ *" + suffix + " ]";
    }
}

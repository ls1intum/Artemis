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
import org.eclipse.jgit.http.server.HttpServerText;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Switch servlet path and path info to use another regex match group.
 * <p>
 * This filter is meant to be installed in the middle of a pipeline created by
 * {@link org.eclipse.jgit.http.server.glue.MetaServlet#serveRegex(String)}. The
 * passed request's servlet path is updated to be all text up to the start of
 * the designated capture group, and the path info is changed to the contents of
 * the capture group.
 */
public class RegexGroupFilter implements Filter {

    private final int groupIdx;

    /**
     * Constructor for RegexGroupFilter
     *
     * @param groupIdx
     *                     capture group number, 1 through the number of groups.
     */
    public RegexGroupFilter(int groupIdx) {
        if (groupIdx < 1) {
            throw new IllegalArgumentException(MessageFormat.format(HttpServerText.get().invalidIndex, groupIdx));
        }
        this.groupIdx = groupIdx - 1;
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
    public void doFilter(final ServletRequest request, final ServletResponse rsp, final FilterChain chain) throws IOException, ServletException {
        final WrappedRequest[] g = groupsFor(request);
        if (groupIdx < g.length) {
            chain.doFilter(g[groupIdx], rsp);
        }
        else {
            throw new ServletException(MessageFormat.format(HttpServerText.get().invalidRegexGroup, groupIdx + 1));
        }
    }

    private static WrappedRequest[] groupsFor(ServletRequest r) {
        return (WrappedRequest[]) r.getAttribute(MetaFilter.REGEX_GROUPS);
    }
}

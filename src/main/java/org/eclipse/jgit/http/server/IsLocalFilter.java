/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.IOException;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.lib.Repository;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import static jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

/**
 * Requires the target {@link Repository} to be available via local filesystem.
 * <p>
 * The target {@link Repository} must be using a {@link ObjectDirectory}, so the
 * downstream servlet can directly access its contents on disk.
 */
class IsLocalFilter implements Filter {

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
        if (isLocal(getRepository(request))) {
            chain.doFilter(request, response);
        }
        else {
            ((HttpServletResponse) response).sendError(SC_FORBIDDEN);
        }
    }

    private static boolean isLocal(Repository db) {
        return db.getObjectDatabase() instanceof ObjectDirectory;
    }
}

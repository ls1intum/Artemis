/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;

/**
 * Binds a servlet to a URL.
 */
public interface ServletBinder {

    /**
     * Set the filter to trigger while processing the path.
     *
     * @param filter
     *                   the filter to trigger while processing the path.
     * @return {@code this}.
     */
    ServletBinder through(Filter filter);

    /**
     * Set the servlet to execute on this path
     *
     * @param servlet
     *                    the servlet to execute on this path.
     */
    void with(HttpServlet servlet);
}

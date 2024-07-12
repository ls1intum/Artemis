/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import java.util.Enumeration;
import java.util.NoSuchElementException;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;

final class NoParameterFilterConfig implements FilterConfig {

    private final String filterName;

    private final ServletContext context;

    NoParameterFilterConfig(String filterName, ServletContext context) {
        this.filterName = filterName;
        this.context = context;
    }

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
    public String getFilterName() {
        return filterName;
    }
}

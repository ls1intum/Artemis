/*
 * Copyright (C) 2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.glue;

import java.io.IOException;
import java.io.Serial;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Send a fixed status code to the client.
 */
public class ErrorServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int status;

    /**
     * Sends a specific status code.
     *
     * @param status
     *                   the HTTP status code to always send.
     */
    public ErrorServlet(int status) {
        this.status = status;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse rsp) throws IOException {
        rsp.sendError(status);
    }
}

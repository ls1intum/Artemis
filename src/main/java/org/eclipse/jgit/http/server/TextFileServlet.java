/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

import java.io.*;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jgit.util.HttpSupport;
import org.eclipse.jgit.util.IO;

/** Sends a small text meta file from the repository. */
class TextFileServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String fileName;

    TextFileServlet(String name) {
        this.fileName = name;
    }

    @Override
    public void doGet(final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        try {
            rsp.setContentType(HttpSupport.TEXT_PLAIN);
            ServletUtils.send(read(req), req, rsp);
        }
        catch (FileNotFoundException noFile) {
            rsp.sendError(SC_NOT_FOUND);
        }
    }

    private byte[] read(HttpServletRequest req) throws IOException {
        final File gitdir = ServletUtils.getRepository(req).getDirectory();
        if (gitdir == null)
            throw new FileNotFoundException(fileName);
        return IO.readFully(new File(gitdir, fileName));
    }
}

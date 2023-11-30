/*
 * Copyright (c) 2019, Google LLC and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.http.server;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;

/**
 * Handle git-receive-pack errors.
 *
 * <p>
 * This is an entry point for customizing an error handler for git-receive-pack.
 * Right before calling {@link ReceivePack#receiveWithExceptionPropagation},
 * JGit will call this handler if specified through {@link GitFilter}. The
 * implementation of this handler is responsible for calling
 * {@link ReceivePackRunnable} and handling exceptions for clients.
 *
 * <p>
 * If a custom handler is not specified, JGit will use the default error
 * handler.
 *
 * @since 5.7
 */
public interface ReceivePackErrorHandler {

    /**
     * Receive pack
     *
     * @param req
     *                The HTTP request
     * @param rsp
     *                The HTTP response
     * @param r
     *                A continuation that handles a git-receive-pack request.
     * @throws IOException
     *                         if an IO error occurred
     */
    void receive(HttpServletRequest req, HttpServletResponse rsp, ReceivePackRunnable r) throws IOException;

    /** Process a git-receive-pack request. */
    public interface ReceivePackRunnable {

        /**
         * See {@link ReceivePack#receiveWithExceptionPropagation}.
         *
         * @throws ServiceMayNotContinueException
         *                                            if transport service cannot continue
         * @throws IOException
         *                                            if an IO error occurred
         */
        void receive() throws ServiceMayNotContinueException, IOException;
    }

}

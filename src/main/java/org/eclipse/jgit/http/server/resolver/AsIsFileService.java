/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.resolver;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;

/**
 * Controls access to bare files in a repository.
 * <p>
 * Older HTTP clients which do not speak the smart HTTP variant of the Git
 * protocol fetch from a repository by directly getting its objects and pack
 * files. This class, along with the {@code http.getanyfile} per-repository
 * configuration setting, can be used by
 * {@link org.eclipse.jgit.http.server.GitServlet} to control whether or not
 * these older clients are permitted to read these direct files.
 */
public class AsIsFileService {

    /** Always throws {@link ServiceNotEnabledException}. */
    public static final AsIsFileService DISABLED = new AsIsFileService() {

        @Override
        public void access(HttpServletRequest req, Repository db) throws ServiceNotEnabledException {
            throw new ServiceNotEnabledException();
        }
    };

    private static class ServiceConfig {

        final boolean enabled;

        ServiceConfig(Config cfg) {
            enabled = cfg.getBoolean("http", "getanyfile", true);
        }
    }

    /**
     * Determine if {@code http.getanyfile} is enabled in the configuration.
     *
     * @param db
     *               the repository to check.
     * @return {@code false} if {@code http.getanyfile} was explicitly set to
     *         {@code false} in the repository's configuration file; otherwise
     *         {@code true}.
     */
    protected static boolean isEnabled(Repository db) {
        return db.getConfig().get(ServiceConfig::new).enabled;
    }

    /**
     * Determine if access to any bare file of the repository is allowed.
     * <p>
     * This method silently succeeds if the request is allowed, or fails by
     * throwing a checked exception if access should be denied.
     * <p>
     * The default implementation of this method checks {@code http.getanyfile},
     * throwing
     * {@link ServiceNotEnabledException} if
     * it was explicitly set to {@code false}, and otherwise succeeding
     * silently.
     *
     * @param req
     *                current HTTP request, in case information from the request may
     *                help determine the access request.
     * @param db
     *                the repository the request would obtain a bare file from.
     * @throws ServiceNotEnabledException
     *                                           bare file access is not allowed on the target repository, by
     *                                           any user, for any reason.
     * @throws ServiceNotAuthorizedException
     *                                           bare file access is not allowed for this HTTP request and
     *                                           repository, such as due to a permission error.
     */
    public void access(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        if (!isEnabled(db))
            throw new ServiceNotEnabledException();
    }
}

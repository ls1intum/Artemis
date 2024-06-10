/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.resolver;

import java.util.Arrays;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Create and configure {@link org.eclipse.jgit.transport.UploadPack} service
 * instance.
 * <p>
 * Reading by upload-pack is permitted unless {@code http.uploadpack} is
 * explicitly set to false.
 */
public class DefaultUploadPackFactory implements UploadPackFactory<HttpServletRequest> {

    private static class ServiceConfig {

        final boolean enabled;

        ServiceConfig(Config cfg) {
            enabled = cfg.getBoolean("http", "uploadpack", true);
        }
    }

    @Override
    public UploadPack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        if (db.getConfig().get(ServiceConfig::new).enabled) {
            UploadPack up = new UploadPack(db);
            String header = req.getHeader("Git-Protocol"); //$NON-NLS-1$
            if (header != null) {
                String[] params = header.split(":"); //$NON-NLS-1$
                up.setExtraParameters(Arrays.asList(params));
            }
            return up;
        }
        throw new ServiceNotEnabledException();
    }
}

/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server.resolver;

import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Create and configure {@link org.eclipse.jgit.transport.ReceivePack} service
 * instance.
 * <p>
 * Writing by receive-pack is permitted if any of the following is true:
 * <ul>
 * <li>The container has authenticated the user and set
 * {@link jakarta.servlet.http.HttpServletRequest#getRemoteUser()} to the
 * authenticated name.
 * <li>The repository configuration file has {@code http.receivepack} explicitly
 * set to true.
 * </ul>
 * and explicitly rejected otherwise.
 */
public class DefaultReceivePackFactory implements ReceivePackFactory<HttpServletRequest> {

    private static class ServiceConfig {

        final boolean set;

        final boolean enabled;

        ServiceConfig(Config cfg) {
            set = cfg.getString("http", null, "receivepack") != null;
            enabled = cfg.getBoolean("http", "receivepack", false);
        }
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db) throws ServiceNotEnabledException, ServiceNotAuthorizedException {
        final ServiceConfig cfg = db.getConfig().get(ServiceConfig::new);
        String user = req.getRemoteUser();

        if (cfg.set) {
            if (cfg.enabled) {
                if (StringUtils.isEmptyOrNull(user)) {
                    user = "anonymous";
                }
                return createFor(req, db, user);
            }
            throw new ServiceNotEnabledException();
        }

        if (!StringUtils.isEmptyOrNull(user)) {
            return createFor(req, db, user);
        }
        throw new ServiceNotAuthorizedException();
    }

    private static ReceivePack createFor(final HttpServletRequest req, final Repository db, final String user) {
        final ReceivePack rp = new ReceivePack(db);
        rp.setRefLogIdent(toPersonIdent(req, user));
        return rp;
    }

    private static PersonIdent toPersonIdent(HttpServletRequest req, String user) {
        return new PersonIdent(user, user + "@" + req.getRemoteHost());
    }
}

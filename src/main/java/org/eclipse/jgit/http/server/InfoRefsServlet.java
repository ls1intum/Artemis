/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Serial;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefAdvertiser;
import org.eclipse.jgit.util.HttpSupport;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.http.server.ServletUtils.getRepository;

/** Send a complete list of current refs, including peeled values for tags. */
class InfoRefsServlet extends HttpServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public void doGet(final HttpServletRequest req, final HttpServletResponse rsp) throws IOException {
        // Assume a dumb client and send back the dumb client
        // version of the info/refs file.
        rsp.setContentType(HttpSupport.TEXT_PLAIN);
        rsp.setCharacterEncoding(UTF_8.name());

        final Repository repository = getRepository(req);
        try (OutputStreamWriter out = new OutputStreamWriter(new SmartOutputStream(req, rsp, true), UTF_8)) {
            final RefAdvertiser advertiser = new RefAdvertiser() {

                @Override
                protected void writeOne(CharSequence line) throws IOException {
                    // Whoever decided that info/refs should use a different
                    // delimiter than the native git:// protocol shouldn't
                    // be allowed to design this sort of stuff. :-(
                    out.append(line.toString().replace(' ', '\t'));
                }

                @Override
                protected void end() {
                    // No end marker required for info/refs format.
                }
            };
            advertiser.init(repository);
            advertiser.setDerefTags(true);
            advertiser.send(repository.getRefDatabase().getRefsByPrefix(Constants.R_REFS));
        }
    }
}

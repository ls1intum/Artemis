/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.Serial;
import java.util.Enumeration;
import org.eclipse.jgit.http.server.glue.MetaServlet;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Handles Git repository access over HTTP.
 * <p>
 * Applications embedding this servlet should map a directory path within the
 * application to this servlet, for example:
 *
 * <pre>
 *   &lt;servlet&gt;
 *     &lt;servlet-name&gt;GitServlet&lt;/servlet-name&gt;
 *     &lt;servlet-class&gt;org.eclipse.jgit.http.server.GitServlet&lt;/servlet-class&gt;
 *     &lt;init-param&gt;
 *       &lt;param-name&gt;base-path&lt;/param-name&gt;
 *       &lt;param-value&gt;/var/srv/git&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 *     &lt;init-param&gt;
 *       &lt;param-name&gt;export-all&lt;/param-name&gt;
 *       &lt;param-value&gt;0&lt;/param-value&gt;
 *     &lt;/init-param&gt;
 * &lt;/servlet&gt;
 *   &lt;servlet-mapping&gt;
 *     &lt;servlet-name&gt;GitServlet&lt;/servlet-name&gt;
 *     &lt;url-pattern&gt;/git/*&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 *
 * <p>
 * Applications may wish to add additional repository action URLs to this
 * servlet by taking advantage of its extension from
 * {@link org.eclipse.jgit.http.server.glue.MetaServlet}. Callers may register
 * their own URL suffix translations through {@link #serve(String)}, or their
 * regex translations through {@link #serveRegex(String)}. Each translation
 * should contain a complete filter pipeline which ends with the HttpServlet
 * that should handle the requested action.
 */
public class GitServlet extends MetaServlet {

    @Serial
    private static final long serialVersionUID = 1L;

    private final GitFilter gitFilter;

    /**
     * New servlet that will load its base directory from {@code web.xml}.
     * <p>
     * The required parameter {@code base-path} must be configured to point to
     * the local filesystem directory where all served Git repositories reside.
     */
    public GitServlet() {
        super(new GitFilter());
        gitFilter = (GitFilter) getDelegateFilter();
    }

    /**
     * New servlet configured with a specific resolver.
     *
     * @param resolver
     *                     the resolver to use when matching URL to Git repository. If
     *                     null the {@code base-path} parameter will be looked for in the
     *                     parameter table during init, which usually comes from the
     *                     {@code web.xml} file of the web application.
     */
    public void setRepositoryResolver(RepositoryResolver<HttpServletRequest> resolver) {
        gitFilter.setRepositoryResolver(resolver);
    }

    /**
     * Set AsIsFileService
     *
     * @param f
     *              the filter to validate direct access to repository files
     *              through a dumb client. If {@code null} then dumb client
     *              support is completely disabled.
     */
    public void setAsIsFileService(AsIsFileService f) {
        gitFilter.setAsIsFileService(f);
    }

    /**
     * Set upload-pack factory
     *
     * @param f
     *              the factory to construct and configure an
     *              {@link org.eclipse.jgit.transport.UploadPack} session when a
     *              fetch or clone is requested by a client.
     */
    public void setUploadPackFactory(UploadPackFactory<HttpServletRequest> f) {
        gitFilter.setUploadPackFactory(f);
    }

    /**
     * Set a custom error handler for git-upload-pack.
     *
     * @param h
     *              A custom error handler for git-upload-pack.
     * @since 5.9.1
     */
    public void setUploadPackErrorHandler(UploadPackErrorHandler h) {
        gitFilter.setUploadPackErrorHandler(h);
    }

    /**
     * Add upload-pack filter
     *
     * @param filter
     *                   filter to apply before any of the UploadPack operations. The
     *                   UploadPack instance is available in the request attribute
     *                   {@link org.eclipse.jgit.http.server.ServletUtils#ATTRIBUTE_HANDLER}.
     */
    public void addUploadPackFilter(Filter filter) {
        gitFilter.addUploadPackFilter(filter);
    }

    /**
     * Set receive-pack factory
     *
     * @param f
     *              the factory to construct and configure a
     *              {@link org.eclipse.jgit.transport.ReceivePack} session when a
     *              push is requested by a client.
     */
    public void setReceivePackFactory(ReceivePackFactory<HttpServletRequest> f) {
        gitFilter.setReceivePackFactory(f);
    }

    /**
     * Set a custom error handler for git-receive-pack.
     *
     * @param h
     *              A custom error handler for git-receive-pack.
     * @since 5.9.1
     */
    public void setReceivePackErrorHandler(ReceivePackErrorHandler h) {
        gitFilter.setReceivePackErrorHandler(h);
    }

    /**
     * Add receive-pack filter
     *
     * @param filter
     *                   filter to apply before any of the ReceivePack operations. The
     *                   ReceivePack instance is available in the request attribute
     *                   {@link org.eclipse.jgit.http.server.ServletUtils#ATTRIBUTE_HANDLER}.
     */
    public void addReceivePackFilter(Filter filter) {
        gitFilter.addReceivePackFilter(filter);
    }

    @Override public void init(ServletConfig config) throws ServletException {
        gitFilter.init(new FilterConfig() {

            @Override public String getFilterName() {
                return gitFilter.getClass().getName();
            }

            @Override public String getInitParameter(String name) {
                return config.getInitParameter(name);
            }

            @Override public Enumeration<String> getInitParameterNames() {
                return config.getInitParameterNames();
            }

            @Override public ServletContext getServletContext() {
                return config.getServletContext();
            }
        });
    }
}

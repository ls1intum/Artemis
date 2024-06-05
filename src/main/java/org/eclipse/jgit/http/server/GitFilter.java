/*
 * Copyright (C) 2009-2010, Google Inc. and others
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import java.io.File;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.jgit.http.server.glue.ErrorServlet;
import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.eclipse.jgit.http.server.glue.RegexGroupFilter;
import org.eclipse.jgit.http.server.glue.ServletBinder;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.http.server.resolver.DefaultUploadPackFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.eclipse.jgit.util.StringUtils;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Handles Git repository access over HTTP.
 * <p>
 * Applications embedding this filter should map a directory path within the
 * application to this filter. For a servlet version, see
 * {@link org.eclipse.jgit.http.server.GitServlet}.
 * <p>
 * Applications may wish to add additional repository action URLs to this
 * servlet by taking advantage of its extension from
 * {@link org.eclipse.jgit.http.server.glue.MetaFilter}. Callers may register
 * their own URL suffix translations through {@link #serve(String)}, or their
 * regex translations through {@link #serveRegex(String)}. Each translation
 * should contain a complete filter pipeline which ends with the HttpServlet
 * that should handle the requested action.
 */
public class GitFilter extends MetaFilter {

    private volatile boolean initialized;

    private RepositoryResolver<HttpServletRequest> resolver;

    private AsIsFileService asIs = new AsIsFileService();

    private UploadPackFactory<HttpServletRequest> uploadPackFactory = new DefaultUploadPackFactory();

    private UploadPackErrorHandler uploadPackErrorHandler;

    private ReceivePackFactory<HttpServletRequest> receivePackFactory = new DefaultReceivePackFactory();

    private ReceivePackErrorHandler receivePackErrorHandler;

    private final List<Filter> uploadPackFilters = new LinkedList<>();

    private final List<Filter> receivePackFilters = new LinkedList<>();

    /**
     * New servlet that will load its base directory from {@code web.xml}.
     * <p>
     * The required parameter {@code base-path} must be configured to point to
     * the local filesystem directory where all served Git repositories reside.
     */
    public GitFilter() {
        // Initialized above by field declarations.
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
        assertNotInitialized();
        this.resolver = resolver;
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
        assertNotInitialized();
        this.asIs = f != null ? f : AsIsFileService.DISABLED;
    }

    /**
     * Set upload-pack factory
     *
     * @param f
     *              the factory to construct and configure an
     *              {@link org.eclipse.jgit.transport.UploadPack} session when a
     *              fetch or clone is requested by a client.
     */
    @SuppressWarnings("unchecked") public void setUploadPackFactory(UploadPackFactory<HttpServletRequest> f) {
        assertNotInitialized();
        this.uploadPackFactory = f != null ? f : (UploadPackFactory<HttpServletRequest>) UploadPackFactory.DISABLED;
    }

    /**
     * Set a custom error handler for git-upload-pack.
     *
     * @param h
     *              A custom error handler for git-upload-pack.
     * @since 5.6
     */
    public void setUploadPackErrorHandler(UploadPackErrorHandler h) {
        assertNotInitialized();
        this.uploadPackErrorHandler = h;
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
        assertNotInitialized();
        uploadPackFilters.add(filter);
    }

    /**
     * Set the receive-pack factory
     *
     * @param f
     *              the factory to construct and configure a
     *              {@link org.eclipse.jgit.transport.ReceivePack} session when a
     *              push is requested by a client.
     */
    @SuppressWarnings("unchecked") public void setReceivePackFactory(ReceivePackFactory<HttpServletRequest> f) {
        assertNotInitialized();
        this.receivePackFactory = f != null ? f : (ReceivePackFactory<HttpServletRequest>) ReceivePackFactory.DISABLED;
    }

    /**
     * Set a custom error handler for git-receive-pack.
     *
     * @param h
     *              A custom error handler for git-receive-pack.
     * @since 5.7
     */
    public void setReceivePackErrorHandler(ReceivePackErrorHandler h) {
        assertNotInitialized();
        this.receivePackErrorHandler = h;
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
        assertNotInitialized();
        receivePackFilters.add(filter);
    }

    private void assertNotInitialized() {
        if (initialized) {
            throw new IllegalStateException(HttpServerText.get().alreadyInitializedByContainer);
        }
    }

    @Override public void init(FilterConfig filterConfig) throws ServletException {
        super.init(filterConfig);

        if (resolver == null) {
            File root = getFile(filterConfig, "base-path");
            boolean exportAll = getBoolean(filterConfig, "export-all");
            setRepositoryResolver(new FileResolver<>(root, exportAll));
        }

        initialized = true;

        if (uploadPackFactory != UploadPackFactory.DISABLED) {
            ServletBinder b = serve("*/" + GitSmartHttpTools.UPLOAD_PACK);
            b = b.through(new UploadPackServlet.Factory(uploadPackFactory));
            for (Filter f : uploadPackFilters) {
                b = b.through(f);
            }
            b.with(new UploadPackServlet(uploadPackErrorHandler));
        }

        if (receivePackFactory != ReceivePackFactory.DISABLED) {
            ServletBinder b = serve("*/" + GitSmartHttpTools.RECEIVE_PACK);
            b = b.through(new ReceivePackServlet.Factory(receivePackFactory));
            for (Filter f : receivePackFilters) {
                b = b.through(f);
            }
            b.with(new ReceivePackServlet(receivePackErrorHandler));
        }

        ServletBinder refs = serve("*/" + Constants.INFO_REFS);
        if (uploadPackFactory != UploadPackFactory.DISABLED) {
            refs = refs.through(new UploadPackServlet.InfoRefs(uploadPackFactory, uploadPackFilters));
        }
        if (receivePackFactory != ReceivePackFactory.DISABLED) {
            refs = refs.through(new ReceivePackServlet.InfoRefs(receivePackFactory, receivePackFilters));
        }
        if (asIs != AsIsFileService.DISABLED) {
            refs = refs.through(new IsLocalFilter());
            refs = refs.through(new AsIsFileFilter(asIs));
            refs.with(new InfoRefsServlet());
        }
        else {
            refs.with(new ErrorServlet(HttpServletResponse.SC_NOT_ACCEPTABLE));
        }

        if (asIs != AsIsFileService.DISABLED) {
            final IsLocalFilter mustBeLocal = new IsLocalFilter();
            final AsIsFileFilter enabled = new AsIsFileFilter(asIs);

            serve("*/" + Constants.HEAD)//
                .through(mustBeLocal)//
                .through(enabled)//
                .with(new TextFileServlet(Constants.HEAD));

            final String info_alternates = Constants.OBJECTS + "/" + Constants.INFO_ALTERNATES;
            serve("*/" + info_alternates)//
                .through(mustBeLocal)//
                .through(enabled)//
                .with(new TextFileServlet(info_alternates));

            final String http_alternates = Constants.OBJECTS + "/" + Constants.INFO_HTTP_ALTERNATES;
            serve("*/" + http_alternates)//
                .through(mustBeLocal)//
                .through(enabled)//
                .with(new TextFileServlet(http_alternates));

            serve("*/objects/info/packs")//
                .through(mustBeLocal)//
                .through(enabled)//
                .with(new InfoPacksServlet());

            serveRegex("^/(.*)/objects/([0-9a-f]{2}/[0-9a-f]{38})$")//
                .through(mustBeLocal)//
                .through(enabled)//
                .through(new RegexGroupFilter(2))//
                .with(new ObjectFileServlet.Loose());

            serveRegex("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.pack)$")//
                .through(mustBeLocal)//
                .through(enabled)//
                .through(new RegexGroupFilter(2))//
                .with(new ObjectFileServlet.Pack());

            serveRegex("^/(.*)/objects/(pack/pack-[0-9a-f]{40}\\.idx)$")//
                .through(mustBeLocal)//
                .through(enabled)//
                .through(new RegexGroupFilter(2))//
                .with(new ObjectFileServlet.PackIdx());
        }
    }

    private static File getFile(FilterConfig cfg, String param) throws ServletException {
        String initParameter = cfg.getInitParameter(param);
        if (StringUtils.isEmptyOrNull(initParameter)) {
            throw new ServletException(MessageFormat.format(HttpServerText.get().parameterNotSet, param));
        }

        File path = new File(initParameter);
        if (!path.exists()) {
            throw new ServletException(MessageFormat.format(HttpServerText.get().pathForParamNotFound, path, param));
        }
        return path;
    }

    private static boolean getBoolean(FilterConfig cfg, String param) throws ServletException {
        String initParameter = cfg.getInitParameter(param);
        if (initParameter == null) {
            return false;
        }
        try {
            return StringUtils.toBoolean(initParameter);
        }
        catch (IllegalArgumentException err) {
            throw new ServletException(MessageFormat.format(HttpServerText.get().invalidBoolean, param, initParameter), err);
        }
    }

    @Override
    protected ServletBinder register(ServletBinder binder) {
        if (resolver == null) {
            throw new IllegalStateException(HttpServerText.get().noResolverAvailable);
        }
        binder = binder.through(new NoCacheFilter());
        binder = binder.through(new RepositoryFilter(resolver));
        return binder;
    }
}

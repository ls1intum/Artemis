/*
 * Copyright 2016-2022 the original author or authors from the JHipster project. This file is part of the JHipster project, see https://www.jhipster.tech/ for more information.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.tum.cit.aet.artemis.core.security.filter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import de.tum.cit.aet.artemis.core.config.ArtemisProperties;

/**
 * Sets long-lived HTTP cache headers on the client bundle.
 * <p>
 * Registered in {@code WebConfigurer} for {@code *.js}, {@code *.css} and {@code /i18n/*}. Every one of those is
 * versioned in its URL — Angular emits content-hashed filenames, and the translation loader appends the build's
 * {@code I18N_HASH} as a query parameter — so a new build is a new URL and a cached response can never go stale. That
 * is what makes caching them for weeks safe, and why they do not share the conservative lifetime that
 * {@code StaticResourcesConfiguration} applies to the mutable files under {@code /public}.
 */
public class CachingHttpHeadersFilter implements Filter {

    /**
     * How long a versioned asset may be cached when nothing is configured.
     * <p>
     * A response here is immutable for the life of its URL, so this is a trade-off about how long a returning browser
     * may skip asking, not about how long a mistake would persist.
     */
    public static final int DEFAULT_DAYS_TO_LIVE = 30;

    /**
     * Seconds, because that is the unit {@code Cache-Control: max-age} is defined in.
     * <p>
     * This used to be computed with {@code TimeUnit.DAYS.toMillis}, inherited from JHipster, whose constant was
     * already named "seconds" while holding milliseconds. The configured seven days was therefore served as
     * {@code max-age=604800000} — a little over nineteen years — and disagreed with the {@code Expires} header beside
     * it, which was correct. {@code Cache-Control} wins where the two differ, so browsers held the bundle
     * indefinitely.
     */
    private long cacheTimeToLiveSeconds = TimeUnit.DAYS.toSeconds(DEFAULT_DAYS_TO_LIVE);

    private final ArtemisProperties jHipsterProperties;

    /**
     * <p>
     * Constructor for CachingHttpHeadersFilter.
     * </p>
     *
     * @param jHipsterProperties a {@link ArtemisProperties} object.
     */
    public CachingHttpHeadersFilter(ArtemisProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    /** {@inheritDoc} */
    @Override
    public void init(FilterConfig filterConfig) {
        cacheTimeToLiveSeconds = TimeUnit.DAYS.toSeconds(jHipsterProperties.getHttp().getCache().getVersionedAssetsTimeToLiveInDays());
    }

    /** {@inheritDoc} */
    @Override
    public void destroy() {
        // Nothing to destroy
    }

    /** {@inheritDoc} */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        httpResponse.setHeader("Cache-Control", "max-age=" + cacheTimeToLiveSeconds + ", public");
        httpResponse.setHeader("Pragma", "cache");

        // Setting Expires header, for proxy caching. It takes milliseconds since the epoch, so the lifetime has to be
        // converted back out of seconds; mixing those two units up is what produced the nineteen-year max-age above.
        httpResponse.setDateHeader("Expires", System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(cacheTimeToLiveSeconds));

        chain.doFilter(request, response);
    }
}

/*
 * Copyright 2016-2022 the original author or authors from the JHipster project. This file is part of the JHipster project, see https://www.jhipster.tech/ for more information.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package de.tum.cit.aet.artemis.core.web.filter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

import tech.jhipster.config.JHipsterProperties;

/**
 * This filter is used in production, to put HTTP cache headers with a long (4 years) expiration time.
 */
public class CachingHttpHeadersFilter implements Filter {

    /** Constant <code>DEFAULT_DAYS_TO_LIVE=1461</code> */
    public static final int DEFAULT_DAYS_TO_LIVE = 1461; // 4 years

    /** Constant <code>DEFAULT_SECONDS_TO_LIVE=TimeUnit.DAYS.toMillis(DEFAULT_DAYS_TO_LIVE)</code> */
    public static final long DEFAULT_SECONDS_TO_LIVE = TimeUnit.DAYS.toMillis(DEFAULT_DAYS_TO_LIVE);

    private long cacheTimeToLive = DEFAULT_SECONDS_TO_LIVE;

    private final JHipsterProperties jHipsterProperties;

    /**
     * <p>
     * Constructor for CachingHttpHeadersFilter.
     * </p>
     *
     * @param jHipsterProperties a {@link JHipsterProperties} object.
     */
    public CachingHttpHeadersFilter(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    /** {@inheritDoc} */
    @Override
    public void init(FilterConfig filterConfig) {
        cacheTimeToLive = TimeUnit.DAYS.toMillis(jHipsterProperties.getHttp().getCache().getTimeToLiveInDays());
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

        httpResponse.setHeader("Cache-Control", "max-age=" + cacheTimeToLive + ", public");
        httpResponse.setHeader("Pragma", "cache");

        // Setting Expires header, for proxy caching
        httpResponse.setDateHeader("Expires", cacheTimeToLive + System.currentTimeMillis());

        chain.doFilter(request, response);
    }
}

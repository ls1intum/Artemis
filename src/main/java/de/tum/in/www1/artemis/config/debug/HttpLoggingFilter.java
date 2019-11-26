package de.tum.in.www1.artemis.config.debug;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HttpLoggingFilter.class);

    @SuppressWarnings("unused")
    private volatile long activeRequests;

    private volatile long allRequests;

    private static final AtomicLongFieldUpdater<HttpLoggingFilter> activeRequestsUpdater = AtomicLongFieldUpdater.newUpdater(HttpLoggingFilter.class, "activeRequests");

    private static final AtomicLongFieldUpdater<HttpLoggingFilter> allRequestsUpdater = AtomicLongFieldUpdater.newUpdater(HttpLoggingFilter.class, "allRequests");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        long activeRequests = activeRequestsUpdater.incrementAndGet(this);
        allRequestsUpdater.incrementAndGet(this);
        logger.debug("Request started. Active requests: {}", activeRequests);
        try {
            chain.doFilter(request, response);
        }
        finally {
            if (!request.isAsyncStarted()) {
                activeRequests = activeRequestsUpdater.decrementAndGet(this);
                logger.debug("Request finished. Active requests: {}", activeRequests);
            }
        }
    }

    public long getActiveRequests() {
        return activeRequests;
    }

    public long getAllRequests() {
        return allRequests;
    }
}

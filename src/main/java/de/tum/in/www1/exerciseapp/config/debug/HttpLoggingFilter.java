package de.tum.in.www1.exerciseapp.config.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(HttpLoggingFilter.class);

    @SuppressWarnings("unused")
    private volatile int activeRequests;

    private static final AtomicIntegerFieldUpdater<HttpLoggingFilter> activeRequestsUpdater = AtomicIntegerFieldUpdater.newUpdater(HttpLoggingFilter.class, "activeRequests");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        int activeRequests = activeRequestsUpdater.incrementAndGet(this);
        logger.info("Request started. Active requests: {}", activeRequests);
        try {
            chain.doFilter(request, response);
        }
        finally {
            if (!request.isAsyncStarted()) {
                activeRequests = activeRequestsUpdater.decrementAndGet(this);
                logger.info("Request finished. Active requests: {}", activeRequests);
            }
        }
    }
}

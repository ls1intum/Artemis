package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Tags responses served under a legacy URL prefix that maps to a canonical new prefix on the same
 * controller with the standard RFC deprecation signal headers:
 * <ul>
 * <li>{@code Deprecation: <date>} (RFC 9745) — when the prefix was deprecated.</li>
 * <li>{@code Sunset: <date>} (RFC 8594) — when the prefix will stop responding.</li>
 * <li>{@code Link: <successor>; rel="successor-version"} (RFC 8288) — replacement URL under the new prefix.</li>
 * </ul>
 * <p>
 * <b>Convention:</b> when a controller carries a multi-value {@link RequestMapping} like
 * {@code @RequestMapping({ "api/account/", "api/core/" })}, the first entry is treated as the canonical
 * (successor) prefix and every subsequent entry is treated as a legacy alias kept for backwards
 * compatibility. Requests that arrived under one of the legacy aliases get tagged with the headers
 * above; requests under the canonical prefix are not.
 * <p>
 * This interceptor is generic and module-agnostic. It reads the {@link RequestMapping} annotation off
 * the resolved {@link HandlerMethod}, so adding a new legacy alias requires nothing more than adding
 * an extra entry to the controller's {@code @RequestMapping} value array.
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class LegacyApiPathDeprecationInterceptor implements HandlerInterceptor {

    // RFC 9745 §2 — Deprecation field-value is an HTTP Structured Field date: "@" followed by the
    // Unix epoch second. 2026-05-28 00:00:00 UTC = 1779926400 — keep in sync with the
    // @Deprecated(since=...) annotations on the *LegacyRestPaths constants across modules.
    public static final String DEPRECATION_DATE = "@1779926400";

    // RFC 8594 — Sunset still uses IMF-fixdate (HTTP-date) format. Bump (and bump the @Deprecated
    // since) when the migration deadline slips.
    public static final String SUNSET_DATE = "Wed, 30 Sep 2026 00:00:00 GMT";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequestMapping mapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
        if (mapping == null) {
            return true;
        }
        String[] paths = mapping.value();
        if (paths.length < 2) {
            // Single-path controller — nothing to deprecate.
            return true;
        }
        String requestPath = request.getRequestURI();
        if (requestPath == null) {
            return true;
        }
        // HttpServletRequest#getRequestURI() includes the servlet context path. Strip it so the
        // legacy-prefix match works under a non-root deployment (server.servlet.context-path).
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        String successorPrefix = withLeadingSlash(paths[0]);
        // Walk every legacy alias declared after the canonical one.
        for (int i = 1; i < paths.length; i++) {
            String legacyPrefix = withLeadingSlash(paths[i]);
            if (!requestPath.startsWith(legacyPrefix)) {
                continue;
            }
            // Deprecation / Sunset are single-valued — setHeader is correct (no other writer sets them).
            response.setHeader("Deprecation", DEPRECATION_DATE);
            response.setHeader("Sunset", SUNSET_DATE);
            // Link is multi-valued (RFC 8288): use addHeader so a pagination Link emitted by the
            // controller (PaginationUtil) is preserved alongside the successor-version Link.
            String successor = successorPrefix + requestPath.substring(legacyPrefix.length());
            response.addHeader("Link", "<" + successor + ">; rel=\"successor-version\"");
            return true;
        }
        return true;
    }

    private static String withLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}

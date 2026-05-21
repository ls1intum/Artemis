package org.apache.commons.lang.exception;

/**
 * Shim class that bridges the old {@code commons-lang} 2.x API to {@code commons-lang3}.
 * <p>
 * <strong>DO NOT DELETE THIS CLASS.</strong> It has no compile-time references in Artemis source code,
 * but it is required at <em>runtime</em> by Netflix Eureka Client ({@code com.netflix.eureka:eureka-client}).
 * Eureka's {@code DiscoveryClient} and {@code RedirectingEurekaHttpClient} use
 * {@code org.apache.commons.lang.exception.ExceptionUtils} internally for error handling
 * during heartbeats, registration, and registry fetching.
 * <p>
 * <strong>Why not just add {@code commons-lang:commons-lang:2.6} as a dependency?</strong>
 * The old {@code commons-lang} 2.x library is globally excluded in {@code build.gradle}
 * because it contains known security vulnerabilities. Instead, this single-class shim
 * delegates to the actively maintained {@code commons-lang3} ({@code org.apache.commons.lang3})
 * which is API-compatible for the methods Eureka uses.
 * <p>
 * <strong>When to remove:</strong> This class can be removed when Netflix Eureka Client is
 * upgraded to a version that uses {@code commons-lang3} directly, or when Artemis switches
 * to a different service discovery mechanism (e.g., Kubernetes-native discovery).
 *
 * @see <a href="https://github.com/Netflix/eureka">Netflix Eureka Client</a>
 * @see org.apache.commons.lang3.exception.ExceptionUtils
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("deprecation")
public class ExceptionUtils extends org.apache.commons.lang3.exception.ExceptionUtils {
}

package de.tum.cit.aet.artemis.core.security.annotations;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.HttpRequestUtils.getIpStringFromRequest;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.config.InternalAccessConfiguration;

@Profile(PROFILE_CORE)
@Component
@Lazy
@Aspect
@EnableConfigurationProperties(InternalAccessConfiguration.class)
public class InternalAspect {

    private static final Logger log = LoggerFactory.getLogger(InternalAspect.class);

    private final List<IpAddressMatcher> allowedMatchers;

    public InternalAspect(InternalAccessConfiguration props) {
        if (props.getAllowedCidrs() == null) {
            this.allowedMatchers = List.of();
            return;
        }

        this.allowedMatchers = props.getAllowedCidrs().stream().map(IpAddressMatcher::new).toList();
    }

    /**
     * Check access to methods or classes annotated with @Internal.
     * Access is allowed only from IP addresses defined in {@link de.tum.cit.aet.artemis.core.config.InternalAccessConfiguration}.
     */
    @Before("@within(Internal) || @annotation(Internal)")
    public void checkAccess() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }

        HttpServletRequest request = attrs.getRequest();
        String clientIp = getIpStringFromRequest(request);

        boolean allowed = allowedMatchers.stream().anyMatch(m -> m.matches(clientIp));
        if (!allowed) {
            log.error("Access to internal endpoint from forbidden IP address: {}", clientIp);
            throw new AccessDeniedException("Forbidden: internal endpoint");
        }
    }
}

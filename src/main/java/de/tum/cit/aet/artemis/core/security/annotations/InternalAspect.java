package de.tum.cit.aet.artemis.core.security.annotations;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
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

    private final List<IpAddressMatcher> allowedMatchers;

    public InternalAspect(InternalAccessConfiguration props) {
        if (props.getAllowedCidrs() == null) {
            this.allowedMatchers = List.of();
            return;
        }

        this.allowedMatchers = props.getAllowedCidrs().stream().map(IpAddressMatcher::new).toList();
    }

    @Before("@within(Internal) || @annotation(Internal)")
    public void checkAccess() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            return;

        HttpServletRequest request = attrs.getRequest();
        String clientIp = resolve(request);

        boolean allowed = allowedMatchers.isEmpty() || allowedMatchers.stream().anyMatch(m -> m.matches(clientIp));
        if (!allowed) {
            throw new SecurityException("Forbidden: internal endpoint");
        }
    }

    private static String resolve(HttpServletRequest request) {
        String value = request.getHeader("X-Forwarded-For");
        if (value != null && !value.isBlank()) {
            String first = Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).findFirst().orElse(null);
            if (first != null)
                return stripPort(first);
        }
        return stripPort(request.getRemoteAddr());
    }

    private static String stripPort(String hostPort) {
        int lastColon = hostPort.lastIndexOf(':');
        int lastBracket = hostPort.lastIndexOf(']');
        if (lastColon > -1 && lastColon > lastBracket) {
            return hostPort.substring(0, lastColon);
        }
        return hostPort;
    }
}

package de.tum.cit.aet.artemis.core.security.annotations;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.config.InternalAccessConfiguration;

@Aspect
@Component
@EnableConfigurationProperties(InternalAccessConfiguration.class)
public class InternalAspect {

    private final List<IpAddressMatcher> allowedMatchers;

    public InternalAspect(InternalAccessConfiguration props) {
        this.allowedMatchers = props.getAllowedCidrs().stream().map(IpAddressMatcher::new).toList();
    }

    @Before("@within(Internal) || @annotation(Internal)")
    public void checkAccess(JoinPoint joinPoint) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null)
            return; // not an HTTP request

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
            // X-Forwarded-For may contain a chain: client, proxy1, proxy2
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

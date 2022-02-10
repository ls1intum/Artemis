package de.tum.in.www1.artemis.usermanagement.client;

import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.security.SecurityUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;

@Component
public class UserClientInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER = "Bearer";

    @Override
    public void apply(RequestTemplate template) {
        SecurityUtils.getCurrentSecurityContextJWT().ifPresent(s -> template.header(AUTHORIZATION_HEADER, String.format("%s %s", BEARER, s)));
    }
}

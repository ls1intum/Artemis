package de.tum.cit.aet.artemis.core.security.allowedTools;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Method;
import java.util.Arrays;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import de.tum.cit.aet.artemis.core.security.jwt.JWTFilter;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

@Profile(PROFILE_CORE)
@Component
public class ToolsInterceptor implements HandlerInterceptor {

    private final TokenProvider tokenProvider;

    public ToolsInterceptor(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String jwtToken;
        try {
            jwtToken = JWTFilter.extractValidJwt(request, tokenProvider);
        }
        catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return false;
        }

        if (handler instanceof HandlerMethod && jwtToken != null) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();

            // Check if the method or its class has the @AllowedTools annotation
            AllowedTools allowedToolsAnnotation = method.getAnnotation(AllowedTools.class);
            if (allowedToolsAnnotation == null) {
                allowedToolsAnnotation = method.getDeclaringClass().getAnnotation(AllowedTools.class);
            }

            // Extract the "tools" claim from the JWT token
            String toolsClaim = tokenProvider.getClaim(jwtToken, "tools", String.class);

            // If no @AllowedTools annotation is present and the token is a tool token, reject the request
            if (allowedToolsAnnotation == null && toolsClaim != null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied due to 'tools' claim.");
                return false;
            }

            // If @AllowedTools is present, check if the toolsClaim is among the allowed values
            if (allowedToolsAnnotation != null && toolsClaim != null) {
                ToolTokenType[] allowedTools = allowedToolsAnnotation.value();
                // no match between allowed tools and tools claim
                var toolsClaimList = toolsClaim.split(",");
                if (Arrays.stream(allowedTools).noneMatch(tool -> Arrays.asList(toolsClaimList).contains(tool.toString()))) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied due to 'tools' claim.");
                    return false;
                }
            }
        }
        return true;
    }
}

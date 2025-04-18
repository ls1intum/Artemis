package de.tum.cit.aet.artemis.core.security.webauthn;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import java.io.IOException;
import java.util.function.Supplier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.jackson.WebauthnJackson2Module;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.HttpSessionPublicKeyCredentialCreationOptionsRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A {@link jakarta.servlet.Filter} that renders the
 * {@link PublicKeyCredentialCreationOptions} for <a href=
 * "https://w3c.github.io/webappsec-credential-management/#dom-credentialscontainer-create">creating</a>
 * a new credential.
 *
 * @see org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter
 */
public class ArtemisPublicKeyCredentialCreationOptionsFilter extends OncePerRequestFilter {

    private PublicKeyCredentialCreationOptionsRepository repository = new HttpSessionPublicKeyCredentialCreationOptionsRepository();

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();

    private final RequestMatcher matcher = antMatcher(HttpMethod.POST, "/webauthn/register/options");

    private final AuthorizationManager<HttpServletRequest> authorization = AuthenticatedAuthorizationManager.authenticated();

    private final WebAuthnRelyingPartyOperations rpOperations;

    private final HttpMessageConverter<Object> converter = new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json().modules(new WebauthnJackson2Module()).build());

    /**
     * Creates a new instance.
     *
     * @param rpOperations the {@link WebAuthnRelyingPartyOperations} to use. Cannot be
     *                         null.
     */
    public ArtemisPublicKeyCredentialCreationOptionsFilter(WebAuthnRelyingPartyOperations rpOperations) {
        Assert.notNull(rpOperations, "rpOperations cannot be null");
        this.rpOperations = rpOperations;
    }

    /**
     * Sets the {@link PublicKeyCredentialCreationOptionsRepository} to use.
     *
     * @param publicKeyCredentialCreationOptionsRepository the
     *                                                         {@link PublicKeyCredentialCreationOptionsRepository} to use. Cannot be null.
     */
    public void setCreationOptionsRepository(PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository) {
        Assert.notNull(publicKeyCredentialCreationOptionsRepository, "publicKeyCredentialCreationOptionsRepository cannot be null");
        this.repository = publicKeyCredentialCreationOptionsRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        if (!this.matcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        Supplier<SecurityContext> context = this.securityContextHolderStrategy.getDeferredContext();
        Supplier<Authentication> authentication = () -> context.get().getAuthentication();
        AuthorizationDecision decision = this.authorization.check(authentication, request);
        if (!decision.isGranted()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        PublicKeyCredentialCreationOptions options = this.rpOperations
                .createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(authentication.get()));
        this.repository.save(request, response, options);
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        this.converter.write(options, MediaType.APPLICATION_JSON, new ServletServerHttpResponse(response));
    }

}

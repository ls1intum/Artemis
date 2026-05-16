package de.tum.cit.aet.artemis.core.security.saml2;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;

import de.tum.cit.aet.artemis.core.repository.saml2.HazelcastSaml2RedirectUriRepository;

/**
 * Resolves the RelayState for a SAML2 AuthnRequest.
 * <p>
 * If the incoming request carries a valid {@code redirect_uri} parameter (external client flow),
 * the URI is validated, stored in the distributed nonce store, and an opaque UUID nonce is
 * returned as RelayState. Otherwise {@code null} is returned and the standard web flow applies.
 */
public class Saml2RelayStateResolver implements Converter<HttpServletRequest, String> {

    private static final Logger log = LoggerFactory.getLogger(Saml2RelayStateResolver.class);

    private final SAML2RedirectUriValidator validator;

    private final HazelcastSaml2RedirectUriRepository redirectUriRepository;

    public Saml2RelayStateResolver(SAML2RedirectUriValidator validator, HazelcastSaml2RedirectUriRepository redirectUriRepository) {
        this.validator = validator;
        this.redirectUriRepository = redirectUriRepository;
    }

    /**
     * Resolves the RelayState value for the given request.
     *
     * @param request the HTTP request initiating the SAML2 flow
     * @return an opaque nonce if a valid {@code redirect_uri} was provided, otherwise {@code null}
     */
    @Override
    public String convert(HttpServletRequest request) {
        String redirectUri = request.getParameter("redirect_uri");
        if (redirectUri == null || redirectUri.isBlank()) {
            return null;
        }

        if (!validator.isFeatureEnabled()) {
            log.warn("SAML2 redirect_uri provided but feature is disabled (empty allowlist). redirect_uri will be ignored and user will be redirected to '/'.");
            return null;
        }

        Optional<String> rejection = validator.validate(redirectUri);
        if (rejection.isPresent()) {
            log.warn("SAML2 redirect_uri rejected: {}", rejection.get());
            return null;
        }

        String nonce = UUID.randomUUID().toString();
        redirectUriRepository.save(nonce, redirectUri);
        log.debug("SAML2 redirect_uri stored with nonce: {}", nonce);
        return nonce;
    }
}

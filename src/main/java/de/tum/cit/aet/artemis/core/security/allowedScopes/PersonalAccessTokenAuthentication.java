package de.tum.cit.aet.artemis.core.security.allowedScopes;

import java.io.Serial;
import java.util.Collection;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * The authentication of a request made with a personal access token.
 * <p>
 * It looks like the authentication of the token's owner, with one difference that matters: it also carries the token's
 * scopes, which {@link AllowedScopesInterceptor} checks against the endpoint. Requests authenticated any other way never
 * have this type, so the interceptor does not apply to them.
 */
public class PersonalAccessTokenAuthentication extends UsernamePasswordAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long tokenId;

    private final Set<AgentScope> scopes;

    public PersonalAccessTokenAuthentication(String login, Collection<? extends GrantedAuthority> authorities, long tokenId, Set<AgentScope> scopes) {
        super(new User(login, "", authorities), null, authorities);
        this.tokenId = tokenId;
        this.scopes = Set.copyOf(scopes);
    }

    public long getTokenId() {
        return tokenId;
    }

    public Set<AgentScope> getScopes() {
        return scopes;
    }
}

package de.tum.cit.aet.artemis.core.security.allowedScopes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a REST endpoint in to requests authenticated with a personal access token.
 * <p>
 * Access is denied by default: a token reaches an endpoint only if the endpoint carries this annotation and the token
 * holds every listed scope. The annotation does not replace the role check; every opted-in endpoint also needs an
 * {@code @EnforceAtLeast...} annotation, which {@code AllowedScopesArchitectureTest} verifies.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedScopes {

    /**
     * The scopes a token must hold, all of them, to call the annotated endpoint.
     *
     * @return the required scopes
     */
    AgentScope[] value();
}

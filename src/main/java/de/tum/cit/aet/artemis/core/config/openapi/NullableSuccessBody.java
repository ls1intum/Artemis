package de.tum.cit.aet.artemis.core.config.openapi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Documents that the {@code 200} response of a REST handler may have an empty body, for example when {@code ResponseEntity.ok(null)} is returned because nothing
 * matches. The OpenAPI specification then describes its JSON schema as {@code oneOf: [<schema>, {type: 'null'}]}, which the generated client types as
 * {@code T | null}.
 * <p>
 * Only the documentation changes: the handler keeps its status and its JSON. Use it only where an empty {@code 200} is part of the contract; a handler that
 * always returns a body must not carry it.
 *
 * @see NullableSuccessBodyCustomizer
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NullableSuccessBody {
}

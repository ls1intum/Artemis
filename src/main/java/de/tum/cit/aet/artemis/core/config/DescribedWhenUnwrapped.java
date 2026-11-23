package de.tum.cit.aet.artemis.core.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record that exists only to be embedded with {@link com.fasterxml.jackson.annotation.JsonUnwrapped}, so that no endpoint refers
 * to it by itself. {@link UnwrappedSchemaOpenApiCustomizer} adds its schema to the specification, which lets the records that embed it be
 * described as that schema plus their own fields. A record that an endpoint refers to needs no mark.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DescribedWhenUnwrapped {
}

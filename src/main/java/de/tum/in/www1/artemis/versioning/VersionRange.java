package de.tum.in.www1.artemis.versioning;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Depicts a range of versions and can contain one or two values.
 * The first value is the lower bound and the second, optional, value is the upper bound.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionRange {

    /**
     * Specifies the range by version numbers
     * @return array of version numbers
     */
    int[] value();
}

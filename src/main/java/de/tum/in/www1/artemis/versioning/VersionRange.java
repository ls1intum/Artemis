package de.tum.in.www1.artemis.versioning;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Depicts a range of versions. Start and end value are part of the range itself. If only the start value is given, the range is considered a lower limit matching all versions
 * greater or equal to the start value.
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionRange {

    int UNDEFINED = -1;

    @AliasFor("start")
    int value();

    /**
     * The start of the version range or a lower limit.
     *
     * @return the starting version number.
     */
    @AliasFor("value")
    int start();

    /**
     * The end of the version range inclusive. If set to -1, the range is considered open-ended. Technically, 0 would already be enough, but -1 is more expressive. A null value is
     * not allowed.
     *
     * @return the ending version number.
     */
    int end() default -1;
}

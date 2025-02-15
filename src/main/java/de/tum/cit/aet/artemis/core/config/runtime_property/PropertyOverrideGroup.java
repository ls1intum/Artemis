package de.tum.cit.aet.artemis.core.config.runtime_property;

import java.util.List;

/**
 * Logical group of custom override properties (for the sake of readability).
 * There is no clear guideline on how to structure this, but you have to make sure that you don't override the same
 * property multiple times.
 */
public interface PropertyOverrideGroup {

    List<PropertyOverride> getProperties();
}

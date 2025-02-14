package de.tum.cit.aet.artemis.core.config.builder;

import org.springframework.core.env.ConfigurableEnvironment;

public interface CustomProperty {

    /**
     * @return the property name to override
     */
    String getPropertyName();

    /**
     * @param environment the current environment (to check if the property should be enabled)
     * @return the property value to override
     */
    Object getPropertyValue(ConfigurableEnvironment environment);

    /**
     * Returns whether the property should be overridden.
     * The property override will only be applied if this method returns true.
     *
     * @param environment the current environment
     * @return true if the property should be overridden, false otherwise
     */
    default boolean enabled(ConfigurableEnvironment environment) {
        return true;
    }

    /**
     * Returns the property source name for the property to override.
     * This name is arbitrary and should be unique.
     * When such a property is read from an application.yml, this property would include the file name.
     * While you can override this value, there is currently no good reason to do so.
     *
     * @return the property source name
     */
    default String getPropertySourceName() {
        return getPropertyName().replace(".", "_") + "_override";
    }
}

package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.BuildConfigAdvancedSettings;

/**
 * Projection that reads only the {@code advanced_settings} column of a build configuration. It wraps the settings because Spring Data would otherwise treat the
 * settings record itself as a projection class and rewrite the query into a constructor expression.
 *
 * @param advancedSettings the settings, null if the build configuration has none that deviate from the default
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildConfigAdvancedSettingsProjectionDTO(@Nullable BuildConfigAdvancedSettings advancedSettings) {
}

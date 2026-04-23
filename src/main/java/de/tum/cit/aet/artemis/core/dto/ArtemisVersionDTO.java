package de.tum.cit.aet.artemis.core.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO containing Artemis version information including update status.
 * Implements Serializable for distributed caching compatibility.
 *
 * @param currentVersion  the currently running version of Artemis
 * @param latestVersion   the latest available version from GitHub releases
 * @param updateAvailable true if a newer version is available
 * @param releaseUrl      URL to the latest release on GitHub
 * @param releaseNotes    brief description of the latest release
 * @param lastChecked     timestamp when the version was last checked (ISO 8601)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ArtemisVersionDTO(String currentVersion, String latestVersion, boolean updateAvailable, String releaseUrl, String releaseNotes, String lastChecked)
        implements Serializable {
}

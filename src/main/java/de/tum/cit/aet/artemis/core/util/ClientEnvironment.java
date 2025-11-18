package de.tum.cit.aet.artemis.core.util;

import jakarta.annotation.Nullable;

import de.tum.cit.aet.artemis.core.domain.Language;

/**
 * Represents the client environment, including browser, operating system, and application type.
 * <p>
 * This class is used to provide information about the environment from which a request originates.
 * Usually either browser and operating system or the Artemis app are available.
 */
public record ClientEnvironment(@Nullable Browser browser, @Nullable OperatingSystem operatingSystem, @Nullable ArtemisApp artemisApp) {

    /**
     * Provides a language-specific description for cases where the environment cannot be determined.
     *
     * @param language the language in which the display name should be generated
     * @return a {@link String} representing the unknown environment display name
     */
    public static String getUnknownEnvironmentDisplayName(Language language) {
        String unknownDevice = "an unknown device";

        if (language == Language.GERMAN) {
            unknownDevice = "einem unbekannten Gerät";
        }

        return unknownDevice;
    }

    /**
     * The EnvironmentInfo includes the browser, operating system, or application type, depending on the available information.
     * If none of these are available, it returns a language-specific description for an unknown environment {@link ClientEnvironment#getUnknownEnvironmentDisplayName}.
     *
     * @param language in which the environment information should be generated
     * @return a {@link String} representing the localized client environment description
     */
    public String getEnvironmentInfo(Language language) {
        String browserToOsConnection = " on ";
        String appPrefix = "the Artemis ";

        if (language == Language.GERMAN) {
            browserToOsConnection = " unter ";
            appPrefix = "der Artemis ";
        }

        if (browser == null && operatingSystem == null && artemisApp == null) {
            return getUnknownEnvironmentDisplayName(language);
        }

        if (artemisApp != null) {
            return appPrefix + artemisApp.getDisplayName();
        }

        if (browser != null && operatingSystem != null) {
            return browser.getDisplayName() + browserToOsConnection + operatingSystem.getDisplayName();
        }

        if (browser != null) {
            return browser.getDisplayName();
        }

        return operatingSystem.getDisplayName();
    }

}

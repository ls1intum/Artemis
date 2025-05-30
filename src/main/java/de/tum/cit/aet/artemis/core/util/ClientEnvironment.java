package de.tum.cit.aet.artemis.core.util;

import de.tum.cit.aet.artemis.core.domain.Language;

public record ClientEnvironment(Browser browser, OperatingSystem operatingSystem, ArtemisApp artemisApp) {

    public static String getUnknownEnvironmentDisplayName(Language language) {
        String unknownDevice = "an unknown device";

        if (language == Language.GERMAN) {
            unknownDevice = "einem unbekannten Gerät";
        }

        return unknownDevice;
    }

    public String getEnvironmentInfo(Language language) {
        String browserToOsConnection = " on ";
        String appPrefix = "the Artemis ";

        if (language == Language.GERMAN) {
            browserToOsConnection = "unter";
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

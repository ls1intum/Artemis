package de.tum.cit.aet.artemis.core.util;

/**
 * This enum is used to distinguish between the <a href="https://github.com/ls1intum/artemis-ios">iOS</a> and
 * <a href="https://github.com/ls1intum/artemis-android">Android</a> versions of the Artemis app.
 */
public enum ArtemisApp {

    IOS("iOS App"), ANDROID("Android App");

    private final String displayName;

    ArtemisApp(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

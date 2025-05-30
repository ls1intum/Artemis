package de.tum.cit.aet.artemis.core.util;

public enum ArtemisApp {

    IOS("iOS App"), ANROID("Android App");

    private final String displayName;

    ArtemisApp(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

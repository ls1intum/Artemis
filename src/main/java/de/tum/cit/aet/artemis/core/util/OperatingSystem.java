package de.tum.cit.aet.artemis.core.util;

public enum OperatingSystem {

    WINDOWS("Windows"), MACOS("macOS"), LINUX("Linux"), ANDROID("Android"), IOS("iOS");

    private final String displayName;

    OperatingSystem(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

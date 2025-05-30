package de.tum.cit.aet.artemis.core.util;

/**
 * This enum is used to identify and categorize the <a href="https://gs.statcounter.com/browser-market-share">most commonly used browsers</a> based on their display names.
 */
public enum Browser {

    MICROSOFT_EDGE("Microsoft Edge"), OPERA("Opera"), SAMSUNG_INTERNET("Samsung Internet"), GOOGLE_CHROME("Google Chrome"), MOZILLA_FIREFOX("Mozilla Firefox"),
    APPLE_SAFARI("Apple Safari"), BRAVE("Brave"), VIVALDI("Vivaldi"), DUCKDUCKGO("DuckDuckGo");

    private final String displayName;

    Browser(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Retrieves the user friendly display name of the browser.
     *
     * @return the display name as a {@link String}
     */
    public String getDisplayName() {
        return displayName;
    }
}

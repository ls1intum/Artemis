package de.tum.in.www1.exerciseapp.config;

import java.io.File;

/**
 * Application constants.
 */
public final class Constants {

    //Regex for acceptable logins
    public static final String LOGIN_REGEX = "^[_'.@A-Za-z0-9-]*$";

    public static final String SYSTEM_ACCOUNT = "system";
    public static final String ANONYMOUS_USER = "anonymoususer";
    public static final String DEFAULT_LANGUAGE = "en";

    public static final int QUIZ_AUTOMATIC_SUBMISSION_DELAY_IN_SECONDS = 3;
    public static final int QUIZ_GRACE_PERIOD_IN_SECONDS = 2;

    public static final String TEMP_FILEPATH = "uploads" + File.separator + "images" + File.separator + "temp" + File.separator;
    public static final String DRAG_AND_DROP_BACKGROUND_FILEPATH = "uploads" + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "backgrounds" + File.separator;
    public static final String DRAG_ITEM_FILEPATH = "uploads" + File.separator + "images" + File.separator + "drag-and-drop" + File.separator + "drag-items" + File.separator;
    public static final String FILEPATH_ID_PLACHEOLDER = "PLACEHOLDER_FOR_ID";

    private Constants() {
    }
}

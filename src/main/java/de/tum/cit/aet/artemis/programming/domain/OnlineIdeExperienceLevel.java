package de.tum.cit.aet.artemis.programming.domain;

import java.util.Locale;

/**
 * The experience level a student starts at when opening a programming exercise in the online IDE (EduIDE).
 * <p>
 * It is written into {@code .vscode/eduide.json} of every student repository created from the template. EduIDE only applies it to students who have not
 * chosen a level themselves, so it is a starting point rather than a restriction.
 */
public enum OnlineIdeExperienceLevel {

    /**
     * The reduced IDE surface for students who are new to programming.
     */
    BEGINNER,

    /**
     * The intermediate IDE surface.
     */
    ADVANCED,

    /**
     * The full IDE surface.
     */
    EXPERT;

    /**
     * @return the value EduIDE expects in the {@code level} key of {@code .vscode/eduide.json}
     */
    public String toEduIdeValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}

package de.tum.in.www1.artemis.tutorialgroups;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;

/**
 * Factory for creating TutorialGroups and related objects.
 */
public class TutorialGroupFactory {

    /**
     * Generates an example tutorial group
     *
     * @param title                 of tutorial group
     * @param additionalInformation of tutorial group
     * @param capacity              of tutorial group
     * @param isOnline              of tutorial group
     * @param language              of tutorial group
     * @param campus                of tutorial group
     * @return example tutorial gorup
     */
    public static TutorialGroup generateTutorialGroup(String title, String additionalInformation, Integer capacity, Boolean isOnline, String language, String campus) {
        TutorialGroup tutorialGroup = new TutorialGroup();
        tutorialGroup.setTitle(title);
        tutorialGroup.setAdditionalInformation(additionalInformation);
        tutorialGroup.setCapacity(capacity);
        tutorialGroup.setIsOnline(isOnline);
        tutorialGroup.setLanguage(language);
        tutorialGroup.setCampus(campus);
        return tutorialGroup;
    }

    /**
     * Generates an example tutorial group configuration
     *
     * @param start of configuration
     * @param end   of configuration
     * @return example configuration
     */
    public static TutorialGroupsConfiguration generateTutorialGroupsConfiguration(LocalDate start, LocalDate end) {
        TutorialGroupsConfiguration tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
        tutorialGroupsConfiguration.setTutorialPeriodStartInclusive(start.format(DateTimeFormatter.ISO_LOCAL_DATE));
        tutorialGroupsConfiguration.setTutorialPeriodEndInclusive(end.format(DateTimeFormatter.ISO_LOCAL_DATE));
        tutorialGroupsConfiguration.setUsePublicTutorialGroupChannels(true);
        tutorialGroupsConfiguration.setUseTutorialGroupChannels(true);
        return tutorialGroupsConfiguration;
    }
}

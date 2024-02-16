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
     * Generates a TutorialGroup with the given arguments.
     *
     * @param title                 The title of the TutorialGroup
     * @param additionalInformation The additional information of the TutorialGroup
     * @param capacity              The capacity of the TutorialGroup
     * @param isOnline              True, if the TutorialGroup is online
     * @param language              The language of the TutorialGroup
     * @param campus                The campus of the TutorialGroup
     * @return The generated TutorialGroup
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
     * Generates a TutorialGroupsConfiguration with the given arguments. The TutorialGroupsConfiguration's (public) tutorialGroupsChannel attribute is set to true.
     *
     * @param start The start date of the TutorialGroupsConfiguration
     * @param end   The end date of the TutorialGroupsConfiguration
     * @return The generated TutorialGroupsConfiguration
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

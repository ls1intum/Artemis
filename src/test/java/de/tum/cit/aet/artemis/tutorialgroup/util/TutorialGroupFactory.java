package de.tum.cit.aet.artemis.tutorialgroup.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;

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
    public static TutorialGroup generateTutorialGroup(Course course, String title, String additionalInformation, Integer capacity, Boolean isOnline, String campus, String language,
            User teachingAssistant, TutorialGroupSchedule schedule) {
        TutorialGroup tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle(title);
        tutorialGroup.setAdditionalInformation(additionalInformation);
        tutorialGroup.setCapacity(capacity);
        tutorialGroup.setIsOnline(isOnline);
        tutorialGroup.setLanguage(language);
        tutorialGroup.setCampus(campus);
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        if (schedule != null) {
            tutorialGroup.setTutorialGroupSchedule(schedule);
        }
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

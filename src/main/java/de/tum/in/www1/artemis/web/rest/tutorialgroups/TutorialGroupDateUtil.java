package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import java.time.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;

public class TutorialGroupDateUtil {

    /*
     * Note: We can NOT use LocalTime.MIN as the precision is not supported by the database, and thus it will rounded
     */
    public static final LocalTime START_OF_DAY = LocalTime.of(0, 0, 0);

    /*
     * Note: We can NOT use LocalTime.MAX as the precision is not supported by the database, and thus it will be rounded
     */
    public static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

    /**
     * Convert a LocalDate and LocalTime to a ZonedDateTime by interpreting them in the time zone of the TutorialGroupsConfiguration
     *
     * @param localDate                   date to convert
     * @param localTime                   time to convert
     * @param tutorialGroupsConfiguration configuration to use for the time zone
     * @return the ZonedDateTime object interpreted in the time zone of the TutorialGroupsConfiguration
     */
    public static ZonedDateTime interpretInTimeZoneOfConfiguration(LocalDate localDate, LocalTime localTime, TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        return ZonedDateTime.of(localDate, localTime, ZoneId.of(tutorialGroupsConfiguration.getTimeZone()));
    }

    /**
     * Convert a LocalDateTime to a ZonedDateTime by interpreting them in the time zone of the TutorialGroupsConfiguration
     *
     * @param localDateTime               date and time to convert
     * @param tutorialGroupsConfiguration configuration to use for the time zone
     * @return the ZonedDateTime object interpreted in the time zone of the TutorialGroupsConfiguration
     */
    public static ZonedDateTime interpretInTimeZoneOfConfiguration(LocalDateTime localDateTime, TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        return interpretInTimeZoneOfConfiguration(localDateTime.toLocalDate(), localDateTime.toLocalTime(), tutorialGroupsConfiguration);
    }

}

package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Guards the time zone mapping of the "Run Tests after Due Date" date.
 * <p>
 * The date is the only temporal column of the programming exercise that lives on the secondary table
 * {@code programming_exercise_details}. Hibernate writes a secondary table with a MERGE whose source casts every
 * parameter, and for a {@code ZonedDateTime} without an explicit storage strategy that cast is
 * {@code timestamp with time zone}. The column is {@code timestamp without time zone}, so the database converted the
 * value with the session time zone and the date moved by the server's UTC offset on every save: an instructor on a
 * UTC+2 server who set the date to 08:00 got 10:00 stored, and the exam assessment dashboard then reported that the
 * tests were still pending. {@link TimeZoneStorageType#NORMALIZE} binds a plain timestamp in the JDBC time zone
 * instead, which is what the main table's date columns already receive.
 * <p>
 * This is a mapping assertion rather than a round trip on purpose: the defect only shows up when the server's default
 * zone is not UTC, and the test JVM runs in UTC, so a save-and-reload would pass either way and protect nothing.
 */
class ProgrammingExerciseBuildAndTestDateMappingTest {

    @Test
    void shouldNormalizeTheBuildAndTestDateSoItDoesNotDependOnTheServersZone() throws NoSuchFieldException {
        Field field = ProgrammingExercise.class.getDeclaredField("buildAndTestStudentSubmissionsAfterDueDate");

        TimeZoneStorage timeZoneStorage = field.getAnnotation(TimeZoneStorage.class);

        assertThat(timeZoneStorage).as("the build and test date is on a secondary table and needs an explicit time zone storage strategy").isNotNull();
        assertThat(timeZoneStorage.value()).as("only NORMALIZE binds a plain timestamp, which is what the column's type is").isEqualTo(TimeZoneStorageType.NORMALIZE);
    }
}

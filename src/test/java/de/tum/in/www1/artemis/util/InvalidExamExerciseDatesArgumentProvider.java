package de.tum.in.www1.artemis.util;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Provides arguments in form of a for parameterized JUnit 5 tests (see {@link ParameterizedTest}) that are not a valid date configuration for exam exercises.
 * <p>
 * Exam exercises must not have any of the dates set. Use this with
 */
public class InvalidExamExerciseDatesArgumentProvider implements ArgumentsProvider {

    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext context) throws Exception {
        ZonedDateTime releaseDate = ZonedDateTime.now().plusHours(1);
        ZonedDateTime dueDate = releaseDate.plusHours(1);
        ZonedDateTime assessmentDueDate = dueDate.plusHours(1);
        ZonedDateTime exampleSolutionPublicationDate = ZonedDateTime.now();

        return Stream.of(new InvalidExamExerciseDateConfiguration(releaseDate, dueDate, assessmentDueDate, null),
                new InvalidExamExerciseDateConfiguration(releaseDate, null, null, null), new InvalidExamExerciseDateConfiguration(null, dueDate, null, null),
                new InvalidExamExerciseDateConfiguration(null, null, assessmentDueDate, null),
                new InvalidExamExerciseDateConfiguration(null, null, null, exampleSolutionPublicationDate)).map(Arguments::of);
    }

    /**
     * An exercise date attribute configuration that is invalid for exam exercises. It consists of the {@link #releaseDate()}, the {@link #dueDate()} and the {@link #assessmentDueDate()}.
     */
    public record InvalidExamExerciseDateConfiguration(ZonedDateTime releaseDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate,
            ZonedDateTime exampleSolutionPublicationDate) implements InvalidExerciseConfiguration<Exercise> {

        /**
         * {@inheritDoc}
         * Sets the release, due and assessment due date of the given exercise to the values of this configuration.
         */
        @Override
        public <T extends Exercise> T applyTo(T exercise) {
            exercise.setReleaseDate(releaseDate);
            exercise.setDueDate(dueDate);
            exercise.setAssessmentDueDate(assessmentDueDate);
            exercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);
            return exercise;
        }
    }
}

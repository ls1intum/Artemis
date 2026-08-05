package de.tum.cit.aet.artemis.programming.factories;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

/**
 * Factory for constructing {@link ProgrammingExercise} objects that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the {@code demo}
 * profile.
 * <p>
 * This factory only <b>constructs</b> the entity, it never persists it. It deliberately sets only the fields a caller has to provide: the project key, the repository URIs and the
 * default branch are derived by {@code ProgrammingExerciseCreationUpdateService#createProgrammingExercise}, and the build configuration is handed to that method separately, so
 * none of them must be pre-set for a new exercise.
 */
public final class ProgrammingExerciseFactory {

    private ProgrammingExerciseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a programming exercise for the given course.
     *
     * @param title               The title of the exercise.
     * @param shortName           The short name of the exercise. Also determines the project key and the repository names.
     * @param problemStatement    The markdown problem statement shown to students.
     * @param maxPoints           The achievable points.
     * @param bonusPoints         The achievable bonus points.
     * @param dates               The release, start, due and assessment due dates.
     * @param programmingLanguage The language students write their solution in.
     * @param projectType         The project type, required for languages that support several build tools.
     * @param packageName         The package the exercise template uses, required for languages that have packages.
     * @param course              The course the exercise belongs to.
     * @return The generated programming exercise.
     */
    public static ProgrammingExercise generateProgrammingExercise(String title, String shortName, @Nullable String problemStatement, double maxPoints, double bonusPoints,
            ExerciseDates dates, ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType, @Nullable String packageName, Course course) {
        ProgrammingExercise programmingExercise = ExerciseFactory.populateExercise(new ProgrammingExercise(), title, shortName, problemStatement, maxPoints, bonusPoints, dates,
                course);
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.setProjectType(projectType);
        programmingExercise.setPackageName(packageName);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        // At least one way of working on the exercise has to be allowed, otherwise validateProgrammingSettings rejects the exercise.
        programmingExercise.setAllowOnlineEditor(true);
        programmingExercise.setAllowOfflineIde(true);
        return programmingExercise;
    }
}

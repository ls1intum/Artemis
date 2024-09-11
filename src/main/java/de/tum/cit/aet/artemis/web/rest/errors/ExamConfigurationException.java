package de.tum.cit.aet.artemis.web.rest.errors;

import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.domain.exam.ExerciseGroup;

/**
 * Exception that will be thrown if the user tries to import an exam that contains programming exercises with an invalid shortName.
 * The error response will contain the exam, but the shortNames are removed for those ProgrammingExercises with an invalid one,
 * so the user can choose another one and trigger the import again
 */
public class ExamConfigurationException extends BadRequestAlertException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ExamConfigurationException(List<ExerciseGroup> exerciseGroupList, int numberOfInvalidProgrammingExercises, String errorKey) {
        super(ErrorConstants.EXAM_PROGRAMMING_EXERCISE_SHORT_NAME_INVALID, "Exam contains programming exercise(s) with invalid short name.", "ExamResource", errorKey,
                getParameters(exerciseGroupList, numberOfInvalidProgrammingExercises, errorKey));
    }

    private static Map<String, Object> getParameters(List<ExerciseGroup> exerciseGroupList, int numberOfInvalidProgrammingExercises, String errorKey) {
        Map<String, List<ExerciseGroup>> params = new HashMap<>();
        params.put("exerciseGroups", exerciseGroupList);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("numberOfInvalidProgrammingExercises", numberOfInvalidProgrammingExercises);
        parameters.put("skipAlert", true);
        parameters.put("message", "exam.examManagement.import." + errorKey);
        parameters.put("params", params);
        return parameters;
    }
}

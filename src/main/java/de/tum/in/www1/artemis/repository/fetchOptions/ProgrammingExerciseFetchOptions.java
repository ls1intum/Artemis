package de.tum.in.www1.artemis.repository.fetchOptions;

import de.tum.in.www1.artemis.domain.Exercise_;
import de.tum.in.www1.artemis.domain.ProgrammingExercise_;

/**
 * Fetch options for the {@link de.tum.in.www1.artemis.domain.ProgrammingExercise} entity.
 * Each option specifies an entity or a collection of entities to fetch eagerly when using a dynamic fetching query.
 */
public enum ProgrammingExerciseFetchOptions implements FetchOptions {

    // @formatter:off
    Categories(Exercise_.CATEGORIES),
    TeamAssignmentConfig(Exercise_.TEAM_ASSIGNMENT_CONFIG),
    AuxiliaryRepositories(ProgrammingExercise_.AUXILIARY_REPOSITORIES),
    GradingCriteria(Exercise_.GRADING_CRITERIA),
    StudentParticipations(ProgrammingExercise_.STUDENT_PARTICIPATIONS),
    TemplateParticipation(ProgrammingExercise_.TEMPLATE_PARTICIPATION),
    SolutionParticipation(ProgrammingExercise_.SOLUTION_PARTICIPATION),
    TestCases(ProgrammingExercise_.TEST_CASES),
    Tasks(ProgrammingExercise_.TASKS),
    StaticCodeAnalysisCategories(ProgrammingExercise_.STATIC_CODE_ANALYSIS_CATEGORIES),
    SubmissionPolicy(ProgrammingExercise_.SUBMISSION_POLICY),
    ExerciseHints(ProgrammingExercise_.EXERCISE_HINTS),
    Competencies(ProgrammingExercise_.COMPETENCIES),
    Teams(ProgrammingExercise_.TEAMS),
    TutorParticipations(ProgrammingExercise_.TUTOR_PARTICIPATIONS),
    ExampleSubmissions(ProgrammingExercise_.EXAMPLE_SUBMISSIONS),
    Attachments(ProgrammingExercise_.ATTACHMENTS),
    Posts(ProgrammingExercise_.POSTS),
    PlagiarismCases(ProgrammingExercise_.PLAGIARISM_CASES),
    PlagiarismDetectionConfig(ProgrammingExercise_.PLAGIARISM_DETECTION_CONFIG);
    // @formatter:on

    private final String fetchPath;

    ProgrammingExerciseFetchOptions(String fetchPath) {
        this.fetchPath = fetchPath;
    }

    public String getFetchPath() {
        return fetchPath;
    }
}

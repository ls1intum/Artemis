/**
 * Unified DTO containing comprehensive summary information about a course.
 * This model is used by both deletion and reset confirmation dialogs.
 *
 * For deletion: All data is permanently removed.
 * For reset: Course structure is preserved, but student data is removed.
 */
export interface CourseSummaryDTO {
    // Users
    numberOfStudents: number;
    numberOfTutors: number;
    numberOfEditors: number;
    numberOfInstructors: number;

    // Student Work
    numberOfParticipations: number;
    numberOfSubmissions: number;
    numberOfResults: number;

    // Communication
    numberOfConversations: number;
    numberOfPosts: number;
    numberOfAnswerPosts: number;

    // Learning Data
    numberOfCompetencies: number;
    numberOfCompetencyProgress: number;
    numberOfLearnerProfiles: number;

    // AI Data
    numberOfIrisChatSessions: number;
    numberOfLLMTraces: number;

    // Infrastructure
    numberOfBuilds: number;

    // Course Structure
    numberOfExams: number;
    numberOfExercises: number;
    numberOfProgrammingExercises: number;
    numberOfTextExercises: number;
    numberOfModelingExercises: number;
    numberOfQuizExercises: number;
    numberOfFileUploadExercises: number;
    numberOfLectures: number;
    numberOfFaqs: number;
    numberOfTutorialGroups: number;
}

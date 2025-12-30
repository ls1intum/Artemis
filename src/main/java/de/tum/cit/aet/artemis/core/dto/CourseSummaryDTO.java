package de.tum.cit.aet.artemis.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Data Transfer Object containing summary information about a course.
 * This DTO is used for both course deletion and reset operations to provide
 * administrators with an overview of what will be affected.
 * <p>
 * For deletion: All data is permanently removed.
 * For reset: Course structure is preserved, but student data is removed.
 *
 * @param numberOfStudents             the number of students enrolled
 * @param numberOfTutors               the number of tutors in the course
 * @param numberOfEditors              the number of editors in the course
 * @param numberOfInstructors          the number of instructors in the course
 * @param numberOfParticipations       the total number of exercise participations
 * @param numberOfSubmissions          the total number of submissions
 * @param numberOfResults              the total number of results (assessments)
 * @param numberOfConversations        the number of conversations
 * @param numberOfPosts                the number of posts
 * @param numberOfAnswerPosts          the number of answer posts (replies)
 * @param numberOfCompetencies         the number of competencies defined
 * @param numberOfCompetencyProgress   the number of competency progress records
 * @param numberOfLearnerProfiles      the number of learner profiles
 * @param numberOfIrisChatSessions     the number of Iris chat sessions
 * @param numberOfLLMTraces            the number of LLM token usage traces
 * @param numberOfBuilds               the number of build jobs
 * @param numberOfExams                the number of exams
 * @param numberOfExercises            the total number of exercises
 * @param numberOfProgrammingExercises the number of programming exercises
 * @param numberOfTextExercises        the number of text exercises
 * @param numberOfModelingExercises    the number of modeling exercises
 * @param numberOfQuizExercises        the number of quiz exercises
 * @param numberOfFileUploadExercises  the number of file upload exercises
 * @param numberOfLectures             the number of lectures
 * @param numberOfFaqs                 the number of FAQs
 * @param numberOfTutorialGroups       the number of tutorial groups
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseSummaryDTO(long numberOfStudents, long numberOfTutors, long numberOfEditors, long numberOfInstructors, long numberOfParticipations, long numberOfSubmissions,
        long numberOfResults, long numberOfConversations, long numberOfPosts, long numberOfAnswerPosts, long numberOfCompetencies, long numberOfCompetencyProgress,
        long numberOfLearnerProfiles, long numberOfIrisChatSessions, long numberOfLLMTraces, long numberOfBuilds, long numberOfExams, long numberOfExercises,
        long numberOfProgrammingExercises, long numberOfTextExercises, long numberOfModelingExercises, long numberOfQuizExercises, long numberOfFileUploadExercises,
        long numberOfLectures, long numberOfFaqs, long numberOfTutorialGroups) {
}

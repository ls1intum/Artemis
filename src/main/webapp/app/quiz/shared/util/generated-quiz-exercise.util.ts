import { QuizBatch, QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { User } from 'app/account/user/user.model';
import { hydrate } from 'app/foundation/util/deep-clone.util';
import { convertDateStringFromServer } from 'app/foundation/util/date.utils';
import { toQuizQuestion, toSubmittedAnswer, toSubmittedAnswerFromLiveClient, toSubmittedAnswerFromStudent } from 'app/quiz/shared/util/generated-quiz-question.util';
import { QuizExerciseWithSolution } from 'app/openapi/model/quiz-exercise-with-solution';
import { QuizExerciseWithQuestions } from 'app/openapi/model/quiz-exercise-with-questions';
import { QuizExerciseWithoutQuestions } from 'app/openapi/model/quiz-exercise-without-questions';
import { QuizSubmissionAfterEvaluation } from 'app/openapi/model/quiz-submission-after-evaluation';
import { QuizSubmissionBeforeEvaluation } from 'app/openapi/model/quiz-submission-before-evaluation';
import { QuizSubmissionForResult } from 'app/openapi/model/quiz-submission-for-result';
import { QuizSubmissionFromLiveClient } from 'app/openapi/model/quiz-submission-from-live-client';
import { QuizSubmissionFromStudent } from 'app/openapi/model/quiz-submission-from-student';
import { ResultAfterEvaluation } from 'app/openapi/model/result-after-evaluation';
import { ResultAfterEvaluationWithSubmission } from 'app/openapi/model/result-after-evaluation-with-submission';
import { StudentQuizParticipation } from 'app/openapi/model/student-quiz-participation';
import type { QuizBatch as GeneratedQuizBatch } from 'app/openapi/model/quiz-batch';
import { QuizBatchWithPassword } from 'app/openapi/model/quiz-batch-with-password';
import { QuizExerciseDetails } from 'app/openapi/model/quiz-exercise-details';
import { QuizExerciseForCourse } from 'app/openapi/model/quiz-exercise-for-course';
import { QuizExerciseForStudentResponse } from 'app/openapi/model/quiz-exercise-for-student-response';
import type { ExerciseVariantGroupReference as GeneratedExerciseVariantGroupReference } from 'app/openapi/model/exercise-variant-group-reference';
import { ExerciseVariantGroupReference } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizStatisticsOverview } from 'app/openapi/model/quiz-statistics-overview';
import { QuizPointStatistics } from 'app/openapi/model/quiz-point-statistics';
import type { QuizQuestionStatisticResponse as GeneratedQuizQuestionStatisticResponse } from 'app/openapi/model/quiz-question-statistic-response';
import { QuizPointStatisticsResponse, QuizQuestionStatisticResponse, QuizStatisticsOverviewResponse } from 'app/quiz/manage/statistics/quiz-statistics-response.model';

/**
 * Bridges the generated models of the quiz retrieval and participation endpoints and the quiz class graph.
 *
 * The quiz views are built on the class graph: they read prototype behaviour, dayjs dates, and the client-side
 * helper fields the classes declare. Conversion therefore happens once, at the service boundary.
 *
 * Only the dates the class graph reads as dayjs are converted. Course dates are left as the server sent them,
 * matching what the hand-written services did before the generated client replaced them.
 */

/**
 * Every generated shape of a full quiz exercise. They share the exercise fields and differ only in how much of the
 * question graph the server sends: none before the quiz starts, no solutions while it runs, everything afterwards,
 * plus the editor-only fields of the instructor view.
 */
type GeneratedQuizExercise = QuizExerciseWithSolution | QuizExerciseWithQuestions | QuizExerciseWithoutQuestions | QuizExerciseDetails | QuizExerciseForStudentResponse;

/** A submission carries evaluated answers, unevaluated answers, or evaluated answers plus the owning participation. */
type GeneratedQuizSubmission = QuizSubmissionAfterEvaluation | QuizSubmissionBeforeEvaluation | QuizSubmissionForResult;

/**
 * Converts a generated quiz batch into a {@link QuizBatch} instance.
 *
 * @param batch the generated batch, with or without the join password an instructor may read
 * @returns a class instance with a dayjs start time
 */
export function toQuizBatch(batch: GeneratedQuizBatch | QuizBatchWithPassword): QuizBatch {
    const quizBatch: QuizBatch = hydrate(new QuizBatch(), batch);
    quizBatch.startTime = convertDateStringFromServer(batch.startTime);
    return quizBatch;
}

function toExerciseVariantGroupReference(group: GeneratedExerciseVariantGroupReference): ExerciseVariantGroupReference {
    // The group carries the shared timeline of its variants; a string date here would be written back as missing and
    // wipe that timeline the next time the group is saved.
    return {
        id: group.id,
        title: group.title,
        maxPoints: group.maxPoints,
        releaseDate: convertDateStringFromServer(group.releaseDate),
        startDate: convertDateStringFromServer(group.startDate),
        dueDate: convertDateStringFromServer(group.dueDate),
        assessmentDueDate: convertDateStringFromServer(group.assessmentDueDate),
        exampleSolutionPublicationDate: convertDateStringFromServer(group.exampleSolutionPublicationDate),
    };
}

/**
 * Converts a generated quiz exercise into a {@link QuizExercise} instance.
 *
 * Categories arrive as JSON strings and are left for the caller to parse, since parsing them is shared with every
 * other exercise type.
 *
 * @param exercise the generated exercise in any of its question-visibility states
 * @returns a class instance with dayjs dates and a converted question graph
 */
export function toQuizExercise(exercise: GeneratedQuizExercise): QuizExercise {
    // The class types several enums that the generated model types as string literals, so the intersection hydrate()
    // returns collapses to never. Annotating the target keeps the class view of the data.
    const quizExercise: QuizExercise = hydrate(new QuizExercise(undefined, undefined), exercise);
    quizExercise.releaseDate = convertDateStringFromServer(exercise.releaseDate);
    quizExercise.startDate = convertDateStringFromServer(exercise.startDate);
    quizExercise.dueDate = convertDateStringFromServer(exercise.dueDate);
    quizExercise.assessmentDueDate = convertDateStringFromServer(exercise.assessmentDueDate);
    quizExercise.course = exercise.course ? hydrate(new Course(), exercise.course) : undefined;
    quizExercise.exerciseVariantGroup = exercise.exerciseVariantGroup ? toExerciseVariantGroupReference(exercise.exerciseVariantGroup) : undefined;
    quizExercise.quizBatches = exercise.quizBatches?.map(toQuizBatch);
    // Absent while the quiz has not started yet; the server withholds the questions until then.
    quizExercise.quizQuestions = 'quizQuestions' in exercise ? exercise.quizQuestions?.map(toQuizQuestion) : undefined;
    return quizExercise;
}

/**
 * Converts a row of a quiz exercise list into a {@link QuizExercise} instance.
 *
 * The list endpoints send a summary without questions, course or variant group, to keep the payload small.
 *
 * @param exercise the generated list row
 * @returns a class instance with dayjs dates
 */
export function toQuizExerciseFromListRow(exercise: QuizExerciseForCourse): QuizExercise {
    const quizExercise: QuizExercise = hydrate(new QuizExercise(undefined, undefined), exercise);
    quizExercise.releaseDate = convertDateStringFromServer(exercise.releaseDate);
    quizExercise.startDate = convertDateStringFromServer(exercise.startDate);
    quizExercise.dueDate = convertDateStringFromServer(exercise.dueDate);
    quizExercise.quizBatches = exercise.quizBatches?.map(toQuizBatch);
    return quizExercise;
}

/**
 * Converts the overview statistics of a quiz exercise into the model the statistics view works on.
 *
 * @param overview the generated overview, whose questions are per-question summaries
 * @returns the converted exercise carrying the overview statistics
 */
export function toQuizStatisticsOverview(overview: QuizStatisticsOverview): QuizStatisticsOverviewResponse {
    const { quizQuestions, participantsRated, participantsUnrated, ...exercise } = overview;
    const quizStatisticsOverview: QuizStatisticsOverviewResponse = toQuizExercise(exercise);
    quizStatisticsOverview.quizQuestions = quizQuestions;
    quizStatisticsOverview.participantsRated = participantsRated;
    quizStatisticsOverview.participantsUnrated = participantsUnrated;
    return quizStatisticsOverview;
}

/**
 * Converts the point distribution of a quiz exercise into the model the statistics view works on.
 *
 * @param pointStatistics the generated exercise with its point distribution
 * @returns the converted exercise carrying the point distribution
 */
export function toQuizPointStatistics(pointStatistics: QuizPointStatistics): QuizPointStatisticsResponse {
    const { quizPointStatistic, ...exercise } = pointStatistics;
    const quizPointStatistics: QuizPointStatisticsResponse = toQuizExercise(exercise);
    quizPointStatistics.quizPointStatistic = quizPointStatistic;
    return quizPointStatistics;
}

/**
 * Converts the statistic of one quiz question into the model the statistics view works on.
 *
 * @param questionStatistic the generated exercise with one question and its statistic
 * @returns the converted exercise carrying the converted question and its statistic
 */
export function toQuizQuestionStatistic(questionStatistic: GeneratedQuizQuestionStatisticResponse): QuizQuestionStatisticResponse {
    const { quizQuestion, quizQuestionStatistic, ...exercise } = questionStatistic;
    const quizQuestionStatisticResponse: QuizQuestionStatisticResponse = toQuizExercise(exercise);
    quizQuestionStatisticResponse.quizQuestion = quizQuestion ? toQuizQuestion(quizQuestion) : undefined;
    quizQuestionStatisticResponse.quizQuestionStatistic = quizQuestionStatistic;
    return quizQuestionStatisticResponse;
}

/**
 * Converts a generated result into a {@link Result} instance.
 *
 * @param result the generated result, with or without its submission
 * @returns a class instance with a dayjs completion date and, where present, a converted submission
 */
export function toResult(result: ResultAfterEvaluation | ResultAfterEvaluationWithSubmission): Result {
    const convertedResult: Result = hydrate(new Result(), result);
    convertedResult.completionDate = convertDateStringFromServer(result.completionDate);
    convertedResult.submission = 'submission' in result && result.submission ? toQuizSubmission(result.submission) : undefined;
    return convertedResult;
}

/**
 * Converts a generated quiz submission into a {@link QuizSubmission} instance.
 *
 * @param submission the generated submission in any of its three states
 * @returns a class instance with a dayjs submission date and a converted answer graph
 */
export function toQuizSubmission(submission: GeneratedQuizSubmission): QuizSubmission {
    const quizSubmission: QuizSubmission = hydrate(new QuizSubmission(), submission);
    quizSubmission.submissionDate = convertDateStringFromServer(submission.submissionDate);
    quizSubmission.submittedAnswers = submission.submittedAnswers?.map(toSubmittedAnswer);
    quizSubmission.results = 'results' in submission ? submission.results?.map(toResult) : undefined;
    quizSubmission.participation = 'participation' in submission && submission.participation ? toStudentParticipation(submission.participation) : undefined;
    return quizSubmission;
}

/**
 * Converts a generated quiz participation into a {@link StudentParticipation} instance.
 *
 * @param participation the generated participation, discriminated on `quizQuestionsType`
 * @returns a class instance with a dayjs initialization date and a converted exercise and submission graph
 */
export function toStudentParticipation(participation: StudentQuizParticipation): StudentParticipation {
    const studentParticipation: StudentParticipation = hydrate(new StudentParticipation(), participation);
    studentParticipation.initializationDate = convertDateStringFromServer(participation.initializationDate);
    studentParticipation.student = participation.student ? hydrate(new User(), participation.student) : undefined;
    studentParticipation.exercise = participation.exercise ? toQuizExercise(participation.exercise) : undefined;
    studentParticipation.submissions = participation.submissions?.map(toQuizSubmission);
    return studentParticipation;
}

/**
 * Converts a submission into the generated request model of the live submission endpoints.
 *
 * @param submission the submission the student assembled in the UI
 * @returns the generated request model, carrying only what the student decided
 */
export function toQuizSubmissionFromLiveClient(submission: QuizSubmission): QuizSubmissionFromLiveClient {
    return {
        id: submission.id,
        submittedAnswers: submission.submittedAnswers?.map(toSubmittedAnswerFromLiveClient),
    };
}

/**
 * Converts a submission into the generated request model of the practice and preview endpoints.
 *
 * @param submission the submission the student assembled in the UI
 * @returns the generated request model, carrying only what the student decided
 */
export function toQuizSubmissionFromStudent(submission: QuizSubmission): QuizSubmissionFromStudent {
    return {
        submittedAnswers: submission.submittedAnswers?.map(toSubmittedAnswerFromStudent) ?? [],
    };
}

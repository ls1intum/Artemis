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

/**
 * Bridges the generated models of the quiz participation endpoints and the quiz class graph.
 *
 * The quiz views are built on the class graph: they read prototype behaviour, dayjs dates, and the client-side
 * helper fields the classes declare. Conversion therefore happens once, at the service boundary.
 *
 * Only the dates the class graph reads as dayjs are converted. Course dates are left as the server sent them,
 * matching what the hand-written services did before the generated client replaced them.
 */

/** The three quiz states the server discriminates. They differ only in how much of the question graph they carry. */
type GeneratedQuizExercise = QuizExerciseWithSolution | QuizExerciseWithQuestions | QuizExerciseWithoutQuestions;

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

/**
 * Converts a generated quiz exercise into a {@link QuizExercise} instance.
 *
 * @param exercise the generated exercise in any of its three question-visibility states
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
    quizExercise.quizBatches = exercise.quizBatches?.map(toQuizBatch);
    // Absent while the quiz has not started yet; the server withholds the questions until then.
    quizExercise.quizQuestions = 'quizQuestions' in exercise ? exercise.quizQuestions?.map(toQuizQuestion) : undefined;
    return quizExercise;
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

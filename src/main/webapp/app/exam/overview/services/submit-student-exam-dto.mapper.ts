import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { SubmittedAnswer } from 'app/quiz/shared/entities/submitted-answer.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { Language } from 'app/course/shared/entities/course.model';

/**
 * Wire shape of the exam hand-in request body ({@code POST .../student-exams/submit}).
 *
 * These interfaces mirror the server-side {@code SubmitStudentExamDTO} record hierarchy. They intentionally carry only
 * the student exam id and the last-second submission changes; ownership, exam/course validation, the submitted flag and
 * the test-run/test-exam gating are all re-derived server-side from the persisted student exam. The polymorphic
 * discriminators ({@code submissionExerciseType}, and {@code type} for quiz answers) match the entity annotations so the
 * body binds to the server DTO exactly as a full-entity body used to.
 */
export interface SubmitStudentExamDTO {
    id?: number;
    exercises: SubmitExamExerciseDTO[];
}

export interface SubmitExamExerciseDTO {
    id?: number;
    studentParticipations: SubmitExamParticipationDTO[];
}

export interface SubmitExamParticipationDTO {
    id?: number;
    submissions: SubmitExamSubmissionDTO[];
}

interface EntityIdRef {
    id?: number;
}

/**
 * Discriminated union of the per-type submission variants, keyed by {@code submissionExerciseType} — the same
 * discriminator the server DTO hierarchy binds on. Modelling each variant separately (instead of one flat interface
 * with every optional field) makes the impossible states — e.g. a text submission carrying quiz answers —
 * unrepresentable and lets the compiler narrow field access after a {@code submissionExerciseType} check.
 */
interface BaseSubmissionDTO {
    id?: number;
}

export interface TextSubmissionDTO extends BaseSubmissionDTO {
    submissionExerciseType: SubmissionExerciseType.TEXT;
    text?: string;
    // The client-detected language must ride along: the server persists the reconstructed submission via a JPA merge that
    // overwrites every column, so a dropped language would be nulled out on every hand-in text edit. Mirrors the server
    // TextExamSubmissionDTO. The language is detected in text-exam-submission.component.ts.
    language?: Language;
}

export interface ModelingSubmissionDTO extends BaseSubmissionDTO {
    submissionExerciseType: SubmissionExerciseType.MODELING;
    model?: string;
    explanationText?: string;
}

export interface QuizSubmissionDTO extends BaseSubmissionDTO {
    submissionExerciseType: SubmissionExerciseType.QUIZ;
    submittedAnswers?: SubmittedAnswerFromLiveClientDTO[];
}

/**
 * Programming and file-upload submissions are never persisted through the exam hand-in; they are modelled as an inert
 * id-only variant so a legacy body still binds, but carry no content.
 */
export interface InertSubmissionDTO extends BaseSubmissionDTO {
    submissionExerciseType: SubmissionExerciseType.PROGRAMMING | SubmissionExerciseType.FILE_UPLOAD;
}

export type SubmitExamSubmissionDTO = TextSubmissionDTO | ModelingSubmissionDTO | QuizSubmissionDTO | InertSubmissionDTO;

/**
 * Discriminated union of the quiz submitted-answer variants, keyed by {@code type} (matching the server's
 * {@code QuizQuestionType} discriminator).
 */
export interface MultipleChoiceSubmittedAnswerDTO {
    type: QuizQuestionType.MULTIPLE_CHOICE;
    quizQuestion?: EntityIdRef;
    selectedOptions?: EntityIdRef[];
}

export interface DragAndDropSubmittedAnswerDTO {
    type: QuizQuestionType.DRAG_AND_DROP;
    quizQuestion?: EntityIdRef;
    mappings?: { dragItem?: EntityIdRef; dropLocation?: EntityIdRef }[];
}

export interface ShortAnswerSubmittedAnswerDTO {
    type: QuizQuestionType.SHORT_ANSWER;
    quizQuestion?: EntityIdRef;
    submittedTexts?: { text?: string; spot?: EntityIdRef }[];
}

export type SubmittedAnswerFromLiveClientDTO = MultipleChoiceSubmittedAnswerDTO | DragAndDropSubmittedAnswerDTO | ShortAnswerSubmittedAnswerDTO;

/**
 * Builds the slim submit request body from the live in-memory {@link StudentExam}.
 *
 * This is a pure mapper: it only copies scalars and ids into freshly-created objects, so — unlike the previous
 * {@code cloneDeep + breakCircularDependency} approach — it never carries back-references and needs no circular-reference
 * stripping. It is the single choke point for the submit body, so the normal hand-in and the localStorage recovery/resume
 * flow (which re-enters through the same {@code submitStudentExam} call) build an identical body.
 *
 * @param studentExam the in-memory student exam to submit
 * @returns the request body for the submit endpoint
 */
export function toSubmitStudentExamDTO(studentExam: StudentExam): SubmitStudentExamDTO {
    return {
        id: studentExam.id,
        exercises: (studentExam.exercises ?? []).map((exercise) => ({
            id: exercise.id,
            studentParticipations: (exercise.studentParticipations ?? []).map((participation) => ({
                id: participation.id,
                // flatMap + `?? []` drops any submission the builder cannot type (undefined discriminator); see toSubmissionDTO.
                submissions: (participation.submissions ?? []).flatMap((submission) => toSubmissionDTO(submission) ?? []),
            })),
        })),
    };
}

function toSubmissionDTO(submission: Submission): SubmitExamSubmissionDTO | undefined {
    switch (submission.submissionExerciseType) {
        case SubmissionExerciseType.TEXT:
            return {
                submissionExerciseType: SubmissionExerciseType.TEXT,
                id: submission.id,
                text: (submission as TextSubmission).text,
                language: (submission as TextSubmission).language,
            };
        case SubmissionExerciseType.MODELING: {
            const modelingSubmission = submission as ModelingSubmission;
            return {
                submissionExerciseType: SubmissionExerciseType.MODELING,
                id: submission.id,
                model: modelingSubmission.model,
                explanationText: modelingSubmission.explanationText,
            };
        }
        case SubmissionExerciseType.QUIZ:
            return {
                submissionExerciseType: SubmissionExerciseType.QUIZ,
                id: submission.id,
                submittedAnswers: ((submission as QuizSubmission).submittedAnswers ?? []).flatMap((answer) => toSubmittedAnswerDTO(answer) ?? []),
            };
        case SubmissionExerciseType.PROGRAMMING:
        case SubmissionExerciseType.FILE_UPLOAD:
            // programming / file-upload: never saved via the hand-in, the server accepts and ignores them.
            return { submissionExerciseType: submission.submissionExerciseType, id: submission.id };
        default:
            // Only reachable if submissionExerciseType is undefined (malformed submission): the discriminator cannot be
            // typed, so skip the entry (the caller filters out undefined). This is behavior-equivalent to the legacy
            // full-entity hand-in, whose server-side reconstruction already drops submissions of unknown/mismatched
            // type, so emitting nothing client-side is honest rather than shipping an untypeable inert shape.
            return undefined;
    }
}

function toSubmittedAnswerDTO(answer: SubmittedAnswer): SubmittedAnswerFromLiveClientDTO | undefined {
    switch (answer.type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return {
                type: QuizQuestionType.MULTIPLE_CHOICE,
                quizQuestion: { id: answer.quizQuestion?.id },
                selectedOptions: ((answer as MultipleChoiceSubmittedAnswer).selectedOptions ?? []).map((option) => ({ id: option.id })),
            };
        case QuizQuestionType.DRAG_AND_DROP:
            return {
                type: QuizQuestionType.DRAG_AND_DROP,
                quizQuestion: { id: answer.quizQuestion?.id },
                mappings: ((answer as DragAndDropSubmittedAnswer).mappings ?? []).map((mapping) => ({
                    dragItem: { id: mapping.dragItem?.id },
                    dropLocation: { id: mapping.dropLocation?.id },
                })),
            };
        case QuizQuestionType.SHORT_ANSWER:
            return {
                type: QuizQuestionType.SHORT_ANSWER,
                quizQuestion: { id: answer.quizQuestion?.id },
                submittedTexts: ((answer as ShortAnswerSubmittedAnswer).submittedTexts ?? []).map((submittedText) => ({
                    text: submittedText.text,
                    spot: { id: submittedText.spot?.id },
                })),
            };
        default:
            // Only reachable if type is undefined (all three real quiz question types are handled above): the
            // discriminator cannot be typed, so skip the entry (the caller filters out undefined). The server drops
            // answers of unknown type during reconstruction, so emitting nothing is behavior-equivalent.
            return undefined;
    }
}

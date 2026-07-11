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

export interface SubmitExamSubmissionDTO {
    submissionExerciseType: SubmissionExerciseType;
    id?: number;
    // text
    text?: string;
    // modeling
    model?: string;
    explanationText?: string;
    // quiz
    submittedAnswers?: SubmittedAnswerFromLiveClientDTO[];
}

export interface SubmittedAnswerFromLiveClientDTO {
    type: QuizQuestionType;
    quizQuestion?: EntityIdRef;
    // multiple-choice
    selectedOptions?: EntityIdRef[];
    // drag-and-drop
    mappings?: { dragItem?: EntityIdRef; dropLocation?: EntityIdRef }[];
    // short-answer
    submittedTexts?: { text?: string; spot?: EntityIdRef }[];
}

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
                submissions: (participation.submissions ?? []).map(toSubmissionDTO),
            })),
        })),
    };
}

function toSubmissionDTO(submission: Submission): SubmitExamSubmissionDTO {
    switch (submission.submissionExerciseType) {
        case SubmissionExerciseType.TEXT:
            return { submissionExerciseType: SubmissionExerciseType.TEXT, id: submission.id, text: (submission as TextSubmission).text };
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
                submittedAnswers: ((submission as QuizSubmission).submittedAnswers ?? []).map(toSubmittedAnswerDTO),
            };
        default:
            // programming / file-upload: never saved via the hand-in, the server accepts and ignores them.
            return { submissionExerciseType: submission.submissionExerciseType!, id: submission.id };
    }
}

function toSubmittedAnswerDTO(answer: SubmittedAnswer): SubmittedAnswerFromLiveClientDTO {
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
            return { type: answer.type!, quizQuestion: { id: answer.quizQuestion?.id } };
    }
}

import { describe, expect, it } from 'vitest';
import { QuizSubmissionDTO, toSubmitStudentExamDTO } from 'app/exam/overview/services/submit-student-exam-dto.mapper';
import { SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { Language } from 'app/course/shared/entities/course.model';

function withId<T extends { id?: number }>(entity: T, id: number): T {
    entity.id = id;
    return entity;
}

// The mapper only reads exercise.id and exercise.studentParticipations, not the exercise runtime type (the submission's
// submissionExerciseType drives the per-type mapping), so a TextExercise is a fine generic carrier for every case.
function exerciseWith(id: number, participationId: number, submissions: Submission[]): Exercise {
    const exercise = withId(new TextExercise(undefined, undefined), id);
    const participation = new StudentParticipation();
    participation.id = participationId;
    participation.submissions = submissions;
    exercise.studentParticipations = [participation];
    return exercise;
}

describe('toSubmitStudentExamDTO', () => {
    it('maps only id and exercises, dropping user/exam/dates/sessions', () => {
        const studentExam = new StudentExam();
        studentExam.id = 42;
        studentExam.user = { id: 7, login: 'student1' } as any;
        studentExam.workingTime = 3600;
        studentExam.exercises = [];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto).toEqual({ id: 42, exercises: [] });
        expect(dto).not.toHaveProperty('user');
        expect(dto).not.toHaveProperty('exam');
        expect(dto).not.toHaveProperty('workingTime');
    });

    it('tolerates a student exam without an exercises array', () => {
        const studentExam = new StudentExam();
        studentExam.id = 9;
        studentExam.exercises = undefined;

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto).toEqual({ id: 9, exercises: [] });
    });

    it('maps a text submission to the slim text variant preserving the submission id and language', () => {
        const submission = withId(new TextSubmission(), 100);
        submission.text = 'my answer';
        // the client-detected language must ride along: the server persists via a merge that overwrites the column, so a
        // dropped language would be nulled out on every hand-in text edit. See the mapper's TextSubmissionDTO.
        submission.language = Language.GERMAN;

        const studentExam = new StudentExam();
        studentExam.id = 1;
        studentExam.exercises = [exerciseWith(10, 1000, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto.exercises[0]).toEqual({
            id: 10,
            studentParticipations: [{ id: 1000, submissions: [{ submissionExerciseType: 'text', id: 100, text: 'my answer', language: Language.GERMAN }] }],
        });
    });

    it('maps a modeling submission preserving model and explanation', () => {
        const submission = withId(new ModelingSubmission(), 101);
        submission.model = '{"elements":[]}';
        submission.explanationText = 'because';

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(11, 1001, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto.exercises[0].studentParticipations[0].submissions[0]).toEqual({
            submissionExerciseType: 'modeling',
            id: 101,
            model: '{"elements":[]}',
            explanationText: 'because',
        });
    });

    it('maps quiz submitted answers to the reused FromLiveClient id-ref shape for every answer type', () => {
        const mcAnswer = new MultipleChoiceSubmittedAnswer();
        mcAnswer.quizQuestion = withId(new MultipleChoiceQuestion(), 20);
        mcAnswer.selectedOptions = [withId(new AnswerOption(), 201), withId(new AnswerOption(), 202)];

        const dndAnswer = new DragAndDropSubmittedAnswer();
        dndAnswer.quizQuestion = withId(new DragAndDropQuestion(), 21);
        dndAnswer.mappings = [new DragAndDropMapping(withId(new DragItem(), 211), withId(new DropLocation(), 212))];

        const saAnswer = new ShortAnswerSubmittedAnswer();
        saAnswer.quizQuestion = withId(new ShortAnswerQuestion(), 22);
        const submittedText = new ShortAnswerSubmittedText();
        submittedText.text = 'answer';
        submittedText.spot = withId(new ShortAnswerSpot(), 221);
        saAnswer.submittedTexts = [submittedText];

        const submission = withId(new QuizSubmission(), 102);
        submission.submittedAnswers = [mcAnswer, dndAnswer, saAnswer];

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(12, 1002, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);
        const mappedSubmission = dto.exercises[0].studentParticipations[0].submissions[0];

        // narrow the discriminated union to the quiz variant so submittedAnswers is statically visible
        expect(mappedSubmission.submissionExerciseType).toBe(SubmissionExerciseType.QUIZ);
        if (mappedSubmission.submissionExerciseType !== SubmissionExerciseType.QUIZ) {
            throw new Error('expected a quiz submission');
        }
        const quizDto: QuizSubmissionDTO = mappedSubmission;
        expect(quizDto.id).toBe(102);
        expect(quizDto.submittedAnswers).toEqual([
            { type: 'multiple-choice', quizQuestion: { id: 20 }, selectedOptions: [{ id: 201 }, { id: 202 }] },
            { type: 'drag-and-drop', quizQuestion: { id: 21 }, mappings: [{ dragItem: { id: 211 }, dropLocation: { id: 212 } }] },
            { type: 'short-answer', quizQuestion: { id: 22 }, submittedTexts: [{ text: 'answer', spot: { id: 221 } }] },
        ]);
    });

    it('maps a programming submission to the inert id-only variant', () => {
        const submission = withId(new ProgrammingSubmission(), 103);

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(13, 1003, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto.exercises[0].studentParticipations[0].submissions[0]).toEqual({ submissionExerciseType: 'programming', id: 103 });
    });

    it('maps a file-upload submission to the inert id-only variant', () => {
        const submission = withId(new FileUploadSubmission(), 105);

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(14, 1004, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto.exercises[0].studentParticipations[0].submissions[0]).toEqual({ submissionExerciseType: 'file-upload', id: 105 });
    });

    it('skips a submission with an unknown submissionExerciseType, keeping the valid ones', () => {
        const malformed = withId(new TextSubmission(), 106);
        // force the malformed state the narrow union cannot represent: an undefined discriminator
        (malformed as any).submissionExerciseType = undefined;
        const valid = withId(new TextSubmission(), 107);
        valid.text = 'kept';

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(15, 1005, [malformed, valid])];

        const dto = toSubmitStudentExamDTO(studentExam);

        // the malformed submission is dropped, the valid one survives
        expect(dto.exercises[0].studentParticipations[0].submissions).toEqual([{ submissionExerciseType: 'text', id: 107, text: 'kept' }]);
    });

    it('maps a quiz submission without submitted answers to an empty answer array', () => {
        const submission = withId(new QuizSubmission(), 108);
        submission.submittedAnswers = undefined;

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(16, 1006, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);
        const mappedSubmission = dto.exercises[0].studentParticipations[0].submissions[0];

        expect(mappedSubmission.submissionExerciseType).toBe(SubmissionExerciseType.QUIZ);
        if (mappedSubmission.submissionExerciseType !== SubmissionExerciseType.QUIZ) {
            throw new Error('expected a quiz submission');
        }
        expect(mappedSubmission.submittedAnswers).toEqual([]);
    });

    it('skips a submitted answer with an unknown type and tolerates undefined nested collections', () => {
        // an answer whose discriminator is undefined must be dropped
        const malformedAnswer = new MultipleChoiceSubmittedAnswer();
        (malformedAnswer as any).type = undefined;

        // valid answers with undefined nested collections / question exercise the `?? []` and `?.id` fallbacks
        const mcAnswer = new MultipleChoiceSubmittedAnswer();
        mcAnswer.quizQuestion = undefined;
        mcAnswer.selectedOptions = undefined;

        const dndAnswer = new DragAndDropSubmittedAnswer();
        dndAnswer.quizQuestion = withId(new DragAndDropQuestion(), 30);
        dndAnswer.mappings = undefined;

        const saAnswer = new ShortAnswerSubmittedAnswer();
        saAnswer.quizQuestion = withId(new ShortAnswerQuestion(), 31);
        saAnswer.submittedTexts = undefined;

        const submission = withId(new QuizSubmission(), 109);
        submission.submittedAnswers = [malformedAnswer, mcAnswer, dndAnswer, saAnswer];

        const studentExam = new StudentExam();
        studentExam.exercises = [exerciseWith(17, 1007, [submission])];

        const dto = toSubmitStudentExamDTO(studentExam);
        const mappedSubmission = dto.exercises[0].studentParticipations[0].submissions[0];

        expect(mappedSubmission.submissionExerciseType).toBe(SubmissionExerciseType.QUIZ);
        if (mappedSubmission.submissionExerciseType !== SubmissionExerciseType.QUIZ) {
            throw new Error('expected a quiz submission');
        }
        // the malformed answer is dropped; the three valid ones keep their type with empty nested collections
        expect(mappedSubmission.submittedAnswers).toEqual([
            { type: 'multiple-choice', quizQuestion: { id: undefined }, selectedOptions: [] },
            { type: 'drag-and-drop', quizQuestion: { id: 30 }, mappings: [] },
            { type: 'short-answer', quizQuestion: { id: 31 }, submittedTexts: [] },
        ]);
    });

    it('tolerates a participation with undefined submissions and an exercise with undefined participations', () => {
        const exerciseWithoutParticipations = withId(new TextExercise(undefined, undefined), 18);
        exerciseWithoutParticipations.studentParticipations = undefined;

        const exerciseWithNullSubmissions = withId(new TextExercise(undefined, undefined), 19);
        const participation = new StudentParticipation();
        participation.id = 1008;
        participation.submissions = undefined;
        exerciseWithNullSubmissions.studentParticipations = [participation];

        const studentExam = new StudentExam();
        studentExam.id = 6;
        studentExam.exercises = [exerciseWithoutParticipations, exerciseWithNullSubmissions];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto.exercises[0]).toEqual({ id: 18, studentParticipations: [] });
        expect(dto.exercises[1]).toEqual({ id: 19, studentParticipations: [{ id: 1008, submissions: [] }] });
    });

    it('emits explicit empty arrays (never omitted) so the server does not read null', () => {
        const studentExam = new StudentExam();
        studentExam.id = 5;
        studentExam.exercises = [exerciseWith(10, 1000, [])];

        const dto = toSubmitStudentExamDTO(studentExam);

        expect(dto.exercises[0].studentParticipations[0].submissions).toEqual([]);
        // JSON round-trip proves the empty array survives on the wire (no @JsonInclude omission on the client side).
        expect(JSON.parse(JSON.stringify(dto)).exercises[0].studentParticipations[0].submissions).toEqual([]);
    });
});

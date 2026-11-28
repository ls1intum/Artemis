import { describe, expect, it } from 'vitest';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { PlagiarismCaseExercise } from 'app/openapi/model/plagiarism-case-exercise';
import { PlagiarismCaseOverview } from 'app/openapi/model/plagiarism-case-overview';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismSubmissionElement } from 'app/plagiarism/shared/entities/PlagiarismSubmissionElement';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import {
    toPlagiarismCaseForScores,
    toPlagiarismCaseFromDetail,
    toPlagiarismCaseFromOverview,
    toPlagiarismCaseInfo,
    toPlagiarismCaseInfoMap,
    toPlagiarismCaseVerdictResponse,
    toPlagiarismComparison,
} from 'app/plagiarism/shared/services/plagiarism-api.mapper';

describe('Plagiarism API mapper', () => {
    const minimalExercise: PlagiarismCaseExercise = { id: 3, type: 'text' };

    it.each([
        ['PLAGIARISM', PlagiarismVerdict.PLAGIARISM],
        ['POINT_DEDUCTION', PlagiarismVerdict.POINT_DEDUCTION],
        ['WARNING', PlagiarismVerdict.WARNING],
        ['NO_PLAGIARISM', PlagiarismVerdict.NO_PLAGIARISM],
    ] as const)('should map the verdict %s to the client enum', (verdict, expected) => {
        expect(toPlagiarismCaseInfo({ id: 1, verdict, createdByContinuousPlagiarismControl: false }).verdict).toBe(expected);
        expect(toPlagiarismCaseForScores({ id: 1, verdict }).verdict).toBe(expected);
    });

    it.each([
        ['CONFIRMED', PlagiarismStatus.CONFIRMED],
        ['DENIED', PlagiarismStatus.DENIED],
        ['NONE', PlagiarismStatus.NONE],
    ] as const)('should map the status %s to the client enum', (status, expected) => {
        const comparison = toPlagiarismComparison({
            id: 1,
            similarity: 1,
            status,
            submissionA: { id: 1, submissionId: 1, size: 1 },
            submissionB: { id: 2, submissionId: 2, size: 1 },
        });
        expect(comparison?.status).toBe(expected);
    });

    it.each([
        ['text', ExerciseType.TEXT],
        ['programming', ExerciseType.PROGRAMMING],
        ['modeling', ExerciseType.MODELING],
        ['file-upload', ExerciseType.FILE_UPLOAD],
        ['quiz', ExerciseType.QUIZ],
    ] as const)('should map the exercise type %s to the client enum', (type, expected) => {
        const overview: PlagiarismCaseOverview = {
            id: 1,
            exercise: { id: 3, type },
            plagiarismSubmissionCount: 0,
            createdByContinuousPlagiarismControl: false,
            hasStudentAnswer: false,
        };
        expect(toPlagiarismCaseFromOverview(overview).exercise?.type).toBe(expected);
    });

    it('should keep optional values undefined instead of defaulting them', () => {
        const plagiarismCase = toPlagiarismCaseFromOverview({
            id: 1,
            exercise: minimalExercise,
            plagiarismSubmissionCount: 0,
            createdByContinuousPlagiarismControl: false,
            hasStudentAnswer: false,
        });

        expect(plagiarismCase.student).toBeUndefined();
        expect(plagiarismCase.post).toBeUndefined();
        expect(plagiarismCase.verdict).toBeUndefined();
        expect(plagiarismCase.verdictDate).toBeUndefined();
        expect(plagiarismCase.verdictBy).toBeUndefined();
        expect(plagiarismCase.exercise?.title).toBeUndefined();
        expect(plagiarismCase.exercise?.dueDate).toBeUndefined();
        expect(plagiarismCase.plagiarismSubmissionCount).toBe(0);
        expect(plagiarismCase.hasStudentAnswer).toBe(false);
        expect(toPlagiarismCaseForScores({ id: 1 })).toEqual({ id: 1, verdict: undefined, studentId: undefined });
    });

    it('should map the nested user, post, exercise and submissions of a case detail', () => {
        const plagiarismCase = toPlagiarismCaseFromDetail({
            id: 1,
            exercise: { id: 3, title: 'Exam text', type: 'text', examId: 4, examTitle: 'Exam', dueDate: '2026-11-01T10:00:00Z' },
            student: { id: 7, login: 'student', name: 'Student', visibleRegistrationNumber: '123' },
            post: { id: 9, creationDate: '2026-11-02T10:00:00Z' },
            verdict: 'WARNING',
            verdictMessage: 'Do not do this again',
            verdictBy: { id: 8 },
            plagiarismSubmissionCount: 1,
            createdByContinuousPlagiarismControl: false,
            verdictPointDeduction: 0,
            plagiarismSubmissions: [{ id: 11, submissionId: 21, size: 30, score: 75 }],
        });

        expect(plagiarismCase.exercise).toEqual(expect.objectContaining({ id: 3, examId: 4, examTitle: 'Exam', dueDate: '2026-11-01T10:00:00Z' }));
        expect(plagiarismCase.student).toEqual({ id: 7, login: 'student', name: 'Student', visibleRegistrationNumber: '123' });
        expect(plagiarismCase.post).toEqual({ id: 9, creationDate: '2026-11-02T10:00:00Z' });
        expect(plagiarismCase.verdictBy).toEqual({ id: 8, login: undefined, name: undefined, visibleRegistrationNumber: undefined });
        expect(plagiarismCase.verdictMessage).toBe('Do not do this again');
        expect(plagiarismCase.plagiarismSubmissions).toEqual([{ id: 11, submissionId: 21, studentLogin: undefined, size: 30, score: 75, plagiarismComparison: undefined }]);
    });

    it('should map the saved verdict', () => {
        expect(
            toPlagiarismCaseVerdictResponse({
                verdict: 'POINT_DEDUCTION',
                verdictPointDeduction: 30,
                verdictBy: { id: 8, name: 'Instructor' },
                verdictDate: '2026-11-03T10:00:00Z',
            }),
        ).toEqual({
            verdict: PlagiarismVerdict.POINT_DEDUCTION,
            verdictMessage: undefined,
            verdictPointDeduction: 30,
            verdictBy: { id: 8, login: undefined, name: 'Instructor', visibleRegistrationNumber: undefined },
            verdictDate: '2026-11-03T10:00:00Z',
        });
    });

    it('should map an empty info map to an empty object', () => {
        expect(toPlagiarismCaseInfoMap({})).toEqual({});
    });

    it('should build the comparison and element classes of the split view', () => {
        const comparison = toPlagiarismComparison({
            id: 1,
            similarity: 20,
            submissionA: { id: 1, submissionId: 11, size: 1, elements: [{ id: 5, column: 1, line: 1, length: 4 }] },
            submissionB: { id: 2, submissionId: 12, size: 1 },
        });

        expect(comparison).toBeInstanceOf(PlagiarismComparison);
        expect(comparison?.submissionA.elements?.[0]).toBeInstanceOf(PlagiarismSubmissionElement);
        expect(comparison?.submissionA.elements?.[0].file).toBeUndefined();
        expect(comparison?.matches).toBeUndefined();
        expect(comparison?.status).toBeUndefined();
    });

    it('should return undefined for a comparison without both submissions', () => {
        expect(toPlagiarismComparison({ id: 1, similarity: 20, submissionB: { id: 2, submissionId: 12, size: 1 } })).toBeUndefined();
        expect(toPlagiarismComparison({ id: 1, similarity: 20 })).toBeUndefined();
    });
});

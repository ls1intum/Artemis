import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { PlagiarismStatus } from 'app/plagiarism/shared/entities/PlagiarismStatus';
import { PlagiarismCase, PlagiarismCaseDTO, PlagiarismCaseVerdictResponse } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';
import { PlagiarismComparison } from 'app/plagiarism/shared/entities/PlagiarismComparison';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { PlagiarismCaseOverview } from 'app/openapi/model/plagiarism-case-overview';
import { PlagiarismCaseDetail } from 'app/openapi/model/plagiarism-case-detail';
import { PlagiarismComparison as PlagiarismComparisonDto } from 'app/openapi/model/plagiarism-comparison';

/**
 * Runs the adapter against the real generated PlagiarismCaseApi and PlagiarismApi, so the URLs, query encoding and bodies on the wire are checked, not only the delegation.
 */
describe('Plagiarism Cases Service', () => {
    let service: PlagiarismCasesService;
    let httpMock: HttpTestingController;

    const overview: PlagiarismCaseOverview = {
        id: 1,
        exercise: { id: 3, title: 'Text', type: 'text', courseId: 1, courseTitle: 'Course', dueDate: '2026-11-01T10:00:00Z' },
        student: { id: 7, login: 'studentA', name: 'Student A' },
        post: { id: 9, creationDate: '2026-11-02T10:00:00Z' },
        verdict: 'POINT_DEDUCTION',
        verdictDate: '2026-11-03T10:00:00Z',
        verdictBy: { id: 8, login: 'instructor', name: 'Instructor' },
        plagiarismSubmissionCount: 2,
        createdByContinuousPlagiarismControl: false,
        hasStudentAnswer: true,
    };

    const expectedCase: PlagiarismCase = {
        id: 1,
        exercise: {
            id: 3,
            title: 'Text',
            shortName: undefined,
            type: ExerciseType.TEXT,
            dueDate: '2026-11-01T10:00:00Z',
            courseId: 1,
            courseTitle: 'Course',
            examId: undefined,
            examTitle: undefined,
            continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: undefined,
        },
        student: { id: 7, login: 'studentA', name: 'Student A', visibleRegistrationNumber: undefined },
        post: { id: 9, creationDate: '2026-11-02T10:00:00Z' },
        verdict: PlagiarismVerdict.POINT_DEDUCTION,
        verdictDate: '2026-11-03T10:00:00Z',
        verdictBy: { id: 8, login: 'instructor', name: 'Instructor', visibleRegistrationNumber: undefined },
        plagiarismSubmissionCount: 2,
        createdByContinuousPlagiarismControl: false,
        hasStudentAnswer: true,
    };

    const detail: PlagiarismCaseDetail = {
        id: 1,
        exercise: { id: 3, title: 'Text', type: 'text', courseId: 1 },
        plagiarismSubmissionCount: 1,
        createdByContinuousPlagiarismControl: true,
        verdictPointDeduction: 0,
        plagiarismSubmissions: [{ id: 11, submissionId: 21, studentLogin: 'studentA', size: 30, plagiarismComparison: { id: 4, similarity: 80, status: 'CONFIRMED' } }],
    };

    const textExercise = { id: 1, type: ExerciseType.TEXT, course: { id: 1 } } as TextExercise;
    const examTextExercise = { id: 1, type: ExerciseType.TEXT, exerciseGroup: { exam: { id: 1, course: { id: 1 } } } } as TextExercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(PlagiarismCasesService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get the plagiarism cases of a course for the scores', () => {
        let result: PlagiarismCaseDTO[] | undefined;
        service.getCoursePlagiarismCasesForScores(1).subscribe((plagiarismCases) => (result = plagiarismCases));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/plagiarism-cases/for-scores' }).flush([{ id: 10, verdict: 'PLAGIARISM', studentId: 5 }, { id: 11 }]);

        expect(result).toEqual([
            { id: 10, verdict: PlagiarismVerdict.PLAGIARISM, studentId: 5 },
            { id: 11, verdict: undefined, studentId: undefined },
        ]);
    });

    it('should get the plagiarism cases of a course for the instructor', () => {
        let result: PlagiarismCase[] | undefined;
        service.getCoursePlagiarismCasesForInstructor(1).subscribe((plagiarismCases) => (result = plagiarismCases));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/plagiarism-cases/for-instructor' }).flush([overview]);

        expect(result).toEqual([expectedCase]);
    });

    it('should get the plagiarism cases of an exam for the instructor', () => {
        let result: PlagiarismCase[] | undefined;
        service.getExamPlagiarismCasesForInstructor(1, 42).subscribe((plagiarismCases) => (result = plagiarismCases));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/exams/42/plagiarism-cases/for-instructor' }).flush([overview]);

        expect(result).toEqual([expectedCase]);
    });

    it('should get the plagiarism case detail for the instructor', () => {
        let result: PlagiarismCase | undefined;
        service.getPlagiarismCaseDetailForInstructor(1, 2).subscribe((plagiarismCase) => (result = plagiarismCase));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/plagiarism-cases/2/for-instructor' }).flush(detail);

        expect(result?.verdict).toBeUndefined();
        expect(result?.verdictPointDeduction).toBe(0);
        expect(result?.createdByContinuousPlagiarismControl).toBe(true);
        expect(result?.exercise?.type).toBe(ExerciseType.TEXT);
        expect(result?.plagiarismSubmissions).toEqual([
            { id: 11, submissionId: 21, studentLogin: 'studentA', size: 30, score: undefined, plagiarismComparison: { id: 4, similarity: 80, status: PlagiarismStatus.CONFIRMED } },
        ]);
    });

    it('should get the plagiarism case detail for the student', () => {
        let result: PlagiarismCase | undefined;
        service.getPlagiarismCaseDetailForStudent(1, 2).subscribe((plagiarismCase) => (result = plagiarismCase));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/plagiarism-cases/2/for-student' }).flush(detail);

        expect(result?.id).toBe(1);
    });

    it('should send only the verdict when no message or point deduction is given', () => {
        let result: PlagiarismCaseVerdictResponse | undefined;
        service.saveVerdict(1, 2, { verdict: PlagiarismVerdict.PLAGIARISM }).subscribe((response) => (result = response));

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/plagiarism/courses/1/plagiarism-cases/2/verdict' });
        expect(req.request.body).toEqual({ verdict: 'PLAGIARISM' });
        expect(Object.keys(req.request.body)).toEqual(['verdict']);
        req.flush({ verdict: 'PLAGIARISM', verdictPointDeduction: 0, verdictBy: { id: 8, login: 'instructor' }, verdictDate: '2026-11-03T10:00:00Z' });

        expect(result).toEqual({
            verdict: PlagiarismVerdict.PLAGIARISM,
            verdictMessage: undefined,
            verdictPointDeduction: 0,
            verdictBy: { id: 8, login: 'instructor', name: undefined, visibleRegistrationNumber: undefined },
            verdictDate: '2026-11-03T10:00:00Z',
        });
    });

    it('should send the message and the point deduction when they are given', () => {
        service.saveVerdict(1, 2, { verdict: PlagiarismVerdict.WARNING, verdictMessage: 'message', verdictPointDeduction: 0 }).subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/plagiarism/courses/1/plagiarism-cases/2/verdict' });
        expect(req.request.body).toEqual({ verdict: 'WARNING', verdictMessage: 'message', verdictPointDeduction: 0 });
        req.flush({ verdict: 'WARNING', verdictMessage: 'message', verdictPointDeduction: 0, verdictBy: { id: 8 }, verdictDate: '2026-11-03T10:00:00Z' });
    });

    it('should propagate an error when saving the verdict fails', () => {
        let status: number | undefined;
        service.saveVerdict(1, 1, { verdict: PlagiarismVerdict.WARNING }).subscribe({
            next: () => {
                throw new Error('expected an error');
            },
            error: (error) => (status = error.status),
        });

        httpMock.expectOne({ method: 'PUT', url: '/api/plagiarism/courses/1/plagiarism-cases/1/verdict' }).flush('Invalid data', { status: 400, statusText: 'Bad Request' });

        expect(status).toBe(400);
    });

    it('should send one exerciseId parameter per exercise and map the infos by exercise id', () => {
        let result: { [exerciseId: number]: PlagiarismCaseInfo } | undefined;
        service.getPlagiarismCaseInfosForStudent(7, [1, 2]).subscribe((infos) => (result = infos));

        httpMock
            .expectOne({ method: 'GET', url: '/api/plagiarism/courses/7/plagiarism-cases?exerciseId=1&exerciseId=2' })
            .flush({ '1': { id: 5, verdict: 'WARNING', createdByContinuousPlagiarismControl: false }, '2': { id: 6, createdByContinuousPlagiarismControl: true } });

        expect(result).toEqual({
            1: { id: 5, verdict: PlagiarismVerdict.WARNING, createdByContinuousPlagiarismControl: false },
            2: { id: 6, verdict: undefined, createdByContinuousPlagiarismControl: true },
        });
    });

    it('should map an empty info map to an empty object', () => {
        let result: { [exerciseId: number]: PlagiarismCaseInfo } | undefined;
        service.getPlagiarismCaseInfosForStudent(7, [1]).subscribe((infos) => (result = infos));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/7/plagiarism-cases?exerciseId=1' }).flush({});

        expect(result).toEqual({});
    });

    it('should map the comparison of the split view with its submissions, elements and matches', () => {
        const comparison: PlagiarismComparisonDto = {
            id: 1,
            submissionA: {
                id: 1,
                submissionId: 11,
                studentLogin: 'Your submission',
                size: 2,
                elements: [{ id: 5, column: 1, line: 2, length: 3, file: 'a.txt', endLine: 2, endColumn: 4 }],
            },
            submissionB: { id: 2, submissionId: 12, size: 2 },
            matches: [{ startA: 0, startB: 1, length: 2 }],
            similarity: 50,
            status: 'DENIED',
        };
        let result: PlagiarismComparison | undefined;
        service.getPlagiarismComparisonForSplitView(1, 1).subscribe((plagiarismComparison) => (result = plagiarismComparison));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/plagiarism-comparisons/1/for-split-view' }).flush(comparison);

        expect(result).toBeInstanceOf(PlagiarismComparison);
        expect(result?.status).toBe(PlagiarismStatus.DENIED);
        expect(result?.similarity).toBe(50);
        expect(result?.matches).toEqual([{ startA: 0, startB: 1, length: 2 }]);
        expect(result?.submissionA.studentLogin).toBe('Your submission');
        expect(result?.submissionA.elements).toEqual([expect.objectContaining({ id: 5, column: 1, line: 2, length: 3, file: 'a.txt', endLine: 2, endColumn: 4 })]);
        expect(result?.submissionB.studentLogin).toBeUndefined();
        expect(result?.submissionB.elements).toBeUndefined();
    });

    it('should emit undefined for a comparison without one of its submissions', () => {
        let emitted = false;
        let result: PlagiarismComparison | undefined;
        service.getPlagiarismComparisonForSplitView(1, 1).subscribe((plagiarismComparison) => {
            emitted = true;
            result = plagiarismComparison;
        });

        httpMock
            .expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/plagiarism-comparisons/1/for-split-view' })
            .flush({ id: 1, similarity: 50, submissionA: { id: 1, submissionId: 11, size: 2 } });

        expect(emitted).toBe(true);
        expect(result).toBeUndefined();
    });

    it('should PUT the status of a comparison', () => {
        service.updatePlagiarismComparisonStatus(2, 3, PlagiarismStatus.DENIED).subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: '/api/plagiarism/courses/2/plagiarism-comparisons/3/status' });
        expect(req.request.body).toEqual({ status: 'DENIED' });
        req.flush(null);
    });

    it('should send deleteAll=false by default when cleaning up', () => {
        service.cleanUpPlagiarism(5, 99).subscribe();

        httpMock.expectOne({ method: 'DELETE', url: '/api/plagiarism/exercises/5/plagiarism-results/99/plagiarism-comparisons?deleteAll=false' }).flush(null);
    });

    it('should send deleteAll=true when cleaning up everything', () => {
        service.cleanUpPlagiarism(5, 99, true).subscribe();

        httpMock.expectOne({ method: 'DELETE', url: '/api/plagiarism/exercises/5/plagiarism-results/99/plagiarism-comparisons?deleteAll=true' }).flush(null);
    });

    it.each([textExercise, examTextExercise])('should derive the course of the exercise to count its plagiarism cases', (exercise) => {
        let result: number | undefined;
        service.getNumberOfPlagiarismCasesForExercise(exercise).subscribe((count) => (result = count));

        httpMock.expectOne({ method: 'GET', url: '/api/plagiarism/courses/1/exercises/1/plagiarism-cases-count' }).flush(2);

        expect(result).toBe(2);
    });
});

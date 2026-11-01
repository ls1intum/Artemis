import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuizQuestionPoolService } from './quiz-question-pool.service';
import { PoolBuildStart, PoolProgress, PoolQuestion, PoolQuestionPage, PoolSettings } from './quiz-question-pool.types';

describe('QuizQuestionPoolService', () => {
    let service: QuizQuestionPoolService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(QuizQuestionPoolService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    const item = {
        id: 11,
        competencyId: 9,
        competencyTitle: 'Arrays',
        lectureUnitId: 4,
        lectureUnitName: 'Arrays in Java',
        language: 'en',
        questionType: 'single-choice',
        difficulty: 'MEDIUM',
        state: 'FILTERED',
        reviewState: 'PENDING',
        question: { type: 'single-choice', title: 'Q', questionText: 'Q?', options: [{ text: 'A', correct: true }, { text: 'B' }] },
        decision: { accepted: true, aggregateScore: 0.7, verdicts: [{ mode: 'OFF_TOPIC', severity: 0.3, triggered: false, justification: 'slightly off' }], rationale: 'ok' },
        updatedAt: '2026-10-13T10:00:00Z',
    };

    it('should load and map one page of the pool questions', () => {
        let result: PoolQuestionPage | undefined;
        service.getQuestions(7, { page: 0, size: 100 }).subscribe((page) => (result = page));

        const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/hyperion/courses/7/quiz-question-pool/questions?flagged=false&page=0&size=100');
        req.flush({ items: [{ ...item, flagged: true }], totalCount: 250, competencies: [{ id: 9, title: 'Arrays' }, { id: 10 }] });

        expect(result!.totalCount).toBe(250);
        expect(result!.competencies).toEqual([
            { id: 9, title: 'Arrays' },
            { id: 10, title: '' },
        ]);
        expect(result!.items).toHaveLength(1);
        expect(result!.items[0].flagged).toBe(true);
        expect(result!.items[0].question!.id).toBe('pool-11');
        expect(result!.items[0].question!.options[1].correct).toBe(false);
        expect(result!.items[0].decision).toEqual({
            accepted: true,
            aggregateScore: 0.7,
            verdicts: [{ mode: 'OFF_TOPIC', severity: 0.3, triggered: false, justification: 'slightly off', flagged: false }],
            rationale: 'ok',
        });
        expect(result!.items[0].competencyTitle).toBe('Arrays');
        expect(result!.items[0].lectureUnitId).toBe(4);
        expect(result!.items[0].lectureUnitName).toBe('Arrays in Java');
    });

    it('should pass the competency and flag filters as query parameters', () => {
        service.getQuestions(7, { competencyId: 9, flagged: true, page: 2, size: 50 }).subscribe();

        const req = httpMock.expectOne((r) => r.url === '/api/hyperion/courses/7/quiz-question-pool/questions?competencyId=9&flagged=true&page=2&size=50');
        req.flush({ items: [], totalCount: 0, competencies: [] });
    });

    it('should ask the server to cancel the running build', () => {
        let completed = false;
        service.cancelBuild(7).subscribe(() => (completed = true));

        httpMock.expectOne((r) => r.method === 'POST' && r.url === '/api/hyperion/courses/7/quiz-question-pool/build/cancel').flush(null, { status: 202, statusText: 'Accepted' });

        expect(completed).toBe(true);
    });

    it('should map progress with defaults for omitted counts', () => {
        let result: PoolProgress | undefined;
        service.getProgress(7).subscribe((progress) => (result = progress));

        httpMock.expectOne((r) => r.url === '/api/hyperion/courses/7/quiz-question-pool/progress').flush({ running: true, total: 4, judged: 1 });

        expect(result).toEqual({ running: true, total: 4, toGenerate: 0, toJudge: 0, judged: 1, accepted: 0, rejected: 0, failed: 0 });
    });

    it('should start a build and report a running build as not started', () => {
        const results: PoolBuildStart[] = [];
        service.startBuild(7).subscribe((result) => results.push(result));
        httpMock.expectOne((r) => r.method === 'POST' && r.url === '/api/hyperion/courses/7/quiz-question-pool/build').flush({ started: true, enqueued: 12 });

        service.startBuild(7).subscribe((result) => results.push(result));
        httpMock
            .expectOne((r) => r.method === 'POST' && r.url === '/api/hyperion/courses/7/quiz-question-pool/build')
            .flush({ started: false, enqueued: 0 }, { status: 409, statusText: 'Conflict' });

        expect(results).toEqual([
            { started: true, enqueued: 12 },
            { started: false, enqueued: 0 },
        ]);
    });

    it('should propagate other build errors', () => {
        let failed = false;
        service.startBuild(7).subscribe({ error: () => (failed = true) });
        httpMock.expectOne((r) => r.method === 'POST').flush({}, { status: 500, statusText: 'Server Error' });

        expect(failed).toBe(true);
    });

    it('should read and update the settings', () => {
        const stored: PoolSettings = {
            poolGenerationEnabled: false,
            singleChoiceQuestionsPerLectureUnit: 12,
            multipleChoiceEnabled: false,
            multipleChoiceQuestionsPerLectureUnit: 12,
            multipleChoiceMinCorrectOptions: 1,
            multipleChoiceMaxCorrectOptions: 4,
            easyPercent: 20,
            mediumPercent: 50,
            hardPercent: 30,
        };
        let loaded: PoolSettings | undefined;
        service.getSettings(7).subscribe((settings) => (loaded = settings));
        httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/hyperion/courses/7/quiz-question-pool/settings').flush(stored);
        expect(loaded).toEqual(stored);

        const changed: PoolSettings = { ...stored, poolGenerationEnabled: true, singleChoiceQuestionsPerLectureUnit: 30, multipleChoiceEnabled: true, hardPercent: 30 };
        let updated: PoolSettings | undefined;
        service.updateSettings(7, changed).subscribe((settings) => (updated = settings));
        const req = httpMock.expectOne((r) => r.method === 'PUT' && r.url === '/api/hyperion/courses/7/quiz-question-pool/settings');
        expect(req.request.body).toEqual(changed);
        req.flush(changed);
        expect(updated).toEqual(changed);
    });

    it('should send the review decision and map the updated item', () => {
        let updated: PoolQuestion | undefined;
        service.review(7, 11, 'APPROVED').subscribe((question) => (updated = question));

        const req = httpMock.expectOne((r) => r.method === 'PUT' && r.url === '/api/hyperion/courses/7/quiz-question-pool/questions/11/review');
        expect(req.request.body).toEqual({ reviewState: 'APPROVED' });
        req.flush({ ...item, reviewState: 'APPROVED' });

        expect(updated!.reviewState).toBe('APPROVED');
        expect(updated!.id).toBe(11);
    });
});

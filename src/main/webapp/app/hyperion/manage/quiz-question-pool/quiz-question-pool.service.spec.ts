import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { QuizQuestionPoolService } from './quiz-question-pool.service';
import { PoolBuildStart, PoolProgress, PoolQuestion, PoolSettings } from './quiz-question-pool.types';

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
        language: 'en',
        questionType: 'single-choice',
        difficulty: 'MEDIUM',
        state: 'FILTERED',
        reviewState: 'PENDING',
        question: { type: 'single-choice', title: 'Q', questionText: 'Q?', options: [{ text: 'A', correct: true }, { text: 'B' }] },
        decision: { accepted: true, aggregateScore: 0.7, verdicts: [{ mode: 'OFF_TOPIC', severity: 0.3, triggered: false, justification: 'slightly off' }], rationale: 'ok' },
        updatedAt: '2026-10-13T10:00:00Z',
    };

    it('should load and map the pool questions', () => {
        let result: PoolQuestion[] | undefined;
        service.getQuestions(7).subscribe((questions) => (result = questions));

        const req = httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/hyperion/courses/7/quiz-question-pool/questions');
        req.flush([item]);

        expect(result).toHaveLength(1);
        expect(result![0].question!.id).toBe('pool-11');
        expect(result![0].question!.options[1].correct).toBe(false);
        expect(result![0].decision).toEqual({
            accepted: true,
            aggregateScore: 0.7,
            verdicts: [{ mode: 'OFF_TOPIC', severity: 0.3, triggered: false, justification: 'slightly off' }],
            rationale: 'ok',
        });
        expect(result![0].competencyTitle).toBe('Arrays');
    });

    it('should pass the competency filter as a query parameter', () => {
        service.getQuestions(7, 9).subscribe();

        const req = httpMock.expectOne((r) => r.url === '/api/hyperion/courses/7/quiz-question-pool/questions?competencyId=9');
        req.flush([]);
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
        let loaded: PoolSettings | undefined;
        service.getSettings(7).subscribe((settings) => (loaded = settings));
        httpMock.expectOne((r) => r.method === 'GET' && r.url === '/api/hyperion/courses/7/quiz-question-pool/settings').flush({ candidatesPerCompetency: 20 });
        expect(loaded).toEqual({ poolGenerationEnabled: false, candidatesPerCompetency: 20 });

        let stored: PoolSettings | undefined;
        service.updateSettings(7, { poolGenerationEnabled: true, candidatesPerCompetency: 30 }).subscribe((settings) => (stored = settings));
        const req = httpMock.expectOne((r) => r.method === 'PUT' && r.url === '/api/hyperion/courses/7/quiz-question-pool/settings');
        expect(req.request.body).toEqual({ poolGenerationEnabled: true, candidatesPerCompetency: 30 });
        req.flush({ poolGenerationEnabled: true, candidatesPerCompetency: 30 });
        expect(stored).toEqual({ poolGenerationEnabled: true, candidatesPerCompetency: 30 });
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

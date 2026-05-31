import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathNode } from 'app/math/shared/entities/math-node.model';

describe('MathSubmissionService', () => {
    setupTestBed({ zoneless: true });

    let service: MathSubmissionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MathSubmissionService,
                {
                    provide: SubmissionService,
                    useValue: {
                        convert: vi.fn((s) => s),
                        convertResponse: vi.fn((res) => res),
                    },
                },
            ],
        });
        service = TestBed.inject(MathSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('fetches data for the math editor by participation id', async () => {
        const sub = new MathSubmission();
        sub.id = 9;

        const promise = service.getDataForMathEditor(7).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/math/participations/7/math-editor' });
        req.flush(sub);
        const res = await promise;

        expect(res?.body?.id).toBe(9);
    });

    it('creates a submission for an exercise', async () => {
        const sub = new MathSubmission();
        const promise = service.create(sub, 3).toPromise();
        const req = httpMock.expectOne({ method: 'POST', url: 'api/math/exercises/3/math-submissions' });
        req.flush({ ...sub, id: 42 });
        const res = await promise;

        expect(res?.body?.id).toBe(42);
    });

    it('updates a submission for an exercise', async () => {
        const sub = new MathSubmission();
        sub.id = 1;
        const promise = service.update(sub, 3).toPromise();
        const req = httpMock.expectOne({ method: 'PUT', url: 'api/math/exercises/3/math-submissions' });
        req.flush(sub);
        const res = await promise;

        expect(res?.body?.id).toBe(1);
    });

    it('fetches a single submission by id', async () => {
        const promise = service.getMathSubmission(11).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/math/math-submissions/11' });
        const sub = new MathSubmission();
        sub.id = 11;
        req.flush(sub);
        const res = await promise;

        expect(res?.id).toBe(11);
    });

    it('fetches a submission for assessment', async () => {
        const promise = service.getMathSubmissionForAssessment(11).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/math/math-submissions/11/for-assessment' });
        const sub = new MathSubmission();
        sub.id = 11;
        req.flush(sub);
        const res = await promise;

        expect(res?.id).toBe(11);
    });

    it('lists submitted submissions for an exercise', async () => {
        const promise = service.getSubmittedSubmissions(3).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/math/exercises/3/math-submissions' });
        req.flush([new MathSubmission(), new MathSubmission()]);
        const res = await promise;

        expect(res?.length).toBe(2);
    });

    it('saves a manual result', async () => {
        const promise = service.saveManualResult(11, 8.5).toPromise();
        const req = httpMock.expectOne({ method: 'PUT', url: 'api/math/math-submissions/11/manual-result' });
        expect(req.request.body).toBe(8.5);
        const sub = new MathSubmission();
        sub.id = 11;
        req.flush(sub);
        const res = await promise;

        expect(res?.id).toBe(11);
    });

    it('asks the backend for next-step hints', async () => {
        const node: MathNode = { type: 'var', value: 'x' };
        const promise = service.getHints(3, node).toPromise();
        const req = httpMock.expectOne({ method: 'POST', url: 'api/math/exercises/3/hints' });
        req.flush([]);
        const res = await promise;

        expect(res).toEqual([]);
    });
});

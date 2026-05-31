import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { MathNode } from 'app/proof/shared/entities/math-node.model';

describe('ProofSubmissionService', () => {
    setupTestBed({ zoneless: true });

    let service: ProofSubmissionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                ProofSubmissionService,
                {
                    provide: SubmissionService,
                    useValue: {
                        convert: vi.fn((s) => s),
                        convertResponse: vi.fn((res) => res),
                    },
                },
            ],
        });
        service = TestBed.inject(ProofSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('fetches data for the proof editor by participation id', async () => {
        const sub = new ProofSubmission();
        sub.id = 9;

        const promise = service.getDataForProofEditor(7).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/proof/participations/7/proof-editor' });
        req.flush(sub);
        const res = await promise;

        expect(res?.body?.id).toBe(9);
    });

    it('creates a submission for an exercise', async () => {
        const sub = new ProofSubmission();
        const promise = service.create(sub, 3).toPromise();
        const req = httpMock.expectOne({ method: 'POST', url: 'api/proof/exercises/3/proof-submissions' });
        req.flush({ ...sub, id: 42 });
        const res = await promise;

        expect(res?.body?.id).toBe(42);
    });

    it('updates a submission for an exercise', async () => {
        const sub = new ProofSubmission();
        sub.id = 1;
        const promise = service.update(sub, 3).toPromise();
        const req = httpMock.expectOne({ method: 'PUT', url: 'api/proof/exercises/3/proof-submissions' });
        req.flush(sub);
        const res = await promise;

        expect(res?.body?.id).toBe(1);
    });

    it('fetches a single submission by id', async () => {
        const promise = service.getProofSubmission(11).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/proof/proof-submissions/11' });
        const sub = new ProofSubmission();
        sub.id = 11;
        req.flush(sub);
        const res = await promise;

        expect(res?.id).toBe(11);
    });

    it('fetches a submission for assessment', async () => {
        const promise = service.getProofSubmissionForAssessment(11).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/proof/proof-submissions/11/for-assessment' });
        const sub = new ProofSubmission();
        sub.id = 11;
        req.flush(sub);
        const res = await promise;

        expect(res?.id).toBe(11);
    });

    it('lists submitted submissions for an exercise', async () => {
        const promise = service.getSubmittedSubmissions(3).toPromise();
        const req = httpMock.expectOne({ method: 'GET', url: 'api/proof/exercises/3/proof-submissions' });
        req.flush([new ProofSubmission(), new ProofSubmission()]);
        const res = await promise;

        expect(res?.length).toBe(2);
    });

    it('saves a manual result', async () => {
        const promise = service.saveManualResult(11, 8.5).toPromise();
        const req = httpMock.expectOne({ method: 'PUT', url: 'api/proof/proof-submissions/11/manual-result' });
        expect(req.request.body).toBe(8.5);
        const sub = new ProofSubmission();
        sub.id = 11;
        req.flush(sub);
        const res = await promise;

        expect(res?.id).toBe(11);
    });

    it('asks the backend for next-step hints', async () => {
        const node: MathNode = { type: 'var', value: 'x' };
        const promise = service.getHints(3, node).toPromise();
        const req = httpMock.expectOne({ method: 'POST', url: 'api/proof/exercises/3/hints' });
        req.flush([]);
        const res = await promise;

        expect(res).toEqual([]);
    });
});

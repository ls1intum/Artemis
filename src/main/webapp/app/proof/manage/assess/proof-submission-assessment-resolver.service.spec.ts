import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';
import { ProofSubmissionAssessmentResolverService } from 'app/proof/manage/assess/proof-submission-assessment-resolver.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';

describe('ProofSubmissionAssessmentResolverService', () => {
    setupTestBed({ zoneless: true });

    let resolver: ProofSubmissionAssessmentResolverService;
    let submissionService: { getProofSubmissionForAssessment: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        submissionService = { getProofSubmissionForAssessment: vi.fn() };
        TestBed.configureTestingModule({
            providers: [ProofSubmissionAssessmentResolverService, { provide: ProofSubmissionService, useValue: submissionService }],
        });
        resolver = TestBed.inject(ProofSubmissionAssessmentResolverService);
    });

    const snap = (submissionId?: string) => ({ paramMap: convertToParamMap(submissionId !== undefined ? { submissionId } : {}) }) as unknown as ActivatedRouteSnapshot;

    it('returns undefined when submissionId is missing', async () => {
        const result = await firstValueFrom(resolver.resolve(snap()));
        expect(result).toBeUndefined();
    });

    it('fetches the submission for assessment by id', async () => {
        const sub = new ProofSubmission();
        sub.id = 7;
        submissionService.getProofSubmissionForAssessment.mockReturnValue(of(sub));

        const result = await firstValueFrom(resolver.resolve(snap('7')));

        expect(result?.id).toBe(7);
        expect(submissionService.getProofSubmissionForAssessment).toHaveBeenCalledWith(7);
    });

    it('returns undefined when the backend call errors', async () => {
        submissionService.getProofSubmissionForAssessment.mockReturnValue(throwError(() => new Error('boom')));

        const result = await firstValueFrom(resolver.resolve(snap('7')));

        expect(result).toBeUndefined();
    });
});

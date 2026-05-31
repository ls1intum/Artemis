import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';
import { firstValueFrom, of, throwError } from 'rxjs';
import { MathSubmissionAssessmentResolverService } from 'app/math/manage/assess/math-submission-assessment-resolver.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';

describe('MathSubmissionAssessmentResolverService', () => {
    setupTestBed({ zoneless: true });

    let resolver: MathSubmissionAssessmentResolverService;
    let submissionService: { getMathSubmissionForAssessment: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        submissionService = { getMathSubmissionForAssessment: vi.fn() };
        TestBed.configureTestingModule({
            providers: [MathSubmissionAssessmentResolverService, { provide: MathSubmissionService, useValue: submissionService }],
        });
        resolver = TestBed.inject(MathSubmissionAssessmentResolverService);
    });

    const snap = (submissionId?: string) => ({ paramMap: convertToParamMap(submissionId !== undefined ? { submissionId } : {}) }) as unknown as ActivatedRouteSnapshot;

    it('returns undefined when submissionId is missing', async () => {
        const result = await firstValueFrom(resolver.resolve(snap()));
        expect(result).toBeUndefined();
    });

    it('fetches the submission for assessment by id', async () => {
        const sub = new MathSubmission();
        sub.id = 7;
        submissionService.getMathSubmissionForAssessment.mockReturnValue(of(sub));

        const result = await firstValueFrom(resolver.resolve(snap('7')));

        expect(result?.id).toBe(7);
        expect(submissionService.getMathSubmissionForAssessment).toHaveBeenCalledWith(7);
    });

    it('returns undefined when the backend call errors', async () => {
        submissionService.getMathSubmissionForAssessment.mockReturnValue(throwError(() => new Error('boom')));

        const result = await firstValueFrom(resolver.resolve(snap('7')));

        expect(result).toBeUndefined();
    });
});

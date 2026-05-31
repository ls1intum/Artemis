import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ProofSubmissionAssessmentComponent } from 'app/proof/manage/assess/proof-submission-assessment.component';
import { ProofBlockRegistryService } from 'app/proof/manage/service/proof-block-registry.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { ProofSubmission } from 'app/proof/shared/entities/proof-submission.model';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('ProofSubmissionAssessmentComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofSubmissionAssessmentComponent;

    beforeEach(() => {
        const exercise = new ProofExercise(undefined);
        exercise.id = 11;
        const participation = { id: 1, exercise } as StudentParticipation;
        const submission = new ProofSubmission();
        submission.id = 5;
        submission.participation = participation;
        submission.results = [{ score: 80 } as any];

        TestBed.configureTestingModule({
            imports: [ProofSubmissionAssessmentComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ProofBlockRegistryService, { getBlockRegistry: () => of([]) as any }),
                MockProvider(ProofSubmissionService, { saveManualResult: vi.fn().mockReturnValue(of(submission)) }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({ proofSubmission: submission }),
                        snapshot: { params: { courseId: 9 }, parent: null },
                    },
                },
            ],
        }).overrideComponent(ProofSubmissionAssessmentComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(ProofSubmissionAssessmentComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('hydrates exercise/submission/result from the resolver data', () => {
        expect(component.submission.id).toBe(5);
        expect(component.proofExercise.id).toBe(11);
        expect(component.result?.score).toBe(80);
        expect(component.isLoading).toBe(false);
    });

    it('extracts courseId from the route snapshot', () => {
        expect(component.courseId).toBe(9);
    });
});

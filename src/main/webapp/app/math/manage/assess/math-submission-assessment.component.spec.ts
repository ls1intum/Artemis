import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathSubmissionAssessmentComponent } from 'app/math/manage/assess/math-submission-assessment.component';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('MathSubmissionAssessmentComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathSubmissionAssessmentComponent;

    beforeEach(() => {
        const exercise = new MathExercise(undefined);
        exercise.id = 11;
        const participation = { id: 1, exercise } as StudentParticipation;
        const submission = new MathSubmission();
        submission.id = 5;
        submission.participation = participation;
        submission.results = [{ score: 80 } as any];

        TestBed.configureTestingModule({
            imports: [MathSubmissionAssessmentComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any }),
                MockProvider(MathSubmissionService, { saveManualResult: vi.fn().mockReturnValue(of(submission)) }),
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
                        data: of({ mathSubmission: submission }),
                        snapshot: { params: { courseId: 9 }, parent: null },
                    },
                },
            ],
        }).overrideComponent(MathSubmissionAssessmentComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathSubmissionAssessmentComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('hydrates exercise/submission/result from the resolver data', () => {
        expect(component.submission.id).toBe(5);
        expect(component.mathExercise.id).toBe(11);
        expect(component.result?.score).toBe(80);
        expect(component.isLoading).toBe(false);
    });

    it('extracts courseId from the route snapshot', () => {
        expect(component.courseId).toBe(9);
    });
});

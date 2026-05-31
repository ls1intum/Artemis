import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/shared/service/alert.service';
import { MathSubmissionComponent } from 'app/math/participate/math-submission/math-submission.component';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathBlockRegistryService } from 'app/math/manage/service/math-block-registry.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HeaderParticipationPageComponent } from 'app/exercise/exercise-headers/participation-page/header-participation-page.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ExerciseSubmitButtonComponent } from 'app/exercise/shared/exercise-submit-button/exercise-submit-button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

describe('MathSubmissionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathSubmissionComponent;
    let fixture: ComponentFixture<MathSubmissionComponent>;
    let mathSubmissionService: MathSubmissionService;
    let alertService: AlertService;

    const mockExercise = (): MathExercise => {
        const ex = new MathExercise(undefined);
        ex.id = 10;
        ex.type = ExerciseType.MATH;
        return ex;
    };

    const mockParticipation = (exercise: MathExercise): StudentParticipation => {
        return { id: 42, exercise } as StudentParticipation;
    };

    const mockSubmission = (participation: StudentParticipation): MathSubmission => {
        const sub = new MathSubmission();
        sub.id = 5;
        sub.submitted = false;
        sub.participation = participation;
        return sub;
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathSubmissionComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                MockProvider(MathSubmissionService),
                MockProvider(MathBlockRegistryService, { getBlockRegistry: () => of([]) as any }),
                MockProvider(TranslateService, {
                    instant: (key: string) => key,
                    get: (key: string) => of(key) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ participationId: '42' }) },
                    },
                },
            ],
            declarations: [],
        }).overrideComponent(MathSubmissionComponent, {
            remove: { imports: [HeaderParticipationPageComponent, ButtonComponent, ExerciseSubmitButtonComponent] },
            add: {
                imports: [
                    MockComponent(HeaderParticipationPageComponent),
                    MockComponent(ButtonComponent),
                    MockComponent(ExerciseSubmitButtonComponent),
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                    MockPipe(HtmlForMarkdownPipe),
                ],
            },
        });

        fixture = TestBed.createComponent(MathSubmissionComponent);
        component = fixture.componentInstance;
        mathSubmissionService = TestBed.inject(MathSubmissionService);
        alertService = TestBed.inject(AlertService);
    });

    it('should load participation and existing submission on init', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        fixture.detectChanges();

        expect(component.mathExercise).toBe(exercise);
        expect(component.participation).toBe(participation);
        expect(component.submission).toBe(submission);
    });

    it('should show error alert when loading fails', () => {
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(throwError(() => new Error('network')));
        const errorSpy = vi.spyOn(alertService, 'error');

        fixture.detectChanges();

        expect(errorSpy).toHaveBeenCalledWith('artemisApp.mathExercise.error');
    });

    it('should call create when saving a new submission', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = new MathSubmission();
        submission.participation = participation;
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const createSpy = vi.spyOn(mathSubmissionService, 'create').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        component.save();

        expect(createSpy).toHaveBeenCalled();
    });

    it('should call update when saving an existing submission', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        fixture.detectChanges();

        component.save();

        expect(updateSpy).toHaveBeenCalled();
    });

    it('should set submitted=true and call update on submit', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const submittedSub = { ...submission, submitted: true, results: [{ score: 100 }] };
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submittedSub as any })));
        fixture.detectChanges();

        component.submit();

        expect(updateSpy).toHaveBeenCalled();
        expect(component.submission.submitted).toBe(true);
        expect(component.result?.score).toBe(100);
    });

    it('should revert submitted=false when submit fails', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(throwError(() => new Error('server error')));
        fixture.detectChanges();

        component.submit();

        expect(component.submission.submitted).toBe(false);
    });
});

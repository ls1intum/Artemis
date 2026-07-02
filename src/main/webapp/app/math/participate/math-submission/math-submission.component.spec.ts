import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { Confirmation, ConfirmationService } from 'primeng/api';
import { AlertService } from 'app/foundation/service/alert.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MathSubmissionComponent } from 'app/math/participate/math-submission/math-submission.component';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { MathParticipation, MathSubmission } from 'app/math/shared/entities/math-submission.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

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

    const mockParticipation = (exercise: MathExercise): MathParticipation => ({ id: 42, exercise });

    const mockSubmission = (participation: MathParticipation): MathSubmission => {
        const sub = new MathSubmission();
        sub.id = 5;
        sub.submitted = false;
        sub.content = 'draft';
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
                MockProvider(ConfirmationService),
                MockProvider(ArtemisTranslatePipe, { transform: (key: string) => key }),
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
        }).overrideComponent(MathSubmissionComponent, { set: { imports: [], template: '' } });

        fixture = TestBed.createComponent(MathSubmissionComponent);
        component = fixture.componentInstance;
        mathSubmissionService = TestBed.inject(MathSubmissionService);
        alertService = TestBed.inject(AlertService);
    });

    const initWith = (submission: MathSubmission) => {
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        component.ngOnInit();
    };

    it('should load the existing submission and exercise on init', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));

        component.ngOnInit();

        expect(component.exercise()).toBe(exercise);
        expect(component.submission()).toBe(submission);
        expect(component.content()).toBe('draft');
    });

    it('should call update when saving', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        component.ngOnInit();

        component.save();

        expect(updateSpy).toHaveBeenCalled();
        expect(updateSpy.mock.calls[0][0].submitted).toBe(false);
    });

    it('should mark the submission as submitted on submit', () => {
        const exercise = mockExercise();
        const participation = mockParticipation(exercise);
        const submission = mockSubmission(participation);
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(of(new HttpResponse({ body: submission })));
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: { ...submission, submitted: true } as any })));
        component.ngOnInit();

        component.submit();

        expect(updateSpy).toHaveBeenCalled();
        expect(updateSpy.mock.calls[0][0].submitted).toBe(true);
    });

    it('should show a success toast after saving', () => {
        const submission = mockSubmission(mockParticipation(mockExercise()));
        initWith(submission);
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        const successSpy = vi.spyOn(alertService, 'success');

        component.save();

        expect(successSpy).toHaveBeenCalledWith('artemisApp.mathExercise.saveSuccessful');
    });

    it('should show a success toast after submitting', () => {
        const submission = mockSubmission(mockParticipation(mockExercise()));
        initWith(submission);
        vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: { ...submission, submitted: true } as any })));
        const successSpy = vi.spyOn(alertService, 'success');

        component.submit();

        expect(successSpy).toHaveBeenCalledWith('artemisApp.mathExercise.submitSuccessful');
    });

    it('should ask for confirmation before re-submitting an already submitted solution', () => {
        const submission = mockSubmission(mockParticipation(mockExercise()));
        submission.submitted = true;
        initWith(submission);
        const updateSpy = vi.spyOn(mathSubmissionService, 'update').mockReturnValue(of(new HttpResponse({ body: submission })));
        const confirmSpy = vi.spyOn((component as any).confirmationService, 'confirm');

        component.submit();

        // The dialog is opened and nothing is persisted until the student confirms.
        expect(confirmSpy).toHaveBeenCalled();
        expect(updateSpy).not.toHaveBeenCalled();

        // Confirming runs the persist callback.
        (confirmSpy.mock.calls[0][0] as Confirmation).accept!();
        expect(updateSpy).toHaveBeenCalled();
        expect(updateSpy.mock.calls[0][0].submitted).toBe(true);
    });

    it('should not throw when loading fails', () => {
        vi.spyOn(mathSubmissionService, 'getDataForMathEditor').mockReturnValue(throwError(() => new Error('network')));

        expect(() => component.ngOnInit()).not.toThrow();
    });
});

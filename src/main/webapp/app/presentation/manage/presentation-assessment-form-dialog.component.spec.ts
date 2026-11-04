import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentFormDialogComponent, PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { PresentationAssessment, PresentationGradingMethod, PresentationRubricScale } from 'app/presentation/shared/entities/presentation-assessment.model';
import { ExerciseTitle } from 'app/exercise/shared/entities/exercise/exercise-title.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { typeIntoInputNumber } from 'src/test/javascript/spec/helpers/input-number-test-helpers';

describe('PresentationAssessmentFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentFormDialogComponent>;
    let component: PresentationAssessmentFormDialogComponent;
    let saved: Mock<(value: PresentationAssessmentFormDialogResult) => void>;
    let cancelled: Mock<(value: void) => void>;
    let deleteRequested: Mock<(value: PresentationAssessment) => void>;

    const courseId = 1;
    const presentationAssessment: PresentationAssessment = {
        id: 42,
        title: 'Final presentation',
        description: 'Final project presentation',
        gradingMethod: PresentationGradingMethod.SCORE,
        maxPoints: 20,
        courseId,
    };
    const exercise: ExerciseTitle = { id: 7, title: 'Linked exercise' };

    beforeEach(async () => {
        saved = vi.fn();
        cancelled = vi.fn();
        deleteRequested = vi.fn();

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentFormDialogComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, { provide: DialogService, useClass: MockDialogService }, provideArtemisTumAetUiTranslator()],
        }).compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentFormDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('presentationAssessment', presentationAssessment);
        fixture.componentRef.setInput('exercises', [exercise]);
        component.saved.subscribe(saved);
        component.cancelled.subscribe(cancelled);
        component.deleteRequested.subscribe(deleteRequested);
        fixture.detectChanges();
    });

    function byTestId(testId: string): HTMLElement | null {
        return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    }

    it('should initialize the presentation fields', () => {
        expect(component.editForm.controls.title.value).toBe('Final presentation');
        expect(component.exercises()).toEqual([exercise]);
    });

    it('should preserve form edits when exercises are loaded asynchronously', () => {
        fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, exerciseId: exercise.id });
        fixture.componentRef.setInput('exercises', []);
        fixture.detectChanges();
        component.editForm.patchValue({ title: 'Unsaved title', description: 'Unsaved description' });

        fixture.componentRef.setInput('exercises', [exercise]);
        fixture.detectChanges();

        expect(component.editForm.controls.title.value).toBe('Unsaved title');
        expect(component.editForm.controls.description.value).toBe('Unsaved description');
        expect(component.editForm.controls.exercise.value).toBe(exercise);
    });

    it('should preserve the linked exercise when saving before exercises load', () => {
        fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, exerciseId: exercise.id });
        fixture.componentRef.setInput('exercises', []);
        fixture.detectChanges();
        component.editForm.controls.title.setValue('Updated title');

        component.save();

        expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ title: 'Updated title', exerciseId: exercise.id }) });
    });

    it('should preserve an explicit removal when exercises load later', () => {
        fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, exerciseId: exercise.id });
        fixture.componentRef.setInput('exercises', []);
        fixture.detectChanges();
        component.editForm.controls.exercise.setValue(undefined);
        component.editForm.controls.exercise.markAsDirty();

        fixture.componentRef.setInput('exercises', [exercise]);
        fixture.detectChanges();
        component.save();

        expect(component.editForm.controls.exercise.value).toBeUndefined();
        expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ exerciseId: undefined }) });
    });

    it('should save a replacement exercise selected by the user', () => {
        fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, exerciseId: exercise.id });
        fixture.detectChanges();
        const replacement: ExerciseTitle = { id: 8, title: 'Replacement' };
        component.editForm.controls.exercise.setValue(replacement);
        component.editForm.controls.exercise.markAsDirty();

        component.save();

        expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ exerciseId: replacement.id }) });
    });

    it('should not save while typed exercise text has not been selected from the list', () => {
        fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, exerciseId: exercise.id });
        fixture.detectChanges();
        // The autocomplete writes the raw text into the control while the user types.
        component.editForm.controls.exercise.setValue('Linked exer' as unknown as ExerciseTitle);
        component.editForm.controls.exercise.markAsDirty();

        component.save();

        expect(component.editForm.controls.exercise.hasError('exerciseNotSelected')).toBe(true);
        expect(component.editForm.controls.exercise.touched).toBe(true);
        expect(saved).not.toHaveBeenCalled();
    });

    it('should save once an exercise is selected after typing', () => {
        component.editForm.controls.exercise.setValue('Linked exer' as unknown as ExerciseTitle);
        component.editForm.controls.exercise.markAsDirty();
        component.editForm.controls.exercise.setValue(exercise);

        component.save();

        expect(component.editForm.controls.exercise.hasError('exerciseNotSelected')).toBe(false);
        expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ exerciseId: exercise.id }) });
    });

    it('should allow editing and saving a presentation with the smallest valid max points', () => {
        fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, maxPoints: 0.001 });
        fixture.detectChanges();
        expect(component.editForm.valid).toBe(true);
        component.editForm.controls.title.setValue('Updated presentation');

        component.save();

        expect(saved).toHaveBeenCalledOnce();
        expect(saved).toHaveBeenCalledWith({
            presentationAssessment: expect.objectContaining({ id: presentationAssessment.id, title: 'Updated presentation', maxPoints: 0.001 }),
        });
    });

    it('should reject invalid max points', () => {
        component.editForm.controls.maxPoints.setValue(0);
        expect(component.editForm.controls.maxPoints.hasError('min')).toBe(true);

        component.editForm.controls.maxPoints.setValue(-1);
        expect(component.editForm.controls.maxPoints.hasError('min')).toBe(true);

        component.editForm.controls.maxPoints.setValue(0.0009);
        expect(component.editForm.controls.maxPoints.hasError('min')).toBe(true);

        component.editForm.controls.maxPoints.setValue(0.001);
        expect(component.editForm.controls.maxPoints.valid).toBe(true);

        component.editForm.controls.maxPoints.setValue(1.5);
        expect(component.editForm.controls.maxPoints.valid).toBe(true);

        component.editForm.controls.maxPoints.setValue(0.01);
        expect(component.editForm.controls.maxPoints.valid).toBe(true);

        component.editForm.controls.maxPoints.setValue(10001);
        expect(component.editForm.controls.maxPoints.hasError('max')).toBe(true);
    });

    it('should reject a blank presentation title', () => {
        component.editForm.controls.title.setValue('   ');

        component.save();

        expect(component.editForm.controls.title.hasError('required')).toBe(true);
        expect(saved).not.toHaveBeenCalled();
    });

    it('should close with the parent presentation data on save', () => {
        component.editForm.patchValue({ title: 'Updated presentation', exercise });

        component.save();

        expect(saved).toHaveBeenCalledWith(
            expect.objectContaining({
                presentationAssessment: expect.objectContaining({
                    id: presentationAssessment.id,
                    title: 'Updated presentation',
                    exerciseId: exercise.id,
                }),
            }),
        );
    });

    it('should close without result on cancel', () => {
        component.cancel();

        expect(cancelled).toHaveBeenCalledOnce();
    });

    it('should request deletion for an existing presentation', () => {
        component.requestDelete();

        expect(deleteRequested).toHaveBeenCalledWith(presentationAssessment);
    });

    it.each([
        { case: 'an instructor editing a presentation', canDelete: true, isNew: false, shown: true },
        { case: 'an editor editing a presentation', canDelete: false, isNew: false, shown: false },
        { case: 'a new presentation', canDelete: true, isNew: true, shown: false },
    ])('should show the delete button only for $case: $shown', ({ canDelete, isNew, shown }) => {
        fixture.componentRef.setInput('canDelete', canDelete);
        fixture.componentRef.setInput('presentationAssessment', isNew ? undefined : presentationAssessment);
        fixture.detectChanges();

        expect(!!byTestId('delete-presentation-button')).toBe(shown);
    });

    it('should show the title error only after the blank title was touched', () => {
        component.editForm.controls.title.setValue('   ');
        fixture.detectChanges();
        expect(byTestId('presentation-title-error')).toBeNull();

        component.editForm.controls.title.markAsTouched();
        fixture.detectChanges();

        expect(byTestId('presentation-title-error')).not.toBeNull();
    });

    it('should show the max points and take typed points with a comma and up to three fraction digits', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const input = fixture.nativeElement.querySelector('input#presentation-assessment-max-points') as HTMLInputElement;
        expect(input.value).toBe('20');

        typeIntoInputNumber(input, '1,125');
        expect(component.editForm.controls.maxPoints.value).toBe(1.125);
        expect(component.editForm.controls.maxPoints.valid).toBe(true);

        // 0.0009 is below the minimum of 0.001, which the field applies when it is left
        typeIntoInputNumber(input, '0.0009');
        expect(component.editForm.controls.maxPoints.value).toBe(0.001);
    });

    it('should show the max points error for points above the upper bound', () => {
        component.editForm.controls.maxPoints.setValue(10001);
        component.editForm.controls.maxPoints.markAsTouched();
        fixture.detectChanges();

        expect(byTestId('presentation-max-points-error')).not.toBeNull();
    });

    it('should explain that typed exercise text must be selected from the list', () => {
        component.editForm.controls.exercise.setValue('Linked exer' as unknown as ExerciseTitle);

        component.save();
        fixture.detectChanges();

        expect(byTestId('presentation-exercise-error')).not.toBeNull();
    });

    it('should save when the form is submitted', () => {
        component.editForm.controls.title.setValue('Submitted title');
        fixture.detectChanges();

        fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));

        expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ title: 'Submitted title' }) });
    });

    function clickButton(testId: string): void {
        (byTestId(testId)!.querySelector('button') as HTMLButtonElement).click();
        fixture.detectChanges();
    }

    describe('creating a presentation', () => {
        beforeEach(() => {
            fixture.componentRef.setInput('presentationAssessment', undefined);
            fixture.detectChanges();
        });

        function steps(): HTMLElement[] {
            return Array.from(byTestId('presentation-steps')!.querySelectorAll('li'));
        }

        function enterText(selector: string, value: string): void {
            const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
            input.value = value;
            input.dispatchEvent(new Event('input'));
            fixture.detectChanges();
        }

        function chooseMethod(gradingMethod: PresentationGradingMethod): void {
            (byTestId(`grading-method-${gradingMethod}`)!.querySelector('input') as HTMLInputElement).click();
            fixture.detectChanges();
        }

        function goToConfiguration(gradingMethod: PresentationGradingMethod): void {
            enterText('input#presentation-assessment-title', 'Final presentation');
            clickButton('continue-presentation-button');
            chooseMethod(gradingMethod);
            clickButton('continue-presentation-button');
        }

        it('should guide through details, method and configuration and skip the configuration for pass/fail', () => {
            expect(steps()).toHaveLength(3);
            expect((byTestId('continue-presentation-button')!.querySelector('button') as HTMLButtonElement).disabled).toBe(true);

            enterText('input#presentation-assessment-title', 'Final presentation');
            clickButton('continue-presentation-button');

            expect(steps()[0].querySelector('fa-icon')).not.toBeNull();
            expect(steps()[1].getAttribute('aria-current')).toBe('step');
            expect(Object.values(PresentationGradingMethod).map((method) => byTestId(`grading-method-${method}`)?.querySelector('fa-icon'))).not.toContain(null);

            chooseMethod(PresentationGradingMethod.PASS_FAIL);

            expect(steps()).toHaveLength(2);
            expect(byTestId('save-presentation-button')).not.toBeNull();
            expect(byTestId('continue-presentation-button')).toBeNull();
        });

        it('should not continue without a grading method', () => {
            enterText('input#presentation-assessment-title', 'Final presentation');
            clickButton('continue-presentation-button');

            expect((byTestId('continue-presentation-button')!.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
        });

        it('should stay on the details and show what is missing when they are sent incomplete', () => {
            // Pressing Enter sends the form even while the continue button is disabled
            fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
            fixture.detectChanges();

            expect(byTestId('presentation-title-error')).not.toBeNull();
            expect(steps()[0].getAttribute('aria-current')).toBe('step');
        });

        it('should stay on the details and explain a description that is too long', () => {
            enterText('#presentation-assessment-title', 'Final presentation');
            enterText('#presentation-assessment-description', 'x'.repeat(1001));

            expect((byTestId('continue-presentation-button')!.querySelector('button') as HTMLButtonElement).disabled).toBe(true);
            expect(byTestId('presentation-description-error')).not.toBeNull();
            fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
            fixture.detectChanges();
            expect(steps()[0].getAttribute('aria-current')).toBe('step');
        });

        it('should stay on the method and ask for one when it is sent without', () => {
            enterText('input#presentation-assessment-title', 'Final presentation');
            clickButton('continue-presentation-button');

            fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
            fixture.detectChanges();

            expect(byTestId('presentation-grading-method-error')).not.toBeNull();
            expect(steps()[1].getAttribute('aria-current')).toBe('step');
        });

        it('should lock the details and the rubric while saving and unlock them afterwards', () => {
            goToConfiguration(PresentationGradingMethod.RUBRIC);
            clickButton('rubric-add-first-criterion');

            fixture.componentRef.setInput('isSaving', true);
            fixture.detectChanges();
            expect(component.editForm.disabled).toBe(true);
            expect((fixture.nativeElement.querySelector('input#rubric-criterion-name-0') as HTMLInputElement).disabled).toBe(true);

            fixture.componentRef.setInput('isSaving', false);
            fixture.detectChanges();
            expect(component.editForm.controls.title.enabled).toBe(true);
            // Only a presentation graded by score has max points
            expect(component.editForm.controls.maxPoints.disabled).toBe(true);
        });

        it('should go back to the method from the configuration', () => {
            goToConfiguration(PresentationGradingMethod.SCORE);
            expect(fixture.nativeElement.querySelector('input#presentation-assessment-max-points')).not.toBeNull();

            clickButton('back-presentation-button');

            expect(byTestId('grading-method-SCORE')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('input#presentation-assessment-max-points')).toBeNull();
        });

        it('should widen the dialog only while the rubric is configured', async () => {
            const wide: boolean[] = [];
            component.wideChanged.subscribe((value) => wide.push(value));

            goToConfiguration(PresentationGradingMethod.RUBRIC);
            await fixture.whenStable();
            expect(wide.at(-1)).toBe(true);

            clickButton('back-presentation-button');
            await fixture.whenStable();
            expect(wide.at(-1)).toBe(false);
        });

        it('should save a presentation graded by pass/fail without points and rubric', () => {
            enterText('input#presentation-assessment-title', 'Final presentation');
            clickButton('continue-presentation-button');
            chooseMethod(PresentationGradingMethod.PASS_FAIL);

            clickButton('save-presentation-button');

            expect(saved).toHaveBeenCalledWith({
                presentationAssessment: expect.objectContaining({
                    title: 'Final presentation',
                    gradingMethod: PresentationGradingMethod.PASS_FAIL,
                    maxPoints: undefined,
                    rubricCriteria: undefined,
                }),
            });
        });

        it('should save a presentation graded by score with its maximum and without rubric', () => {
            goToConfiguration(PresentationGradingMethod.SCORE);
            typeIntoInputNumber(fixture.nativeElement.querySelector('input#presentation-assessment-max-points'), '15');
            fixture.detectChanges();

            clickButton('save-presentation-button');

            expect(saved).toHaveBeenCalledWith({
                presentationAssessment: expect.objectContaining({ gradingMethod: PresentationGradingMethod.SCORE, maxPoints: 15, rubricCriteria: undefined }),
            });
        });

        it('should save a presentation graded by rubric with its criteria and without points', () => {
            goToConfiguration(PresentationGradingMethod.RUBRIC);
            clickButton('rubric-add-first-criterion');
            enterText('input#rubric-criterion-name-0', 'Content');

            clickButton('save-presentation-button');

            expect(saved).toHaveBeenCalledWith({
                presentationAssessment: expect.objectContaining({
                    gradingMethod: PresentationGradingMethod.RUBRIC,
                    maxPoints: undefined,
                    rubricCriteria: [expect.objectContaining({ name: 'Content', weight: 100, scale: PresentationRubricScale.FIVE_POINT })],
                }),
            });
        });

        it('should show the errors of an incomplete rubric and not save it', () => {
            goToConfiguration(PresentationGradingMethod.RUBRIC);
            expect(byTestId('rubric-empty-error')).toBeNull();

            clickButton('save-presentation-button');

            expect(saved).not.toHaveBeenCalled();
            expect(byTestId('rubric-empty-error')).not.toBeNull();
        });
    });

    describe('editing a presentation', () => {
        it('should show the grading method without steps and without letting it change', () => {
            expect(byTestId('presentation-grading-method')!.textContent).toContain('artemisApp.presentationAssessment.gradingMethod.SCORE');
            expect(byTestId('presentation-steps')).toBeNull();
            expect(byTestId('grading-method-SCORE')).toBeNull();
            expect(component.editForm.controls.gradingMethod.disabled).toBe(true);
        });

        it('should neither ask for nor send max points of a presentation graded by pass/fail', () => {
            fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, gradingMethod: PresentationGradingMethod.PASS_FAIL, maxPoints: undefined });
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('input#presentation-assessment-max-points')).toBeNull();

            clickButton('save-presentation-button');

            expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ gradingMethod: PresentationGradingMethod.PASS_FAIL, maxPoints: undefined }) });
        });

        it('should not send the rubric, which has its own dialog', () => {
            fixture.componentRef.setInput('presentationAssessment', { ...presentationAssessment, gradingMethod: PresentationGradingMethod.RUBRIC, maxPoints: undefined });
            fixture.detectChanges();

            clickButton('save-presentation-button');

            expect(saved).toHaveBeenCalledWith({ presentationAssessment: expect.objectContaining({ gradingMethod: PresentationGradingMethod.RUBRIC, rubricCriteria: undefined }) });
        });
    });
});

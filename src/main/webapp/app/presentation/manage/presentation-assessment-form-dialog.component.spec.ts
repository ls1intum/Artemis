import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { Mock, beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentFormDialogComponent, PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { ExerciseTitle } from 'app/exercise/shared/entities/exercise/exercise-title.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

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
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: DialogService, useClass: MockDialogService },
            ],
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
});

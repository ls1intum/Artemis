import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentFormDialogComponent } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('PresentationAssessmentFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentFormDialogComponent>;
    let component: PresentationAssessmentFormDialogComponent;
    let saved: ReturnType<typeof vi.fn>;
    let cancelled: ReturnType<typeof vi.fn>;
    let deleteRequested: ReturnType<typeof vi.fn>;

    const courseId = 1;
    const presentationAssessment: PresentationAssessment = {
        id: 42,
        title: 'Final presentation',
        description: 'Final project presentation',
        maxPoints: 20,
        courseId,
    };
    const exercise = { id: 7, title: 'Linked exercise' } as Exercise;

    beforeEach(async () => {
        saved = vi.fn();
        cancelled = vi.fn();
        deleteRequested = vi.fn();

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentFormDialogComponent],
        })
            .overrideComponent(PresentationAssessmentFormDialogComponent, {
                set: { template: '' },
            })
            .compileComponents();

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

    it('should initialize the presentation fields', () => {
        expect(component.editForm.controls.title.value).toBe('Final presentation');
        expect(component.exercises()).toEqual([exercise]);
    });

    it('should reject non-integer and excessive max points', () => {
        component.editForm.controls.maxPoints.setValue(1.5);
        expect(component.editForm.controls.maxPoints.hasError('wholeNumber')).toBe(true);

        component.editForm.controls.maxPoints.setValue(10001);
        expect(component.editForm.controls.maxPoints.hasError('max')).toBe(true);
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
});

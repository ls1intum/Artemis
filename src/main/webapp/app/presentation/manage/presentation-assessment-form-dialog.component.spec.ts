import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentFormDialogComponent } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('PresentationAssessmentFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentFormDialogComponent>;
    let component: PresentationAssessmentFormDialogComponent;
    let dialogRef: { close: ReturnType<typeof vi.fn> };

    const courseId = 1;
    const course = { id: courseId, title: 'Test Course', isAtLeastInstructor: true } as Course;
    const presentationAssessment: PresentationAssessment = {
        id: 42,
        title: 'Final presentation',
        description: 'Final project presentation',
        maxPoints: 20,
        courseId,
    };
    const exercise = { id: 7, title: 'Linked exercise' } as Exercise;

    beforeEach(async () => {
        dialogRef = { close: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentFormDialogComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                {
                    provide: DynamicDialogConfig,
                    useValue: {
                        data: {
                            courseId,
                            course,
                            presentationAssessment,
                            exercises: [exercise],
                        },
                    },
                },
            ],
        })
            .overrideComponent(PresentationAssessmentFormDialogComponent, {
                set: { template: '' },
            })
            .compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentFormDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should initialize the presentation fields', () => {
        expect(component.editForm.controls.title.value).toBe('Final presentation');
        expect(component.exercises()).toEqual([exercise]);
    });

    it('should close with the parent presentation data on save', () => {
        component.editForm.patchValue({ title: 'Updated presentation', exerciseId: exercise.id });

        component.save();

        expect(dialogRef.close).toHaveBeenCalledWith(
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

        expect(dialogRef.close).toHaveBeenCalledWith();
    });
});

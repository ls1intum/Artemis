import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentFormDialogComponent } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { User } from 'app/account/user/user.model';

describe('PresentationAssessmentFormDialogComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentFormDialogComponent>;
    let component: PresentationAssessmentFormDialogComponent;

    const courseId = 1;
    const course = { id: courseId, title: 'Test Course', isAtLeastInstructor: true } as Course;
    const presentationDate = dayjs('2026-07-20T10:00:00+02:00');
    const presentationAssessment: PresentationAssessment = {
        id: 42,
        title: 'Final presentation',
        description: 'Final project presentation',
        maxPoints: 20,
        resultPoints: 18,
        presentationDate,
        courseId,
    };
    const student = { id: 1, login: 'student1' } as User;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentFormDialogComponent],
            providers: [{ provide: CourseManagementService, useValue: { searchStudents: vi.fn().mockReturnValue(of(new HttpResponse({ body: [student] }))) } }],
        })
            .overrideComponent(PresentationAssessmentFormDialogComponent, {
                set: { template: '' },
            })
            .compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentFormDialogComponent);
        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('course', course);
        fixture.componentRef.setInput('presentationAssessment', presentationAssessment);
        fixture.componentRef.setInput('initialAssignedStudents', [student]);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should initialize the form and assigned students from dialog data', () => {
        expect(component.editForm.controls.title.value).toBe('Final presentation');
        expect(component.editForm.controls.resultPoints.value).toBe(18);
        expect(component.assignedStudents()).toEqual([student]);
    });

    it('should reject result points above max points', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.editForm.patchValue({ maxPoints: 20, resultPoints: 21 });

        component.save();

        expect(component.editForm.hasError('resultPointsExceedMaxPoints')).toBe(true);
        expect(savedSpy).not.toHaveBeenCalled();
    });

    it('should accept missing result points', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.editForm.controls.resultPoints.setValue(undefined);

        component.save();

        expect(component.editForm.controls.resultPoints.valid).toBe(true);
        expect(savedSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                presentationAssessment: expect.objectContaining({
                    resultPoints: undefined,
                }),
            }),
        );
    });

    it('should reject non-numeric result points', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.editForm.controls.resultPoints.setValue('not a number');

        component.save();

        expect(component.editForm.controls.resultPoints.hasError('numberRequired')).toBe(true);
        expect(savedSpy).not.toHaveBeenCalled();
    });

    it('should reject non-numeric max points', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.editForm.controls.maxPoints.setValue('not a number');

        component.save();

        expect(component.editForm.controls.maxPoints.hasError('numberRequired')).toBe(true);
        expect(savedSpy).not.toHaveBeenCalled();
    });

    it('should reject overly long title and description', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.editForm.controls.title.setValue('a'.repeat(256));
        component.editForm.controls.description.setValue('a'.repeat(1001));

        component.save();

        expect(component.editForm.controls.title.hasError('maxlength')).toBe(true);
        expect(component.editForm.controls.description.hasError('maxlength')).toBe(true);
        expect(savedSpy).not.toHaveBeenCalled();
    });

    it('should save decimal comma result points as number', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.editForm.controls.resultPoints.setValue('18,5');

        component.save();

        expect(savedSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                presentationAssessment: expect.objectContaining({
                    resultPoints: 18.5,
                }),
            }),
        );
    });

    it('should reject invalid date picker input', () => {
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        Object.defineProperty(component, 'datePicker', { value: () => ({ dateInput: { valid: false } }), configurable: true });

        component.save();

        expect(savedSpy).not.toHaveBeenCalled();
    });

    it('should update the student section title when the title changes', () => {
        component.editForm.controls.title.setValue('Live title');

        expect(component.studentSectionTitle()).toBe('Live title');
    });

    it('should close with form value and staged assigned students on save', () => {
        const savedSpy = vi.fn();
        const stagedStudent = { id: 2, login: 'student2' } as User;
        component.saved.subscribe(savedSpy);
        component.assignedStudents.set([stagedStudent]);
        component.editForm.patchValue({ title: 'Updated presentation', resultPoints: 19 });

        component.save();

        expect(savedSpy).toHaveBeenCalledWith(
            expect.objectContaining({
                presentationAssessment: expect.objectContaining({
                    id: presentationAssessment.id,
                    title: 'Updated presentation',
                    resultPoints: 19,
                }),
                assignedStudents: [stagedStudent],
                originalAssignedStudents: [student],
            }),
        );
    });

    it('should close without result on cancel', () => {
        const cancelledSpy = vi.fn();
        component.cancelled.subscribe(cancelledSpy);

        component.cancel();

        expect(cancelledSpy).toHaveBeenCalledOnce();
    });

    it('should disable editable form controls and ignore save and cancel while saving', () => {
        const savedSpy = vi.fn();
        const cancelledSpy = vi.fn();
        component.saved.subscribe(savedSpy);
        component.cancelled.subscribe(cancelledSpy);

        fixture.componentRef.setInput('isSaving', true);
        fixture.detectChanges();

        expect(component.editForm.disabled).toBe(true);

        component.save();
        component.cancel();

        expect(savedSpy).not.toHaveBeenCalled();
        expect(cancelledSpy).not.toHaveBeenCalled();
    });
});

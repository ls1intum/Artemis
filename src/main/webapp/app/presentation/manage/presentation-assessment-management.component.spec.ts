import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { DialogService } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { PresentationAssessmentFormDialogResult } from 'app/presentation/manage/presentation-assessment-form-dialog.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';

describe('PresentationAssessmentManagementComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentManagementComponent>;
    let component: PresentationAssessmentManagementComponent;
    let presentationAssessmentService: {
        findAllByCourseId: ReturnType<typeof vi.fn>;
        create: ReturnType<typeof vi.fn>;
        update: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
        createInstance: ReturnType<typeof vi.fn>;
        updateInstance: ReturnType<typeof vi.fn>;
        deleteInstance: ReturnType<typeof vi.fn>;
    };
    let alertService: { success: ReturnType<typeof vi.fn>; addAlert: ReturnType<typeof vi.fn> };
    let dialogCloseSubject: Subject<PresentationAssessmentFormDialogResult | undefined>;
    let dialogService: { open: ReturnType<typeof vi.fn> };

    const courseId = 1;
    const course = { id: courseId, title: 'Test Course', isAtLeastInstructor: true } as Course;
    const presentationDate = dayjs('2026-07-20T10:00:00+02:00');
    const presentationAssessment: PresentationAssessment = {
        id: 42,
        title: 'Final presentation',
        description: 'Final project presentation',
        maxPoints: 20,
        courseId,
        instances: [{ id: 11, presentationDate, resultPoints: 18, studentLogins: ['student1', 'student2'] }],
    };

    beforeEach(async () => {
        dialogCloseSubject = new Subject<PresentationAssessmentFormDialogResult | undefined>();
        presentationAssessmentService = {
            findAllByCourseId: vi.fn().mockReturnValue(of(new HttpResponse({ body: [presentationAssessment] }))),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            createInstance: vi.fn(),
            updateInstance: vi.fn(),
            deleteInstance: vi.fn(),
        };
        alertService = { success: vi.fn(), addAlert: vi.fn() };
        dialogService = { open: vi.fn().mockReturnValue({ onClose: dialogCloseSubject.asObservable() }) };

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentManagementComponent],
            providers: [
                { provide: PresentationAssessmentService, useValue: presentationAssessmentService },
                { provide: AlertService, useValue: alertService },
                { provide: DialogService, useValue: dialogService },
                { provide: TranslateService, useValue: { instant: (key: string) => key } },
                {
                    provide: CourseManagementService,
                    useValue: { findWithExercises: vi.fn().mockReturnValue(of(new HttpResponse({ body: { ...course, exercises: [] } }))) },
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId }) },
                        parent: {
                            snapshot: { paramMap: convertToParamMap({ courseId }) },
                            data: of({ course }),
                        },
                    },
                },
            ],
        })
            .overrideComponent(PresentationAssessmentManagementComponent, {
                set: { template: '' },
            })
            .compileComponents();

        fixture = TestBed.createComponent(PresentationAssessmentManagementComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should load presentation assessments for the course', () => {
        expect(component.courseId()).toBe(courseId);
        expect(component.course()).toBe(course);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledWith(courseId);
        expect(component.presentationAssessments()).toEqual([presentationAssessment]);
    });

    it('should switch views and filter the student overview', () => {
        component.setViewMode('students');
        component.updateStudentSearch('STUDENT2');

        expect(component.viewMode()).toBe('students');
        expect(component.filteredStudentRows()).toEqual([{ studentLogin: 'student2', presentationAssessment, instance: presentationAssessment.instances![0] }]);
    });

    it('should open the create dialog without persisting on cancel', () => {
        component.startCreate();
        dialogCloseSubject.next(undefined);

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.create).not.toHaveBeenCalled();
    });

    it('should open the parent edit dialog without instance data', () => {
        component.startEdit(presentationAssessment);

        expect(dialogService.open).toHaveBeenCalledWith(
            expect.anything(),
            expect.objectContaining({
                data: expect.objectContaining({
                    courseId,
                    course,
                    presentationAssessment,
                }),
            }),
        );
    });

    it('should create a parent presentation after dialog save', () => {
        const savedAssessment: PresentationAssessment = { ...presentationAssessment, id: 43, title: 'New presentation' };
        presentationAssessmentService.create.mockReturnValue(of(new HttpResponse({ body: savedAssessment })));
        component.startCreate();

        dialogCloseSubject.next({
            presentationAssessment: { title: 'New presentation', description: 'Description', maxPoints: 25, courseId },
        });

        expect(presentationAssessmentService.create).toHaveBeenCalledWith(
            courseId,
            expect.objectContaining({
                title: 'New presentation',
                description: 'Description',
                maxPoints: 25,
                courseId,
            }),
        );
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(2);
    });

    it('should update parent presentation data after dialog save', () => {
        presentationAssessmentService.update.mockReturnValue(of(new HttpResponse({ body: presentationAssessment })));
        component.startEdit(presentationAssessment);

        dialogCloseSubject.next({
            presentationAssessment: { ...presentationAssessment, title: 'Updated presentation' },
        });

        expect(presentationAssessmentService.update).toHaveBeenCalledWith(
            courseId,
            expect.objectContaining({
                id: presentationAssessment.id,
                title: 'Updated presentation',
            }),
        );
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.updated');
    });

    it('should not reload presentations when create fails', () => {
        presentationAssessmentService.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        component.startCreate();

        dialogCloseSubject.next({
            presentationAssessment: { title: 'New presentation', maxPoints: 25, courseId },
        });

        expect(presentationAssessmentService.create).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(alertService.success).not.toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
    });

    it('should delete a presentation assessment from the table', () => {
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));

        component.deletePresentationAssessment(presentationAssessment);

        expect(presentationAssessmentService.delete).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.presentationAssessments()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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
        findCourseStudents: ReturnType<typeof vi.fn>;
    };
    let alertService: { success: ReturnType<typeof vi.fn>; addAlert: ReturnType<typeof vi.fn> };

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
        presentationAssessmentService = {
            findAllByCourseId: vi.fn().mockReturnValue(of(new HttpResponse({ body: [presentationAssessment] }))),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            createInstance: vi.fn(),
            updateInstance: vi.fn(),
            deleteInstance: vi.fn(),
            findCourseStudents: vi.fn().mockReturnValue(
                of(
                    new HttpResponse({
                        body: [
                            { login: 'student1', name: 'Student One' },
                            { login: 'student2', name: 'Student Two' },
                        ],
                    }),
                ),
            ),
        };
        alertService = { success: vi.fn(), addAlert: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentManagementComponent],
            providers: [
                { provide: PresentationAssessmentService, useValue: presentationAssessmentService },
                { provide: AlertService, useValue: alertService },
                { provide: TranslateService, useClass: MockTranslateService },
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
        expect(component.courseStudents()).toHaveLength(2);
    });

    it('should switch views and filter the student overview', () => {
        component.setViewMode('students');
        component.updateStudentSearch('STUDENT2');

        expect(component.viewMode()).toBe('students');
        expect(component.filteredStudentRows()).toEqual([{ studentLogin: 'student2', presentationAssessment, instance: presentationAssessment.instances![0] }]);
    });

    it('should only consider an assigned numeric score as assessed', () => {
        expect(component.hasResultPoints(undefined)).toBe(false);
        expect(component.hasResultPoints(null)).toBe(false);
        expect(component.hasResultPoints(0)).toBe(true);
    });

    it('should only show students with an instance in the selected presentation', () => {
        component.presentationAssessments.set([{ ...presentationAssessment, instances: [] }]);

        expect(component.selectedPresentationStudentRows()).toEqual([]);

        component.presentationAssessments.set([
            {
                ...presentationAssessment,
                instances: [{ id: 12, presentationDate, studentLogins: ['student2'] }],
            },
        ]);

        expect(component.selectedPresentationStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);
    });

    it('should expose the student overview and presentations grouped by exercise linkage in the sidebar', () => {
        const linkedPresentation = { ...presentationAssessment, id: 43, title: 'Linked presentation', exerciseId: 7, exerciseTitle: 'Exercise 1' };
        component.presentationAssessments.set([linkedPresentation, presentationAssessment]);

        const sidebarData = component.sidebarData();

        expect(sidebarData.pinnedData?.map((item) => item.id)).toEqual(['overview']);
        expect(sidebarData.groupedData?.standalone.entityData.map((item) => item.id)).toEqual([42]);
        expect(sidebarData.groupedData?.linkedToExercise.entityData.map((item) => item.id)).toEqual([43]);
        expect(sidebarData.groupedData?.linkedToExercise.entityData[0].subtitleLeft).toBe('Exercise 1');
    });

    it('should use the sidebar to switch between the overall overview and a presentation', () => {
        component.onSidebarItemSelected('overview');
        expect(component.viewMode()).toBe('students');

        component.onSidebarItemSelected(42);
        expect(component.viewMode()).toBe('presentations');
        expect(component.selectedPresentationId()).toBe(42);
    });

    it('should create the course management route for the linked exercise', () => {
        component.exercises.set([{ id: 7, type: ExerciseType.TEXT } as Exercise]);

        expect(component.getLinkedExerciseRoute({ ...presentationAssessment, exerciseId: 7 })).toEqual(['/course-management', courseId, 'text-exercises', 7]);
    });

    it('should open the create dialog without persisting on cancel', () => {
        component.startCreate();
        component.handlePresentationDialogCancel();

        expect(component.presentationDialogVisible()).toBe(false);
        expect(presentationAssessmentService.create).not.toHaveBeenCalled();
    });

    it('should open the parent edit dialog without instance data', () => {
        component.startEdit(presentationAssessment);

        expect(component.presentationDialogVisible()).toBe(true);
        expect(component.dialogPresentationAssessment()).toBe(presentationAssessment);
    });

    it('should create a separate instance for every selected student', () => {
        presentationAssessmentService.createInstance.mockReturnValue(of(new HttpResponse({ body: {} })));
        component.startCreateInstance(presentationAssessment);

        component.handleInstanceDialogSave({ presentationDate, resultPoints: 10, studentLogins: ['student1', 'student2'] });

        expect(presentationAssessmentService.createInstance).toHaveBeenCalledTimes(2);
        expect(presentationAssessmentService.createInstance).toHaveBeenNthCalledWith(
            1,
            courseId,
            presentationAssessment.id,
            expect.objectContaining({ studentLogins: ['student1'] }),
        );
        expect(presentationAssessmentService.createInstance).toHaveBeenNthCalledWith(
            2,
            courseId,
            presentationAssessment.id,
            expect.objectContaining({ studentLogins: ['student2'] }),
        );
    });

    it('should split a legacy shared instance before grading one student', () => {
        const sharedInstance = presentationAssessment.instances![0];
        presentationAssessmentService.updateInstance.mockReturnValue(of(new HttpResponse({ body: sharedInstance })));
        presentationAssessmentService.createInstance.mockReturnValue(of(new HttpResponse({ body: sharedInstance })));
        component.startEditInstance(presentationAssessment, sharedInstance, 'student1');

        component.handleInstanceDialogSave({ ...sharedInstance, resultPoints: 19, studentLogins: ['student1'] });

        expect(presentationAssessmentService.updateInstance).toHaveBeenCalledWith(
            courseId,
            presentationAssessment.id,
            expect.objectContaining({ id: sharedInstance.id, studentLogins: ['student2'], resultPoints: 18 }),
        );
        expect(presentationAssessmentService.createInstance).toHaveBeenCalledWith(
            courseId,
            presentationAssessment.id,
            expect.objectContaining({ id: undefined, studentLogins: ['student1'], resultPoints: 19 }),
        );
    });

    it('should only remove the selected student from a shared instance', () => {
        const sharedInstance = presentationAssessment.instances![0];
        presentationAssessmentService.updateInstance.mockReturnValue(of(new HttpResponse({ body: sharedInstance })));

        component.deleteInstance(presentationAssessment, sharedInstance, 'student1');

        expect(presentationAssessmentService.updateInstance).toHaveBeenCalledWith(courseId, presentationAssessment.id, expect.objectContaining({ studentLogins: ['student2'] }));
        expect(presentationAssessmentService.deleteInstance).not.toHaveBeenCalled();
    });

    it('should create a parent presentation after dialog save', () => {
        const savedAssessment: PresentationAssessment = { ...presentationAssessment, id: 43, title: 'New presentation' };
        presentationAssessmentService.create.mockReturnValue(of(new HttpResponse({ body: savedAssessment })));
        component.startCreate();

        component.handlePresentationDialogSave({
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

        component.handlePresentationDialogSave({
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

        component.handlePresentationDialogSave({
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

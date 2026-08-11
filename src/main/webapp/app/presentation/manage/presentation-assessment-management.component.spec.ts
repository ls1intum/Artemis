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
import { User } from 'app/account/user/user.model';

describe('PresentationAssessmentManagementComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentManagementComponent>;
    let component: PresentationAssessmentManagementComponent;
    let presentationAssessmentService: {
        findAllByCourseId: ReturnType<typeof vi.fn>;
        create: ReturnType<typeof vi.fn>;
        update: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
        findStudents: ReturnType<typeof vi.fn>;
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
        resultPoints: 18,
        presentationDate,
        courseId,
    };
    const student = { id: 1, login: 'student1' } as User;
    const secondStudent = { id: 2, login: 'student2' } as User;

    beforeEach(async () => {
        presentationAssessmentService = {
            findAllByCourseId: vi.fn().mockReturnValue(of(new HttpResponse({ body: [presentationAssessment] }))),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            findStudents: vi.fn().mockReturnValue(of(new HttpResponse({ body: [student] }))),
        };
        alertService = { success: vi.fn(), addAlert: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentManagementComponent],
            providers: [
                { provide: PresentationAssessmentService, useValue: presentationAssessmentService },
                { provide: AlertService, useValue: alertService },
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

    it('should open the create dialog without persisting on cancel', () => {
        component.startCreate();
        expect(component.dialogVisible()).toBeTruthy();

        component.handleDialogCancel();

        expect(component.dialogVisible()).toBeFalsy();
        expect(presentationAssessmentService.create).not.toHaveBeenCalled();
    });

    it('should open the edit dialog with assigned students', () => {
        component.startEdit(presentationAssessment);

        expect(presentationAssessmentService.findStudents).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.dialogVisible()).toBeTruthy();
        expect(component.dialogPresentationAssessment()).toBe(presentationAssessment);
        expect(component.dialogAssignedStudents()).toEqual([student]);
    });

    it('should ignore stale assigned student responses when another dialog is opened', () => {
        const firstStudentsResponse = new Subject<HttpResponse<User[]>>();
        presentationAssessmentService.findStudents.mockReturnValue(firstStudentsResponse);
        component.startEdit(presentationAssessment);

        component.startCreate();
        firstStudentsResponse.next(new HttpResponse({ body: [student] }));
        firstStudentsResponse.complete();

        expect(component.dialogVisible()).toBeTruthy();
        expect(component.dialogPresentationAssessment()).toBeUndefined();
        expect(component.dialogAssignedStudents()).toEqual([]);
        expect(component.isLoadingAssignedStudents()).toBeFalsy();
    });

    it('should ignore stale assigned student responses after cancelling the dialog', () => {
        const studentsResponse = new Subject<HttpResponse<User[]>>();
        presentationAssessmentService.findStudents.mockReturnValue(studentsResponse);
        component.startEdit(presentationAssessment);

        component.handleDialogCancel();
        studentsResponse.next(new HttpResponse({ body: [student] }));
        studentsResponse.complete();

        expect(component.dialogVisible()).toBeFalsy();
        expect(component.dialogPresentationAssessment()).toBeUndefined();
        expect(component.isLoadingAssignedStudents()).toBeFalsy();
    });

    it('should create presentation with selected students after dialog save', () => {
        const savedAssessment: PresentationAssessment = { ...presentationAssessment, id: 43, title: 'New presentation' };
        presentationAssessmentService.create.mockReturnValue(of(new HttpResponse({ body: savedAssessment })));
        component.startCreate();

        component.handleDialogSave({
            presentationAssessment: { title: 'New presentation', description: 'Description', maxPoints: 25, resultPoints: 22, presentationDate, courseId },
            assignedStudents: [student, student],
            originalAssignedStudents: [],
        });

        expect(presentationAssessmentService.create).toHaveBeenCalledWith(
            courseId,
            expect.objectContaining({
                title: 'New presentation',
                description: 'Description',
                maxPoints: 25,
                resultPoints: 22,
                presentationDate,
                courseId,
                studentLogins: ['student1'],
            }),
        );
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(2);
        expect(component.dialogVisible()).toBeFalsy();
    });

    it('should update presentation with the selected students after dialog save', () => {
        presentationAssessmentService.update.mockReturnValue(of(new HttpResponse({ body: presentationAssessment })));
        component.startEdit(presentationAssessment);

        component.handleDialogSave({
            presentationAssessment: { ...presentationAssessment, title: 'Updated presentation' },
            assignedStudents: [secondStudent],
            originalAssignedStudents: [student],
        });

        expect(presentationAssessmentService.update).toHaveBeenCalledWith(
            courseId,
            expect.objectContaining({
                id: presentationAssessment.id,
                title: 'Updated presentation',
                studentLogins: ['student2'],
            }),
        );
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.updated');
        expect(component.dialogVisible()).toBeFalsy();
    });

    it('should keep dialog open and ignore cancel while save is in progress', () => {
        const saveResponse = new Subject<HttpResponse<PresentationAssessment>>();
        presentationAssessmentService.create.mockReturnValue(saveResponse);
        component.startCreate();

        component.handleDialogSave({
            presentationAssessment: { title: 'New presentation', maxPoints: 25, courseId },
            assignedStudents: [],
            originalAssignedStudents: [],
        });
        component.handleDialogCancel();
        component.handleDialogVisibleChange(false);

        expect(component.dialogVisible()).toBeTruthy();
        expect(component.isSaving()).toBeTruthy();

        saveResponse.next(new HttpResponse({ body: presentationAssessment }));
        saveResponse.complete();

        expect(component.dialogVisible()).toBeFalsy();
    });

    it('should ignore create and edit attempts while save is in progress', () => {
        const saveResponse = new Subject<HttpResponse<PresentationAssessment>>();
        presentationAssessmentService.create.mockReturnValue(saveResponse);
        component.startCreate();

        component.handleDialogSave({
            presentationAssessment: { title: 'New presentation', maxPoints: 25, courseId },
            assignedStudents: [],
            originalAssignedStudents: [],
        });
        component.startEdit(presentationAssessment);
        component.startCreate();

        expect(presentationAssessmentService.findStudents).not.toHaveBeenCalled();
        expect(component.dialogPresentationAssessment()).toBeUndefined();

        saveResponse.next(new HttpResponse({ body: presentationAssessment }));
        saveResponse.complete();
    });

    it('should not reload presentations when create fails', () => {
        presentationAssessmentService.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        component.startCreate();

        component.handleDialogSave({
            presentationAssessment: { title: 'New presentation', maxPoints: 25, courseId },
            assignedStudents: [student],
            originalAssignedStudents: [],
        });

        expect(presentationAssessmentService.create).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(alertService.success).not.toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
        expect(component.dialogVisible()).toBeTruthy();
    });

    it('should delete a presentation assessment from the table', () => {
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));

        component.deletePresentationAssessment(presentationAssessment);

        expect(presentationAssessmentService.delete).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.presentationAssessments()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
    });
});

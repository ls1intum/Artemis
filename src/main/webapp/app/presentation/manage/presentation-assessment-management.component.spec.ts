import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TemplateRef } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { SortService } from 'app/foundation/service/sort.service';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { User } from 'app/account/user/user.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';

describe('PresentationAssessmentManagementComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentManagementComponent>;
    let component: PresentationAssessmentManagementComponent;
    let presentationAssessmentService: {
        findAllByCourseId: ReturnType<typeof vi.fn>;
        create: ReturnType<typeof vi.fn>;
        update: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
        findStudents: ReturnType<typeof vi.fn>;
        addStudent: ReturnType<typeof vi.fn>;
        removeStudent: ReturnType<typeof vi.fn>;
    };
    let alertService: { success: ReturnType<typeof vi.fn> };
    let modalRef: { close: ReturnType<typeof vi.fn>; dismiss: ReturnType<typeof vi.fn> };
    let modalService: { open: ReturnType<typeof vi.fn> };

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
        presentationAssessmentService = {
            findAllByCourseId: vi.fn().mockReturnValue(of(new HttpResponse({ body: [presentationAssessment] }))),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            findStudents: vi.fn().mockReturnValue(of(new HttpResponse({ body: [student] }))),
            addStudent: vi.fn().mockReturnValue(of(new HttpResponse<void>())),
            removeStudent: vi.fn().mockReturnValue(of(new HttpResponse<void>())),
        };
        alertService = { success: vi.fn() };
        modalRef = { close: vi.fn(), dismiss: vi.fn() };
        modalService = { open: vi.fn().mockReturnValue(modalRef) };

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentManagementComponent],
            providers: [
                { provide: PresentationAssessmentService, useValue: presentationAssessmentService },
                { provide: CourseManagementService, useValue: { searchStudents: vi.fn().mockReturnValue(of(new HttpResponse({ body: [student] }))) } },
                { provide: AlertService, useValue: alertService },
                {
                    provide: SortService,
                    useValue: {
                        sortByProperty: vi.fn((assessments: PresentationAssessment[], predicate: keyof PresentationAssessment) =>
                            assessments.sort((first, second) => String(first[predicate] ?? '').localeCompare(String(second[predicate] ?? ''))),
                        ),
                    },
                },
                { provide: NgbModal, useValue: modalService },
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

    it('should load and sort presentation assessments for the course', () => {
        expect(component.courseId()).toBe(courseId);
        expect(component.course()).toBe(course);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledWith(courseId);
        expect(component.presentationAssessments()).toEqual([presentationAssessment]);
    });

    it('should open create modal with default form values', () => {
        component.assignedStudents.set([student]);

        component.startCreate({} as TemplateRef<unknown>);

        expect(component.editedAssessment()).toBeUndefined();
        expect(component.assignedStudents()).toEqual([]);
        expect(component.showForm()).toBe(true);
        expect(component.editForm.controls.title.value).toBe('');
        expect(component.editForm.controls.maxPoints.value).toBe(20);
        expect(modalService.open).toHaveBeenCalledWith(expect.anything(), { size: 'xl', backdrop: 'static', scrollable: true });
    });

    it('should open edit modal and load assigned students', () => {
        component.startEdit(presentationAssessment, {} as TemplateRef<unknown>);

        expect(component.editedAssessment()).toBe(presentationAssessment);
        expect(component.editForm.controls.title.value).toBe('Final presentation');
        expect(component.editForm.controls.resultPoints.value).toBe(18);
        expect(presentationAssessmentService.findStudents).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.assignedStudents()).toEqual([student]);
        expect(component.isLoadingAssignedStudents()).toBe(false);
        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('should create presentation, persist unique assigned students, close the modal, and reload', () => {
        const savedAssessment: PresentationAssessment = { ...presentationAssessment, id: 43, title: 'New presentation' };
        presentationAssessmentService.create.mockReturnValue(of(new HttpResponse({ body: savedAssessment })));
        component.startCreate({} as TemplateRef<unknown>);
        component.assignedStudents.set([{ login: 'student1' } as User, { login: 'student1' } as User]);
        component.editForm.setValue({
            title: '  New presentation  ',
            description: 'Description',
            maxPoints: 25,
            resultPoints: 22,
            presentationDate,
        });

        component.save();

        expect(presentationAssessmentService.create).toHaveBeenCalledWith(
            courseId,
            expect.objectContaining({
                title: 'New presentation',
                description: 'Description',
                maxPoints: 25,
                resultPoints: 22,
                presentationDate,
                courseId,
            }),
        );
        expect(presentationAssessmentService.addStudent).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.addStudent).toHaveBeenCalledWith(courseId, savedAssessment.id, 'student1');
        expect(modalRef.close).toHaveBeenCalledOnce();
        expect(component.showForm()).toBe(false);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(2);
    });

    it('should update presentation and close the modal', () => {
        presentationAssessmentService.update.mockReturnValue(of(new HttpResponse({ body: presentationAssessment })));
        component.startEdit(presentationAssessment, {} as TemplateRef<unknown>);
        component.editForm.patchValue({ title: 'Updated presentation', resultPoints: 19 });

        component.save();

        expect(presentationAssessmentService.update).toHaveBeenCalledWith(
            courseId,
            expect.objectContaining({
                id: presentationAssessment.id,
                title: 'Updated presentation',
                resultPoints: 19,
            }),
        );
        expect(modalRef.close).toHaveBeenCalledOnce();
        expect(component.editedAssessment()).toBeUndefined();
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.updated');
    });

    it('should not save invalid form values', () => {
        component.startCreate({} as TemplateRef<unknown>);
        component.editForm.patchValue({ title: '', maxPoints: 20 });

        component.save();

        expect(component.editForm.controls.title.touched).toBe(true);
        expect(presentationAssessmentService.create).not.toHaveBeenCalled();
        expect(presentationAssessmentService.update).not.toHaveBeenCalled();
    });

    it('should delete a presentation assessment from the table', () => {
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));

        component.deletePresentationAssessment(presentationAssessment);

        expect(presentationAssessmentService.delete).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.presentationAssessments()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
    });

    it('should delegate student add and remove requests for edited presentations', () => {
        component.editedAssessment.set(presentationAssessment);

        component.addStudentToPresentation('student1').subscribe();
        component.removeStudentFromPresentation('student1').subscribe();

        expect(presentationAssessmentService.addStudent).toHaveBeenCalledWith(courseId, presentationAssessment.id, 'student1');
        expect(presentationAssessmentService.removeStudent).toHaveBeenCalledWith(courseId, presentationAssessment.id, 'student1');
    });
});

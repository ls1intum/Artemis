import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LangChangeEvent, TranslateService, TranslationChangeEvent } from '@ngx-translate/core';

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
        saveInstances: ReturnType<typeof vi.fn>;
        deleteInstance: ReturnType<typeof vi.fn>;
        findCourseStudents: ReturnType<typeof vi.fn>;
    };
    let alertService: { success: ReturnType<typeof vi.fn>; addAlert: ReturnType<typeof vi.fn> };
    let router: { navigate: ReturnType<typeof vi.fn> };
    let languageChanges: Subject<LangChangeEvent>;
    let translationChanges: Subject<TranslationChangeEvent>;

    const courseId = 1;
    const course = { id: courseId, title: 'Test Course', isAtLeastInstructor: true } as Course;
    const presentationDate = dayjs('2026-07-20T10:00:00+02:00');
    const presentationAssessment: PresentationAssessment = {
        id: 42,
        title: 'Final presentation',
        description: 'Final project presentation',
        maxPoints: 20,
        courseId,
        instances: [
            {
                id: 11,
                presentationDate,
                resultPoints: 18,
                studentLogins: ['student1', 'student2'],
                students: [
                    { login: 'student1', name: 'Student One' },
                    { login: 'student2', name: 'Student Two' },
                ],
            },
        ],
    };

    beforeEach(async () => {
        languageChanges = new Subject<LangChangeEvent>();
        translationChanges = new Subject<TranslationChangeEvent>();
        presentationAssessmentService = {
            findAllByCourseId: vi.fn().mockReturnValue(of(new HttpResponse({ body: [presentationAssessment] }))),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            createInstance: vi.fn(),
            updateInstance: vi.fn(),
            saveInstances: vi.fn(),
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
        router = { navigate: vi.fn().mockResolvedValue(true) };

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentManagementComponent],
            providers: [
                { provide: PresentationAssessmentService, useValue: presentationAssessmentService },
                { provide: AlertService, useValue: alertService },
                { provide: Router, useValue: router },
                { provide: TranslateService, useValue: { instant: (key: string) => key, onLangChange: languageChanges, onTranslationChange: translationChanges } },
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

    it.each(['language', 'translations'])('should refresh management labels on %s changes', (eventType) => {
        component.presentationFilterOptions();
        component.typeFilterOptions();
        component.sidebarData();
        vi.spyOn(TestBed.inject(TranslateService), 'instant').mockImplementation((key) => `updated:${key}`);
        if (eventType === 'language') {
            languageChanges.next({ lang: 'de', translations: {} });
        } else {
            translationChanges.next({ lang: 'de', translations: {} });
        }
        expect(component.presentationFilterOptions()[0].label).toBe('updated:artemisApp.presentationAssessment.filter.allPresentations');
        expect(component.typeFilterOptions()[0].label).toBe('updated:artemisApp.presentationAssessment.filter.allTypes');
        expect(component.sidebarData().pinnedData?.[0].title).toBe('updated:artemisApp.presentationAssessment.overallOverview');
    });

    it('should clamp the displayed page after the last page disappears', () => {
        component.overviewPageSize.set(1);
        component.overviewPage.set(1);
        expect(component.effectiveOverviewPage()).toBe(1);
        const remaining = { ...presentationAssessment, instances: [{ ...presentationAssessment.instances![0], studentLogins: ['student1'] }] };
        component.presentationAssessments.set([remaining]);
        expect(component.effectiveOverviewPage()).toBe(0);
        expect(component.paginatedStudentRows()).toHaveLength(1);
        component.presentationAssessments.set([]);
        expect(component.effectiveOverviewPage()).toBe(0);
        expect(component.paginatedStudentRows()).toEqual([]);
    });

    it('should load presentation assessments for the course', () => {
        expect(component.courseId()).toBe(courseId);
        expect(component.course()).toBe(course);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledWith(courseId);
        expect(component.presentationAssessments()).toEqual([presentationAssessment]);
        expect(component.courseStudents()).toHaveLength(2);
        expect(presentationAssessmentService.findCourseStudents).not.toHaveBeenCalled();
    });

    it('should expose the linked exercise to the student perspective switch while its presentation is selected', () => {
        const linkedPresentation = { ...presentationAssessment, id: 43, exerciseId: 7 };
        component.presentationAssessments.set([linkedPresentation]);
        router.navigate.mockClear();

        component.selectPresentation(linkedPresentation);
        fixture.detectChanges();

        expect(router.navigate).toHaveBeenCalledWith([], {
            relativeTo: expect.anything(),
            queryParams: { presentationExerciseId: 7 },
            queryParamsHandling: 'merge',
            replaceUrl: true,
        });

        router.navigate.mockClear();
        component.presentationAssessments.set([{ ...linkedPresentation, exerciseId: 8 }]);
        fixture.detectChanges();
        expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { presentationExerciseId: 8 } }));

        router.navigate.mockClear();
        component.setViewMode('students');
        fixture.detectChanges();
        expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { presentationExerciseId: null } }));
    });

    it('should switch views and filter the student overview', () => {
        component.setViewMode('students');
        component.updateStudentSearch('STUDENT2');

        expect(component.viewMode()).toBe('students');
        expect(component.filteredStudentRows()).toEqual([
            { studentLogin: 'student2', student: expect.objectContaining({ name: 'Student Two' }), presentationAssessment, instance: presentationAssessment.instances![0] },
        ]);
    });

    it('should only consider an assigned numeric score as assessed', () => {
        component.presentationAssessments.set([{ ...presentationAssessment, instances: [{ id: 11, presentationDate, resultPoints: undefined, studentLogins: ['student1'] }] }]);
        expect(component.filteredSelectedPresentationStudentRows()[0].assessed).toBe(false);

        component.presentationAssessments.set([{ ...presentationAssessment, instances: [{ id: 11, presentationDate, resultPoints: 0, studentLogins: ['student1'] }] }]);
        expect(component.filteredSelectedPresentationStudentRows()[0].assessed).toBe(true);
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
        expect(sidebarData.groupedData?.linkedToExercise.entityData[0].subtitleLeftIcon).toBeDefined();
        expect(sidebarData.groupedData?.linkedToExercise.entityData[0].icon).toBeUndefined();
        expect(sidebarData.groupedData?.standalone.entityData[0].icon).toBeUndefined();
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
        component.presentationAssessments.set([{ ...presentationAssessment, exerciseId: 7 }]);

        expect(component.selectedPresentationExerciseRoute()).toEqual(['/course-management', courseId, 'text-exercises', 7]);
    });

    it('should expose stable keys and expansion state in the row view models', () => {
        const row = component.filteredSelectedPresentationStudentRows()[0];
        component.toggleStudentRowDetails(row);

        expect(component.filteredSelectedPresentationStudentRows()[0]).toMatchObject({ rowKey: '11:student1', assessed: true, expanded: true });
        expect(component.paginatedStudentRows()[0]).toMatchObject({ rowKey: '11:student1', assessed: true, expanded: true });
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

    it('should save selected students through one atomic request', () => {
        presentationAssessmentService.saveInstances.mockReturnValue(of(new HttpResponse({ body: [] })));
        component.startCreateInstance(presentationAssessment);

        const result = { presentationDate, resultPoints: 10, studentLogins: ['student1', 'student2'] };
        component.handleInstanceDialogSave(result);

        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledWith(courseId, presentationAssessment.id, result);
    });

    it('should delegate splitting a legacy shared instance to the atomic endpoint', () => {
        const sharedInstance = presentationAssessment.instances![0];
        presentationAssessmentService.saveInstances.mockReturnValue(of(new HttpResponse({ body: [sharedInstance] })));
        component.startEditInstance(presentationAssessment, sharedInstance, 'student1');

        const result = { ...sharedInstance, resultPoints: 19, studentLogins: ['student1'] };
        component.handleInstanceDialogSave(result);

        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledWith(courseId, presentationAssessment.id, result);
    });

    it('should keep loaded student details when opening the instance edit dialog', () => {
        const sharedInstance = presentationAssessment.instances![0];

        component.startEditInstance(presentationAssessment, sharedInstance, 'student1');

        expect(component.dialogAssignedStudents()).toEqual([expect.objectContaining({ login: 'student1', name: 'Student One' })]);
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
        expect(component.presentationDialogVisible()).toBe(false);
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
        expect(component.presentationDialogVisible()).toBe(false);
    });

    it('should keep the parent dialog open when create fails', () => {
        presentationAssessmentService.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        component.startCreate();

        component.handlePresentationDialogSave({
            presentationAssessment: { title: 'New presentation', maxPoints: 25, courseId },
        });

        expect(presentationAssessmentService.create).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(alertService.success).not.toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
        expect(component.presentationDialogVisible()).toBe(true);
        expect(component.isSaving()).toBe(false);
    });

    it('should keep the instance dialog open when create instance fails', () => {
        presentationAssessmentService.saveInstances.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        component.startCreateInstance(presentationAssessment);

        component.handleInstanceDialogSave({ presentationDate, resultPoints: 10, studentLogins: ['student1'] });

        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(component.instanceDialogVisible()).toBe(true);
        expect(component.isSaving()).toBe(false);
    });

    it('should close the instance dialog after saving instances', () => {
        presentationAssessmentService.saveInstances.mockReturnValue(of(new HttpResponse({ body: [] })));
        component.startCreateInstance(presentationAssessment);

        component.handleInstanceDialogSave({ presentationDate, resultPoints: 10, studentLogins: ['student1'] });

        expect(component.instanceDialogVisible()).toBe(false);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(2);
    });

    it('should delete a presentation assessment from the table', () => {
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));

        component.deletePresentationAssessment(presentationAssessment);

        expect(presentationAssessmentService.delete).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.presentationAssessments()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
    });

    it('should reset selection and filter referencing a deleted assessment', () => {
        const remaining = { ...presentationAssessment, id: 99 };
        component.presentationAssessments.set([presentationAssessment, remaining]);
        component.selectedPresentationId.set(presentationAssessment.id);
        component.presentationFilter.set(presentationAssessment.id!);
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));

        component.deletePresentationAssessment(presentationAssessment);

        expect(component.selectedPresentationId()).toBe(99);
        expect(component.presentationFilter()).toBe('all');
    });

    it('should preserve selection and filter referencing another assessment', () => {
        const remaining = { ...presentationAssessment, id: 99 };
        component.presentationAssessments.set([presentationAssessment, remaining]);
        component.selectedPresentationId.set(99);
        component.presentationFilter.set(99);
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));

        component.deletePresentationAssessment(presentationAssessment);

        expect(component.selectedPresentationId()).toBe(99);
        expect(component.presentationFilter()).toBe(99);
    });
});

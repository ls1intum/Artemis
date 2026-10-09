import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import type { ParamMap } from '@angular/router';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { Course } from 'app/course/shared/entities/course.model';
import {
    PresentationAssessment,
    PresentationAssessmentInstance,
    PresentationAssessmentInstanceRequest,
    PresentationAssessmentInstancesCreate,
    PresentationAssessmentMode,
    PresentationAssessmentStudentRow,
} from 'app/presentation/shared/entities/presentation-assessment.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LangChangeEvent, TranslateService, TranslationChangeEvent } from '@ngx-translate/core';

describe('PresentationAssessmentManagementComponent', () => {
    let fixture: ComponentFixture<PresentationAssessmentManagementComponent>;
    let component: PresentationAssessmentManagementComponent;
    let presentationAssessmentService: {
        findAllByCourseId: ReturnType<typeof vi.fn>;
        findStudentRows: ReturnType<typeof vi.fn>;
        getStatistics: ReturnType<typeof vi.fn>;
        create: ReturnType<typeof vi.fn>;
        update: ReturnType<typeof vi.fn>;
        delete: ReturnType<typeof vi.fn>;
        updateInstance: ReturnType<typeof vi.fn>;
        saveInstances: ReturnType<typeof vi.fn>;
        deleteInstance: ReturnType<typeof vi.fn>;
    };
    let alertService: { success: ReturnType<typeof vi.fn>; addAlert: ReturnType<typeof vi.fn> };
    let router: { navigate: ReturnType<typeof vi.fn> };
    let routeParamMap: BehaviorSubject<ParamMap>;
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
    };
    const instances: PresentationAssessmentInstance[] = [
        {
            id: 11,
            presentationDate,
            resultPoints: 18,
            student: { login: 'student1', name: 'Student One' },
        },

        {
            id: 12,
            presentationDate,
            resultPoints: 18,
            student: { login: 'student2', name: 'Student Two' },
        },
    ];

    beforeEach(async () => {
        languageChanges = new Subject<LangChangeEvent>();
        translationChanges = new Subject<TranslationChangeEvent>();
        presentationAssessmentService = {
            findAllByCourseId: vi.fn().mockReturnValue(of(new HttpResponse({ body: [presentationAssessment] }))),
            getStatistics: vi.fn().mockReturnValue(of(new HttpResponse({ body: { totalCount: 2, assessedCount: 2 } }))),
            findStudentRows: vi.fn().mockReturnValue(
                of(
                    new HttpResponse({
                        body: instances.map((instance) => ({
                            presentationAssessment,
                            instance,
                        })),
                        headers: new HttpHeaders({ 'X-Total-Count': '2' }),
                    }),
                ),
            ),
            create: vi.fn(),
            update: vi.fn(),
            delete: vi.fn(),
            updateInstance: vi.fn(),
            saveInstances: vi.fn(),
            deleteInstance: vi.fn(),
        };
        alertService = { success: vi.fn(), addAlert: vi.fn() };
        router = { navigate: vi.fn().mockResolvedValue(true) };
        routeParamMap = new BehaviorSubject(convertToParamMap({}));

        await TestBed.configureTestingModule({
            imports: [PresentationAssessmentManagementComponent],
            providers: [
                { provide: PresentationAssessmentService, useValue: presentationAssessmentService },
                { provide: AlertService, useValue: alertService },
                { provide: Router, useValue: router },
                { provide: TranslateService, useValue: { instant: (key: string) => key, onLangChange: languageChanges, onTranslationChange: translationChanges } },
                {
                    provide: ExerciseService,
                    useValue: { getTitlesForCourse: vi.fn().mockReturnValue(of([])) },
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId }) },
                        paramMap: routeParamMap,
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

    it('should request and display the second page of student rows', () => {
        const instance = instances[1];
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ presentationAssessment, instance }],
                    headers: new HttpHeaders({ 'X-Total-Count': '2' }),
                }),
            ),
        );

        component.overviewPageSize.set(1);
        component.overviewPage.set(1);
        fixture.detectChanges();

        expect(presentationAssessmentService.findStudentRows).toHaveBeenLastCalledWith(courseId, expect.objectContaining({ page: 1, size: 1 }));
        expect(component.totalStudentRows()).toBe(2);
        expect(component.paginatedStudentRows()).toHaveLength(1);
        expect(component.paginatedStudentRows()[0].instance.id).toBe(instance.id);
    });

    it('should load presentation assessments for the course', () => {
        expect(component.courseId()).toBe(courseId);
        expect(component.course()).toBe(course);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledWith(courseId);
        expect(TestBed.inject(ExerciseService).getTitlesForCourse).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.presentationAssessments()).toEqual([presentationAssessment]);
    });

    it('should represent the selected linked presentation in the route', () => {
        const linkedPresentation = { ...presentationAssessment, id: 43, exerciseId: 7 };
        component.presentationAssessments.set([linkedPresentation]);
        router.navigate.mockClear();

        component.selectPresentation(linkedPresentation);
        fixture.detectChanges();

        expect(router.navigate).toHaveBeenCalledWith(['/course-management', courseId, 'presentations', 43, 'exercises', 7], { replaceUrl: false });

        router.navigate.mockClear();
        component.setViewMode('students');
        fixture.detectChanges();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', courseId, 'presentations']);
    });

    it('should restore the selected presentation from the route', () => {
        const linkedPresentation = { ...presentationAssessment, id: 43, exerciseId: 7 };
        component.presentationAssessments.set([linkedPresentation]);

        routeParamMap.next(convertToParamMap({ presentationId: 43, exerciseId: 7 }));
        fixture.detectChanges();

        expect(component.selectedPresentationId()).toBe(43);
        expect(component.viewMode()).toBe('presentations');
    });

    it('should request and display student rows matching the search term', () => {
        const instance = instances[1];
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ presentationAssessment, instance }],
                    headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                }),
            ),
        );

        component.setViewMode('students');
        component.updateStudentSearch('STUDENT2');
        fixture.detectChanges();

        expect(component.viewMode()).toBe('students');
        expect(presentationAssessmentService.findStudentRows).toHaveBeenLastCalledWith(courseId, expect.objectContaining({ searchTerm: 'STUDENT2', page: 0 }));
        expect(component.currentPageStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);
        expect(component.totalStudentRows()).toBe(1);
    });

    it('should only consider an assigned numeric score as assessed', async () => {
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [
                        {
                            presentationAssessment,
                            instance: { id: 11, presentationDate, resultPoints: undefined, student: { login: 'student1' } },
                        },
                    ],
                    headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                }),
            ),
        );
        component.retryLoading();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.filteredSelectedPresentationStudentRows()[0].assessed).toBe(false);

        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [
                        {
                            presentationAssessment,
                            instance: { id: 11, presentationDate, resultPoints: 0, student: { login: 'student1' } },
                        },
                    ],
                    headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                }),
            ),
        );
        component.retryLoading();
        fixture.detectChanges();
        await fixture.whenStable();
        expect(component.filteredSelectedPresentationStudentRows()[0].assessed).toBe(true);
    });

    it('should request and display rows for the selected presentation', () => {
        const instance = instances[1];
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ presentationAssessment, instance }],
                    headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                }),
            ),
        );

        routeParamMap.next(convertToParamMap({ presentationId: presentationAssessment.id }));
        fixture.detectChanges();

        expect(presentationAssessmentService.findStudentRows).toHaveBeenLastCalledWith(courseId, expect.objectContaining({ assessmentId: presentationAssessment.id, page: 0 }));
        expect(component.filteredSelectedPresentationStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);
    });

    it('should display no student rows when the server returns an empty page', () => {
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [],
                    headers: new HttpHeaders({ 'X-Total-Count': '0' }),
                }),
            ),
        );

        routeParamMap.next(convertToParamMap({ presentationId: presentationAssessment.id }));
        fixture.detectChanges();

        expect(component.filteredSelectedPresentationStudentRows()).toEqual([]);
        expect(component.totalStudentRows()).toBe(0);
        expect(component.isLoadingStudentRows()).toBe(false);
        expect(component.studentRowsLoadFailed()).toBe(false);
    });

    it('should finish loading student rows when the response arrives', async () => {
        const response = new Subject<HttpResponse<PresentationAssessmentStudentRow[]>>();
        presentationAssessmentService.findStudentRows.mockReturnValue(response);

        component.updateStudentSearch('student2');
        fixture.detectChanges();

        expect(component.isLoadingStudentRows()).toBe(true);
        expect(component.loadedStudentRows()).toEqual([]);

        const instance = instances[1];
        response.next(
            new HttpResponse({
                body: [{ presentationAssessment, instance }],
                headers: new HttpHeaders({ 'X-Total-Count': '1' }),
            }),
        );
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isLoadingStudentRows()).toBe(false);
        expect(component.currentPageStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);
        expect(component.totalStudentRows()).toBe(1);

        response.complete();
    });

    it('should retry failed student rows without reloading presentation definitions', () => {
        presentationAssessmentService.findStudentRows.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

        component.updateStudentSearch('student2');
        fixture.detectChanges();

        expect(component.studentRowsLoadFailed()).toBe(true);
        expect(component.isLoadingStudentRows()).toBe(false);

        const instance = instances[1];
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ presentationAssessment, instance }],
                    headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                }),
            ),
        );
        presentationAssessmentService.findStudentRows.mockClear();
        presentationAssessmentService.findAllByCourseId.mockClear();

        component.retryLoading();
        fixture.detectChanges();

        expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledTimes(1);
        expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledWith(courseId, expect.objectContaining({ searchTerm: 'student2', page: 0 }));
        expect(presentationAssessmentService.findAllByCourseId).not.toHaveBeenCalled();
        expect(component.studentRowsLoadFailed()).toBe(false);
        expect(component.isLoadingStudentRows()).toBe(false);
        expect(component.currentPageStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);
    });

    it('should ignore a previous response after the search changes', async () => {
        const firstResponse = new Subject<HttpResponse<PresentationAssessmentStudentRow[]>>();
        const secondResponse = new Subject<HttpResponse<PresentationAssessmentStudentRow[]>>();
        presentationAssessmentService.findStudentRows.mockReturnValueOnce(firstResponse).mockReturnValueOnce(secondResponse);

        component.updateStudentSearch('student1');
        fixture.detectChanges();

        component.updateStudentSearch('student2');
        fixture.detectChanges();

        secondResponse.next(
            new HttpResponse({
                body: [{ presentationAssessment, instance: instances[1] }],
                headers: new HttpHeaders({ 'X-Total-Count': '1' }),
            }),
        );
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.currentPageStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);

        firstResponse.next(
            new HttpResponse({
                body: [{ presentationAssessment, instance: instances[0] }],
                headers: new HttpHeaders({ 'X-Total-Count': '2' }),
            }),
        );
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.currentPageStudentRows().map((row) => row.studentLogin)).toEqual(['student2']);
        expect(component.totalStudentRows()).toBe(1);
        expect(component.isLoadingStudentRows()).toBe(false);

        firstResponse.complete();
        secondResponse.complete();
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

    it('should keep content hidden until the new course and route selection are loaded', () => {
        const response = new Subject<HttpResponse<PresentationAssessment[]>>();
        presentationAssessmentService.findAllByCourseId.mockReturnValue(response);
        routeParamMap.next(convertToParamMap({ courseId: 2, presentationId: 99 }));
        fixture.detectChanges();
        expect(component.contentReady()).toBe(false);
        expect(component.presentationAssessments()).toEqual([]);
        response.next(new HttpResponse({ body: [{ id: 99, courseId: 2, title: 'Next course' }] }));
        fixture.detectChanges();
        expect(component.contentReady()).toBe(true);
        expect(component.selectedPresentationId()).toBe(99);
    });

    it('should provide real sidebar links for the overview and presentation', () => {
        expect(component.sidebarData().pinnedData?.[0].routerLink).toBe(`/course-management/${courseId}/presentations`);
        expect(component.sidebarData().groupedData?.standalone.entityData[0].routerLink).toBe(`/course-management/${courseId}/presentations/42`);
    });

    it('should discard open dialog data when switching courses', () => {
        component.presentationDialogVisible.set(true);
        component.instanceDialogVisible.set(true);
        component.dialogPresentationAssessment.set(presentationAssessment);
        component.dialogInstancePresentationAssessment.set(presentationAssessment);
        component.dialogInstance.set({ id: 11 });
        component.dialogAssignedStudents.set([{ login: 'student1', internal: true }]);
        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();
        expect(component.presentationDialogVisible()).toBe(false);
        expect(component.instanceDialogVisible()).toBe(false);
        expect(component.dialogPresentationAssessment()).toBeUndefined();
        expect(component.dialogInstancePresentationAssessment()).toBeUndefined();
        expect(component.dialogInstance()).toBeUndefined();
        expect(component.dialogAssignedStudents()).toEqual([]);
    });

    it('should finish the failed load state and allow retrying', () => {
        presentationAssessmentService.findAllByCourseId.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();
        expect(component.presentationLoadFailed()).toBe(true);
        expect(component.contentReady()).toBe(false);
        presentationAssessmentService.findAllByCourseId.mockReturnValue(of(new HttpResponse({ body: [{ id: 99, courseId: 2 }] })));
        component.loadAll();
        fixture.detectChanges();
        expect(component.presentationLoadFailed()).toBe(false);
        expect(component.contentReady()).toBe(true);
    });

    it('should create the course management route for the linked exercise', () => {
        component.exercises.set([{ id: 7, type: ExerciseType.TEXT }]);
        component.presentationAssessments.set([{ ...presentationAssessment, exerciseId: 7 }]);

        expect(component.selectedPresentationExerciseRoute()).toEqual(['/course-management', courseId, 'text-exercises', 7]);
    });

    it('should expose stable keys and expansion state in the row view models', () => {
        const row = component.filteredSelectedPresentationStudentRows()[0];
        component.toggleStudentRowDetails(row);

        const selectedRow = component.filteredSelectedPresentationStudentRows()[0];
        const overviewRow = component.paginatedStudentRows()[0];
        expect(selectedRow).toMatchObject({ rowKey: '11:student1', assessed: true, expanded: true });
        expect(overviewRow).toMatchObject({ rowKey: '11:student1', assessed: true, expanded: true });
        expect(selectedRow.student).toBe(row.student);
        expect(selectedRow.instance).toBe(row.instance);
        expect(selectedRow.presentationAssessment).toBe(row.presentationAssessment);
        expect(overviewRow.student).toBe(row.student);
        expect(overviewRow.instance).toBe(row.instance);
        expect(overviewRow.presentationAssessment).toBe(row.presentationAssessment);
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

    it('should create instances for selected students through one batch request', () => {
        presentationAssessmentService.saveInstances.mockReturnValue(of(new HttpResponse({ body: [] })));
        component.startCreateInstance(presentationAssessment);

        const request: PresentationAssessmentInstancesCreate = {
            presentationDate,
            resultPoints: 10,
            studentLogins: ['student1', 'student2'],
            language: 'en',
            mode: PresentationAssessmentMode.IN_PERSON,
        };
        component.handleInstanceDialogSave({ kind: 'create', request });

        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledWith(courseId, presentationAssessment.id, request);
        expect(presentationAssessmentService.updateInstance).not.toHaveBeenCalled();
    });

    it('should update an individual instance through the update endpoint', () => {
        const instance = instances[0];
        const updatedInstance = { ...instance, resultPoints: 19 };
        const request: PresentationAssessmentInstanceRequest = {
            id: instance.id,
            presentationDate,
            resultPoints: 19,
            studentLogin: 'student1',
            language: 'en',
            mode: PresentationAssessmentMode.IN_PERSON,
        };
        presentationAssessmentService.updateInstance.mockReturnValue(of(new HttpResponse({ body: updatedInstance })));
        component.startEditInstance(presentationAssessment, instance);

        component.handleInstanceDialogSave({ kind: 'update', instance: request });

        expect(presentationAssessmentService.updateInstance).toHaveBeenCalledExactlyOnceWith(courseId, presentationAssessment.id, request);
        expect(presentationAssessmentService.saveInstances).not.toHaveBeenCalled();
    });

    it('should keep loaded student details when opening the instance edit dialog', () => {
        const instance = instances[0];

        component.startEditInstance(presentationAssessment, instance);

        expect(component.dialogAssignedStudents()).toEqual([expect.objectContaining({ login: 'student1', name: 'Student One' })]);
    });

    it('should delete an individual instance without updating it', () => {
        const instance = instances[0];
        presentationAssessmentService.deleteInstance.mockReturnValue(of(new HttpResponse<void>()));

        component.deleteInstance(presentationAssessment, instance);

        expect(presentationAssessmentService.deleteInstance).toHaveBeenCalledExactlyOnceWith(courseId, presentationAssessment.id, instance.id);
        expect(presentationAssessmentService.updateInstance).not.toHaveBeenCalled();
    });

    it('should return to the previous page after deleting its last instance', async () => {
        const instance = instances[1];
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ presentationAssessment, instance }],
                    headers: new HttpHeaders({ 'X-Total-Count': '2' }),
                }),
            ),
        );
        component.overviewPageSize.set(1);
        component.overviewPage.set(1);
        fixture.detectChanges();
        await fixture.whenStable();

        presentationAssessmentService.findStudentRows.mockClear();
        presentationAssessmentService.deleteInstance.mockReturnValue(of(new HttpResponse<void>()));
        presentationAssessmentService.findStudentRows.mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ presentationAssessment, instance: instances[0] }],
                    headers: new HttpHeaders({ 'X-Total-Count': '1' }),
                }),
            ),
        );

        component.deleteInstance(presentationAssessment, instance);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.overviewPage()).toBe(0);
        expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledExactlyOnceWith(courseId, expect.objectContaining({ page: 0, size: 1 }));
        expect(component.currentPageStudentRows()[0].instance.id).toBe(11);
        expect(component.totalStudentRows()).toBe(1);
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
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(component.presentationAssessments()).toEqual([presentationAssessment, savedAssessment]);
        expect(component.presentationDialogVisible()).toBe(false);
    });

    it('should update parent presentation data after dialog save', async () => {
        const savedAssessment = { ...presentationAssessment, title: 'Updated presentation' };
        presentationAssessmentService.update.mockReturnValue(of(new HttpResponse({ body: savedAssessment })));
        component.startEdit(presentationAssessment);
        await fixture.whenStable();
        presentationAssessmentService.findStudentRows.mockClear();

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
        expect(component.presentationAssessments()).toEqual([savedAssessment]);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledOnce();
    });

    it('should reload the presentation list when the save response has no body', () => {
        presentationAssessmentService.create.mockReturnValue(of(new HttpResponse()));
        component.startCreate();

        component.handlePresentationDialogSave({ presentationAssessment: { title: 'New presentation', maxPoints: 25, courseId } });

        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(2);
        expect(component.presentationDialogVisible()).toBe(false);
        expect(component.isSaving()).toBe(false);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.created');
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

        component.handleInstanceDialogSave({
            kind: 'create',
            request: {
                presentationDate,
                resultPoints: 10,
                studentLogins: ['student1'],
                language: 'en',
                mode: PresentationAssessmentMode.IN_PERSON,
            },
        });

        expect(presentationAssessmentService.saveInstances).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(component.instanceDialogVisible()).toBe(true);
        expect(component.isSaving()).toBe(false);
    });

    it('should close the instance dialog after saving instances', async () => {
        presentationAssessmentService.saveInstances.mockReturnValue(of(new HttpResponse({ body: [] })));
        component.startCreateInstance(presentationAssessment);
        await fixture.whenStable();
        presentationAssessmentService.findStudentRows.mockClear();
        presentationAssessmentService.getStatistics.mockClear();

        component.handleInstanceDialogSave({
            kind: 'create',
            request: {
                presentationDate,
                resultPoints: 10,
                studentLogins: ['student1'],
                language: 'en',
                mode: PresentationAssessmentMode.IN_PERSON,
            },
        });

        expect(component.instanceDialogVisible()).toBe(false);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.getStatistics).toHaveBeenCalledOnce();
    });

    it('should delete a presentation assessment from the table', async () => {
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));
        component.overviewPage.set(1);
        fixture.detectChanges();
        await fixture.whenStable();
        presentationAssessmentService.findStudentRows.mockClear();
        presentationAssessmentService.getStatistics.mockClear();

        component.deletePresentationAssessment(presentationAssessment);

        expect(presentationAssessmentService.delete).toHaveBeenCalledWith(courseId, presentationAssessment.id);
        expect(component.presentationAssessments()).toEqual([]);
        expect(alertService.success).toHaveBeenCalledWith('artemisApp.presentationAssessment.deleted', { title: presentationAssessment.title });
        expect(component.overviewPage()).toBe(0);
        expect(presentationAssessmentService.findAllByCourseId).toHaveBeenCalledTimes(1);
        fixture.detectChanges();
        await fixture.whenStable();
        expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledOnce();
        expect(presentationAssessmentService.getStatistics).toHaveBeenCalledOnce();
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

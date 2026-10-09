import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import type { ParamMap } from '@angular/router';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { PresentationAssessmentManagementComponent } from 'app/presentation/manage/presentation-assessment-management.component';
import { PresentationAssessmentService } from 'app/presentation/manage/presentation-assessment.service';
import { PresentationAssessmentInstanceFormResult } from 'app/presentation/manage/presentation-assessment-instance-form-dialog.component';
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

    /** Applies a search term the way the debounced input does once typing has paused. */
    function applySearch(searchTerm: string): void {
        component.studentSearchTerm.set(searchTerm);
        component.overviewPage.set(0);
    }

    it('should send the search term only after typing paused and start on the first page', () => {
        vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
        try {
            component.overviewPage.set(2);
            fixture.detectChanges();
            presentationAssessmentService.findStudentRows.mockClear();

            component.updateStudentSearch('s');
            component.updateStudentSearch('st');
            vi.advanceTimersByTime(299);
            component.updateStudentSearch('stu');
            vi.advanceTimersByTime(299);
            fixture.detectChanges();

            expect(component.studentSearchTerm()).toBe('');
            expect(component.overviewPage()).toBe(2);
            expect(presentationAssessmentService.findStudentRows).not.toHaveBeenCalled();

            vi.advanceTimersByTime(1);
            fixture.detectChanges();

            expect(component.studentSearchTerm()).toBe('stu');
            expect(component.overviewPage()).toBe(0);
            expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledOnce();
            expect(presentationAssessmentService.findStudentRows).toHaveBeenCalledWith(courseId, expect.objectContaining({ searchTerm: 'stu', page: 0 }));
        } finally {
            vi.useRealTimers();
        }
    });

    it('should not apply a pending search term in the next course', () => {
        vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
        try {
            component.updateStudentSearch('abc');
            routeParamMap.next(convertToParamMap({ courseId: 2 }));
            fixture.detectChanges();

            vi.advanceTimersByTime(300);

            expect(component.studentSearchTerm()).toBe('');
        } finally {
            vi.useRealTimers();
        }
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
        expect(component.studentRows()).toHaveLength(1);
        expect(component.studentRows()[0].instance.id).toBe(instance.id);
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
        applySearch('STUDENT2');
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
        expect(component.studentRows()[0].assessed).toBe(false);

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
        expect(component.studentRows()[0].assessed).toBe(true);
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
        expect(component.studentRows().map((row) => row.studentLogin)).toEqual(['student2']);
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

        expect(component.studentRows()).toEqual([]);
        expect(component.totalStudentRows()).toBe(0);
        expect(component.isLoadingStudentRows()).toBe(false);
        expect(component.studentRowsLoadFailed()).toBe(false);
    });

    it('should finish loading student rows when the response arrives', async () => {
        const response = new Subject<HttpResponse<PresentationAssessmentStudentRow[]>>();
        presentationAssessmentService.findStudentRows.mockReturnValue(response);

        applySearch('student2');
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

        applySearch('student2');
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

        applySearch('student1');
        fixture.detectChanges();

        applySearch('student2');
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

    it('should reset overview filters and sorting when switching courses', () => {
        component.studentSearchTerm.set('Student One');
        component.assessmentStatusFilter.set('assessed');
        component.presentationTypeFilter.set('exercise');
        component.presentationFilter.set(42);
        component.overviewPage.set(3);
        component.studentSortField.set('presentationDate');
        component.studentSortOrder.set(-1);

        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();

        expect(component.courseId()).toBe(2);
        expect(component.studentSearchTerm()).toBe('');
        expect(component.assessmentStatusFilter()).toBe('all');
        expect(component.presentationTypeFilter()).toBe('all');
        expect(component.presentationFilter()).toBe('all');
        expect(component.overviewPage()).toBe(0);
        expect(component.studentSortField()).toBe('studentLogin');
        expect(component.studentSortOrder()).toBe(1);
        expect(component.studentRowsRequest()).toEqual({
            page: 0,
            size: 25,
            sortField: 'studentLogin',
            direction: 'ASC',
            assessmentId: undefined,
            assessed: undefined,
            linkedToExercise: undefined,
            searchTerm: undefined,
        });
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
        const row = component.studentRows()[0];
        component.toggleStudentRowDetails(row);

        const selectedRow = component.studentRows()[0];
        const overviewRow = component.studentRows()[0];
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

    it.each(['success', 'error'])('should ignore a stale save %s after switching courses', (outcome) => {
        const oldResponse = new Subject<HttpResponse<PresentationAssessment>>();
        const currentResponse = new Subject<HttpResponse<PresentationAssessment>>();
        presentationAssessmentService.create.mockReturnValueOnce(oldResponse).mockReturnValueOnce(currentResponse);

        component.startCreate();
        component.handlePresentationDialogSave({
            presentationAssessment: { title: 'Course A presentation', maxPoints: 20, courseId },
        });

        expect(component.isSaving()).toBe(true);

        const currentAssessment: PresentationAssessment = {
            id: 99,
            title: 'Course B presentation',
            maxPoints: 20,
            courseId: 2,
        };
        presentationAssessmentService.findAllByCourseId.mockReturnValue(of(new HttpResponse({ body: [currentAssessment] })));

        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();

        expect(component.isSaving()).toBe(false);

        expect(component.selectedPresentationId()).toBe(currentAssessment.id);

        component.startCreate();
        component.handlePresentationDialogSave({
            presentationAssessment: { title: 'Another course B presentation', maxPoints: 20, courseId: 2 },
        });

        alertService.success.mockClear();
        alertService.addAlert.mockClear();

        if (outcome === 'success') {
            oldResponse.next(new HttpResponse({ body: { id: 123, title: 'Course A presentation', maxPoints: 20, courseId } }));
            oldResponse.complete();
        } else {
            oldResponse.error(new HttpErrorResponse({ status: 500 }));
        }

        expect(component.presentationAssessments()).toEqual([currentAssessment]);
        expect(component.selectedPresentationId()).toBe(currentAssessment.id);
        expect(component.presentationDialogVisible()).toBe(true);
        expect(component.isSaving()).toBe(true);
        expect(alertService.success).not.toHaveBeenCalled();
        expect(alertService.addAlert).not.toHaveBeenCalled();

        currentResponse.complete();
        expect(component.isSaving()).toBe(false);
    });

    it('should ignore a stale save after switching away and back to the same course', () => {
        const oldResponse = new Subject<HttpResponse<PresentationAssessment>>();
        presentationAssessmentService.create.mockReturnValue(oldResponse);

        component.startCreate();
        component.handlePresentationDialogSave({
            presentationAssessment: { title: 'Old presentation', maxPoints: 20, courseId },
        });

        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();

        routeParamMap.next(convertToParamMap({ courseId }));
        fixture.detectChanges();

        component.startCreate();
        alertService.success.mockClear();

        oldResponse.next(new HttpResponse({ body: { id: 123, title: 'Old presentation', maxPoints: 20, courseId } }));
        oldResponse.complete();

        expect(component.presentationAssessments()).toEqual([presentationAssessment]);
        expect(component.selectedPresentationId()).toBe(presentationAssessment.id);
        expect(component.presentationDialogVisible()).toBe(true);
        expect(component.isSaving()).toBe(false);
        expect(alertService.success).not.toHaveBeenCalled();
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

    it.each([
        ['should not add a generic alert when the server already reports the conflict', { errorKey: 'concurrentModification' }, 0],
        ['should still alert when the conflict carries no server message', undefined, 1],
    ])('%s', (_description, errorBody, expectedAlerts) => {
        presentationAssessmentService.saveInstances.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, error: errorBody })));
        component.startCreateInstance(presentationAssessment);

        component.handleInstanceDialogSave({
            kind: 'create',
            request: { presentationDate, resultPoints: 10, studentLogins: ['student1'], language: 'en', mode: PresentationAssessmentMode.IN_PERSON },
        });

        expect(alertService.addAlert).toHaveBeenCalledTimes(expectedAlerts);
        expect(component.instanceDialogVisible()).toBe(true);
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

    it.each([
        ['create', 'success'],
        ['create', 'error'],
        ['update', 'success'],
        ['update', 'error'],
    ] as const)('should ignore a stale instance %s %s after switching courses', (kind, outcome) => {
        const oldResponse = new Subject<HttpResponse<unknown>>();
        const currentResponse = new Subject<HttpResponse<unknown>>();
        const saveMethod = kind === 'create' ? presentationAssessmentService.saveInstances : presentationAssessmentService.updateInstance;
        saveMethod.mockReturnValueOnce(oldResponse).mockReturnValueOnce(currentResponse);

        const result: PresentationAssessmentInstanceFormResult =
            kind === 'create'
                ? {
                      kind: 'create',
                      request: {
                          presentationDate,
                          resultPoints: 10,
                          studentLogins: ['student1'],
                          language: 'en',
                          mode: PresentationAssessmentMode.IN_PERSON,
                      },
                  }
                : {
                      kind: 'update',
                      instance: {
                          id: instances[0].id,
                          presentationDate,
                          resultPoints: 10,
                          studentLogin: 'student1',
                          language: 'en',
                          mode: PresentationAssessmentMode.IN_PERSON,
                      },
                  };

        component.startCreateInstance(presentationAssessment);
        component.handleInstanceDialogSave(result);
        expect(component.isSaving()).toBe(true);

        const currentAssessment: PresentationAssessment = {
            id: 99,
            title: 'Course B presentation',
            maxPoints: 20,
            courseId: 2,
        };
        presentationAssessmentService.findAllByCourseId.mockReturnValue(of(new HttpResponse({ body: [currentAssessment] })));

        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();
        expect(component.isSaving()).toBe(false);

        component.startCreateInstance(currentAssessment);
        component.handleInstanceDialogSave(result);
        expect(component.isSaving()).toBe(true);

        alertService.success.mockClear();
        alertService.addAlert.mockClear();

        if (outcome === 'success') {
            oldResponse.next(new HttpResponse({ body: [] }));
            oldResponse.complete();
        } else {
            oldResponse.error(new HttpErrorResponse({ status: 500 }));
        }

        expect(component.presentationAssessments()).toEqual([currentAssessment]);
        expect(component.instanceDialogVisible()).toBe(true);
        expect(component.isSaving()).toBe(true);
        expect(alertService.success).not.toHaveBeenCalled();
        expect(alertService.addAlert).not.toHaveBeenCalled();

        currentResponse.complete();
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

    it.each(['success', 'error'])('should ignore a stale presentation deletion %s after switching courses', (outcome) => {
        const oldResponse = new Subject<HttpResponse<void>>();
        const currentResponse = new Subject<HttpResponse<PresentationAssessment>>();
        presentationAssessmentService.delete.mockReturnValue(oldResponse);
        presentationAssessmentService.create.mockReturnValue(currentResponse);

        component.deletePresentationAssessment(presentationAssessment);

        const currentAssessment: PresentationAssessment = {
            id: 99,
            title: 'Course B presentation',
            maxPoints: 20,
            courseId: 2,
        };
        presentationAssessmentService.findAllByCourseId.mockReturnValue(of(new HttpResponse({ body: [currentAssessment] })));

        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();

        component.startCreate();
        component.handlePresentationDialogSave({
            presentationAssessment: {
                title: 'Another course B presentation',
                maxPoints: 20,
                courseId: 2,
            },
        });
        component.overviewPage.set(1);

        const reloadRows = vi.spyOn(component['studentRowsResource'], 'reload');
        const reloadStatistics = vi.spyOn(component['statisticsResource'], 'reload');
        const dialogErrors = vi.fn();
        const subscription = component.dialogError$.subscribe(dialogErrors);
        alertService.success.mockClear();
        alertService.addAlert.mockClear();

        if (outcome === 'success') {
            oldResponse.next(new HttpResponse<void>());
            oldResponse.complete();
        } else {
            oldResponse.error(new HttpErrorResponse({ status: 500 }));
        }

        expect(component.presentationAssessments()).toEqual([currentAssessment]);
        expect(component.selectedPresentationId()).toBe(currentAssessment.id);
        expect(component.presentationDialogVisible()).toBe(true);
        expect(component.overviewPage()).toBe(1);
        expect(component.isSaving()).toBe(true);
        expect(reloadRows).not.toHaveBeenCalled();
        expect(reloadStatistics).not.toHaveBeenCalled();
        expect(dialogErrors).not.toHaveBeenCalled();
        expect(alertService.success).not.toHaveBeenCalled();
        expect(alertService.addAlert).not.toHaveBeenCalled();

        subscription.unsubscribe();
        currentResponse.complete();
        expect(component.isSaving()).toBe(false);
    });

    it.each(['success', 'error'])('should ignore a stale instance deletion %s after switching courses', (outcome) => {
        const oldResponse = new Subject<HttpResponse<void>>();
        presentationAssessmentService.deleteInstance.mockReturnValue(oldResponse);

        component.deleteInstance(presentationAssessment, instances[0]);

        const currentAssessment: PresentationAssessment = {
            id: 99,
            title: 'Course B presentation',
            maxPoints: 20,
            courseId: 2,
        };
        presentationAssessmentService.findAllByCourseId.mockReturnValue(of(new HttpResponse({ body: [currentAssessment] })));

        routeParamMap.next(convertToParamMap({ courseId: 2 }));
        fixture.detectChanges();

        component.startCreateInstance(currentAssessment);
        component.overviewPage.set(1);

        const reloadRows = vi.spyOn(component['studentRowsResource'], 'reload');
        const reloadStatistics = vi.spyOn(component['statisticsResource'], 'reload');
        alertService.success.mockClear();
        alertService.addAlert.mockClear();

        if (outcome === 'success') {
            oldResponse.next(new HttpResponse<void>());
            oldResponse.complete();
        } else {
            oldResponse.error(new HttpErrorResponse({ status: 500 }));
        }

        expect(component.presentationAssessments()).toEqual([currentAssessment]);
        expect(component.selectedPresentationId()).toBe(currentAssessment.id);
        expect(component.instanceDialogVisible()).toBe(true);
        expect(component.overviewPage()).toBe(1);
        expect(reloadRows).not.toHaveBeenCalled();
        expect(reloadStatistics).not.toHaveBeenCalled();
        expect(alertService.success).not.toHaveBeenCalled();
        expect(alertService.addAlert).not.toHaveBeenCalled();
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

    it('should go to the overview after deleting the selected presentation', () => {
        const remaining = { ...presentationAssessment, id: 99 };
        component.presentationAssessments.set([presentationAssessment, remaining]);
        component.selectedPresentationId.set(presentationAssessment.id);
        component.viewMode.set('presentations');
        presentationAssessmentService.delete.mockReturnValue(of(new HttpResponse<void>()));
        router.navigate.mockClear();

        component.deletePresentationAssessment(presentationAssessment);

        expect(component.viewMode()).toBe('students');
        expect(router.navigate).toHaveBeenCalledOnce();
        expect(router.navigate).toHaveBeenCalledWith(['/course-management', courseId, 'presentations']);
    });

    it('should not navigate when the page was left while the presentation deletion was pending', () => {
        const response = new Subject<HttpResponse<void>>();
        const remaining = { ...presentationAssessment, id: 99 };
        component.presentationAssessments.set([presentationAssessment, remaining]);
        component.selectedPresentationId.set(presentationAssessment.id);
        presentationAssessmentService.delete.mockReturnValue(response);
        router.navigate.mockClear();

        component.deletePresentationAssessment(presentationAssessment);
        fixture.destroy();
        response.next(new HttpResponse<void>());

        expect(router.navigate).not.toHaveBeenCalled();
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

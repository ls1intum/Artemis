import { TumUiButtonComponent, TumUiPanelComponent } from '@tumaet/ui-angular';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmbeddedViewRef } from '@angular/core';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroupsComponent } from 'app/exam/manage/exercise-groups/exercise-groups.component';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamExerciseTableComponent } from 'app/exam/manage/exercise-groups/exercise-table/exam-exercise-table.component';
import { ExamExerciseGroupEditModalComponent } from 'app/exam/manage/exercise-groups/group-edit-modal/exam-exercise-group-edit-modal.component';
import { ExamExerciseTypePickerComponent } from 'app/exam/manage/exercise-groups/exercise-type-picker/exam-exercise-type-picker.component';

describe('Exercise Groups Component', () => {
    const course = new Course();
    course.id = 456;

    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    let groups: ExerciseGroup[];

    let comp: ExerciseGroupsComponent;
    let fixture: ComponentFixture<ExerciseGroupsComponent>;

    let exerciseGroupService: ExerciseGroupService;
    let examManagementService: ExamManagementService;
    let eventManager: EventManager;
    let dialogService: DialogService;
    let router: Router;
    let alertService: AlertService;

    const data = of({ exam });
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id, examId: exam.id }) },
        data,
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                ExerciseGroupsComponent,
                MockComponent(ExamExerciseTableComponent),
                MockComponent(ExamExerciseGroupEditModalComponent),
                MockComponent(ExamExerciseTypePickerComponent),
                MockComponent(TumUiPanelComponent),
                MockComponent(TumUiButtonComponent),
                MockDirective(DeleteButtonDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
                { provide: DialogService, useClass: MockDialogService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseGroupsComponent);
                comp = fixture.componentInstance;

                exerciseGroupService = TestBed.inject(ExerciseGroupService);
                examManagementService = TestBed.inject(ExamManagementService);
                eventManager = TestBed.inject(EventManager);
                dialogService = TestBed.inject(DialogService);
                alertService = TestBed.inject(AlertService);
                router = TestBed.inject(Router);

                groups = [
                    {
                        id: 0,
                        exercises: [
                            { id: 3, type: ExerciseType.TEXT },
                            { id: 4, type: ExerciseType.PROGRAMMING },
                        ],
                    } as ExerciseGroup,
                    { id: 1 } as ExerciseGroup,
                    { id: 2 } as ExerciseGroup,
                ];
                // Always initialized and bind before tests
                fixture.detectChanges();
            });
    });

    // The header buttons are projected into the shell title bar, so they are not part of this component's own view.
    // Render the registered actions template to query them, and destroy the view afterwards.
    const projectedViews: EmbeddedViewRef<unknown>[] = [];

    function renderActions(): HTMLElement {
        const service = TestBed.inject(CourseTitleBarService);
        const template = service.actionsTemplate();
        expect(template, 'the exercise-groups page does not project a title bar actions template').toBeDefined();
        const view = template!.createEmbeddedView({});
        projectedViews.push(view);
        view.detectChanges();
        const host = document.createElement('div');
        view.rootNodes.forEach((node) => host.appendChild(node));
        return host;
    }

    afterEach(() => {
        vi.restoreAllMocks();
        projectedViews.splice(0).forEach((view) => view.destroy());
    });

    it('loads the exercise groups', async () => {
        const mockResponse = new HttpResponse<Exam>({ body: exam });

        vi.spyOn(examManagementService, 'find').mockReturnValue(of(mockResponse));

        comp.loadExerciseGroups().subscribe((response) => {
            expect(response.body).not.toBeNull();
            expect(response.body!.id).toBe(exam.id);
        });

        await Promise.resolve();
    });

    it('loads exam information', async () => {
        const latestIndividualEndDate = dayjs();
        const mockResponse = new HttpResponse<ExamInformationDTO>({ body: { latestIndividualEndDate } });

        vi.spyOn(examManagementService, 'getLatestIndividualEndDateOfExam').mockReturnValue(of(mockResponse));

        comp.loadLatestIndividualEndDateOfExam().subscribe((response) => {
            expect(response).not.toBeNull();
            expect(response!.body).not.toBeNull();
            expect(response!.body!.latestIndividualEndDate).toBe(latestIndividualEndDate);
        });

        await Promise.resolve();
    });

    it('removes an exercise from group', () => {
        comp.exerciseGroups.set(groups);

        comp.removeExercise(3, 0);

        expect(comp.exerciseGroups()![0].exercises).toHaveLength(1);
    });

    it('deletes an exercise group', async () => {
        comp.exerciseGroups.set(groups);

        vi.spyOn(exerciseGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        vi.spyOn(eventManager, 'broadcast');

        comp.deleteExerciseGroup(0, {});
        await Promise.resolve();

        expect(exerciseGroupService.delete).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toHaveLength(groups.length - 1);
    });

    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'opens the import dialog and navigates to import page',
        async (exerciseType: ExerciseType) => {
            const onCloseSubject = new Subject<Exercise | undefined>();
            const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            vi.spyOn(router, 'navigate');

            comp.openImportModal(groups[0], exerciseType);

            // Simulate dialog closing with result
            onCloseSubject.next({ id: 1 } as Exercise);
            onCloseSubject.complete();
            await Promise.resolve();

            expect(dialogService.open).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import', 1]);
        },
    );
    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'opens the import dialog and navigates to import from file page',
        async (exerciseType: ExerciseType) => {
            const onCloseSubject = new Subject<Exercise | undefined>();
            const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
            vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
            vi.spyOn(router, 'navigate');

            comp.openImportModal(groups[0], exerciseType);

            // Simulate dialog closing with result (no id means import from file)
            onCloseSubject.next({ id: undefined } as Exercise);
            onCloseSubject.complete();
            await Promise.resolve();

            expect(dialogService.open).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(
                ['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import-from-file'],
                expect.anything(),
            );
        },
    );

    it('moves up an exercise group', () => {
        comp.exerciseGroups.set(groups);
        const from = 1;
        const to = 0;

        const fromId = comp.exerciseGroups()![from].id;
        const toId = comp.exerciseGroups()![to].id;

        comp.moveUp(from);

        expect(comp.exerciseGroups()![to].id).toBe(fromId);
        expect(comp.exerciseGroups()![from].id).toBe(toId);
    });

    it('moves down an exercise group', () => {
        comp.exerciseGroups.set(groups);
        const from = 0;
        const to = 1;

        const fromId = comp.exerciseGroups()![from].id;
        const toId = comp.exerciseGroups()![to].id;

        comp.moveDown(from);

        expect(comp.exerciseGroups()![to].id).toBe(fromId);
        expect(comp.exerciseGroups()![from].id).toBe(toId);
    });

    it('serializes reorder saves: ignores a reorder click while a save is in flight and keeps the optimistic order once it resolves', async () => {
        comp.exerciseGroups.set(groups);

        // Two independent PUTs, each controlled manually so the test can decide when they resolve.
        const firstSave = new Subject<HttpResponse<void>>();
        const secondSave = new Subject<HttpResponse<void>>();
        const updateOrderSpy = vi.spyOn(examManagementService, 'updateOrder').mockReturnValueOnce(firstSave.asObservable()).mockReturnValueOnce(secondSave.asObservable());

        // User action 1: move group at index 0 ("id 0") down. Optimistic local order becomes [1, 0, 2] and a save starts.
        comp.moveDown(0);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);
        expect(comp.orderSavePending()).toBe(true);
        expect(updateOrderSpy).toHaveBeenCalledOnce();

        // User action 2 (race attempt): while the first save is still in flight, further reorder actions must be
        // ignored so a second, independent PUT can never be fired (concurrent PUTs could otherwise arrive at the
        // server out of order and an earlier order would overwrite a later one).
        comp.moveDown(1);
        expect(updateOrderSpy).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);

        // The first (and only) in-flight save resolves; the response has no body and the optimistic order stands.
        firstSave.next(new HttpResponse<void>({}));
        firstSave.complete();
        await Promise.resolve();

        expect(comp.orderSavePending()).toBe(false);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);

        // Now that the first save has resolved, a new reorder action is allowed and fires its own PUT.
        comp.moveUp(2);
        expect(updateOrderSpy).toHaveBeenCalledTimes(2);
        expect(comp.orderSavePending()).toBe(true);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 2, 0]);

        secondSave.next(new HttpResponse<void>({}));
        secondSave.complete();
        await Promise.resolve();

        expect(comp.orderSavePending()).toBe(false);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 2, 0]);
    });

    it('restores the previous order and clears the pending flag when the reorder save is rejected', async () => {
        comp.exerciseGroups.set(groups);
        const failingSave = new Subject<HttpResponse<void>>();
        vi.spyOn(examManagementService, 'updateOrder').mockReturnValueOnce(failingSave.asObservable());
        const errorSpy = vi.spyOn(alertService, 'error');

        // The optimistic swap is applied immediately.
        comp.moveDown(0);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);
        expect(comp.orderSavePending()).toBe(true);

        // The server rejects the order (e.g. a stale tab whose groups no longer match the exam): the optimistic
        // swap must not stay visible.
        failingSave.error(new Error('rejected'));
        await Promise.resolve();

        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([0, 1, 2]);
        expect(comp.orderSavePending()).toBe(false);
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.orderCouldNotBeSaved');
    });

    it('opens the type picker for a group and forwards import requests to the import dialog', () => {
        comp.exerciseGroups.set(groups);

        comp.openTypePicker(0, 'import');

        expect(comp.typePickerVisible()).toBe(true);
        expect(comp.typePickerGroupId()).toBe(0);
        expect(comp.typePickerMode()).toBe('import');

        const openImportModalSpy = vi.spyOn(comp, 'openImportModal');
        comp.onTypePickerImport(ExerciseType.TEXT);

        expect(openImportModalSpy).toHaveBeenCalledWith(groups[0], ExerciseType.TEXT);
    });

    it('opens the group-edit modal for an existing group and updates it on save', async () => {
        comp.exerciseGroups.set(groups);
        const updated = { id: 1, title: 'Renamed', isMandatory: false } as ExerciseGroup;
        vi.spyOn(exerciseGroupService, 'update').mockReturnValue(of(new HttpResponse<ExerciseGroup>({ body: updated })));

        comp.openGroupEditModal(1);
        expect(comp.groupEditIsNew()).toBe(false);
        expect(comp.groupEditTarget()).toEqual(groups[1]);

        comp.onGroupEditSaved(updated);
        await Promise.resolve();

        expect(exerciseGroupService.update).toHaveBeenCalledWith(course.id, exam.id, updated);
        expect(comp.exerciseGroups()!.find((g) => g.id === 1)?.title).toBe('Renamed');
    });

    it('creates a new group via the create-group modal', async () => {
        comp.exerciseGroups.set(groups);
        const created = { id: 99, title: 'New group', isMandatory: true, exam } as ExerciseGroup;
        vi.spyOn(exerciseGroupService, 'create').mockReturnValue(of(new HttpResponse<ExerciseGroup>({ body: created })));

        comp.openCreateGroupModal();
        expect(comp.groupEditIsNew()).toBe(true);

        comp.onGroupEditSaved({ title: 'New group', isMandatory: true });
        await Promise.resolve();

        expect(exerciseGroupService.create).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toHaveLength(groups.length + 1);
    });

    it('moves an exercise into a different group on table group change', async () => {
        comp.exerciseGroups.set(groups);
        vi.spyOn(exerciseGroupService, 'moveExerciseToGroup').mockReturnValue(of(new HttpResponse<void>()));

        comp.onTableGroupChange({ exercise: { id: 3 } as Exercise, group: { id: 1 } as ExerciseGroup });
        await Promise.resolve();

        expect(exerciseGroupService.moveExerciseToGroup).toHaveBeenCalledWith(course.id, exam.id, 3, 1);
        expect(comp.exerciseGroups()!.find((g) => g.id === 0)!.exercises).toHaveLength(1);
        expect(comp.exerciseGroups()!.find((g) => g.id === 1)!.exercises).toEqual([{ id: 3, type: ExerciseType.TEXT }]);
    });

    it('opens the import modal for exercise groups', async () => {
        const alertSpy = vi.spyOn(alertService, 'success');
        const exerciseGroup = { id: 1 } as ExerciseGroup;

        const onCloseSubject = new Subject<ExerciseGroup[] | undefined>();
        const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

        comp.openExerciseGroupImportModal();

        // Simulate dialog closing with result
        onCloseSubject.next([exerciseGroup]);
        onCloseSubject.complete();
        await Promise.resolve();

        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toEqual([exerciseGroup]);
        expect(alertSpy).toHaveBeenCalledOnce();
    });

    it('shows the exercise group import button only to instructors, not to editors', () => {
        // Importing exercise groups requires selecting a source exam, which is instructor-only on the server;
        // editors must not see the (non-functional-for-them) import button, while still keeping "create".
        const editorCourse = new Course();
        editorCourse.id = course.id;
        editorCourse.isAtLeastEditor = true;
        editorCourse.isAtLeastInstructor = false;
        comp.course.set(editorCourse);
        fixture.detectChanges();
        let actions = renderActions();
        expect(actions.querySelector('#import-group')).toBeNull();
        expect(actions.querySelector('#create-new-group')).not.toBeNull();

        const instructorCourse = new Course();
        instructorCourse.id = course.id;
        instructorCourse.isAtLeastEditor = true;
        instructorCourse.isAtLeastInstructor = true;
        comp.course.set(instructorCourse);
        fixture.detectChanges();
        actions = renderActions();
        expect(actions.querySelector('#import-group')).not.toBeNull();
    });
});

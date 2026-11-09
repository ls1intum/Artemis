import { TumAetUiButtonComponent, TumAetUiDialogComponent, TumAetUiPanelComponent } from '@tumaet/ui-angular';
import { By } from '@angular/platform-browser';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmbeddedViewRef } from '@angular/core';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager, EventWithContent } from 'app/foundation/service/event-manager.service';
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
import { NEVER, Subject, of, throwError } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ExamDeleteDialogComponent } from 'app/exam/shared/delete-dialog/exam-delete-dialog.component';
import { ExamExerciseImportDialogComponent } from 'app/exam/manage/exercise-groups/exercise-import-dialog/exam-exercise-import-dialog.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamExerciseTableComponent } from 'app/exam/manage/exercise-groups/exercise-table/exam-exercise-table.component';
import { ExamExerciseGroupEditModalComponent } from 'app/exam/manage/exercise-groups/group-edit-modal/exam-exercise-group-edit-modal.component';
import { ExamExerciseTypePickerComponent } from 'app/exam/manage/exercise-groups/exercise-type-picker/exam-exercise-type-picker.component';
import { EntityDeletionProgress, EntityDeletionStatus, EntityDeletionType } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EXAM_DELETED_EVENT, ExamDeletedEventContent } from 'app/exam/shared/entities/exam-deleted-event.model';
import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { MockEntityDeletionService } from 'test/helpers/mocks/service/mock-entity-deletion.service';

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
                MockComponent(TumAetUiPanelComponent),
                MockComponent(TumAetUiDialogComponent),
                MockComponent(ExamImportComponent),
                MockComponent(TumAetUiButtonComponent),
                MockComponent(ExamDeleteDialogComponent),
                MockComponent(ExamExerciseImportDialogComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
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
        vi.spyOn(comp, 'loadExerciseGroups').mockReturnValue(NEVER);

        comp.deleteExerciseGroup(0, {});
        await Promise.resolve();

        expect(exerciseGroupService.delete).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toHaveLength(groups.length - 1);
    });

    it('reads the exercise groups from the server after a deletion ended, because the outcome may be unknown', () => {
        comp.exerciseGroups.set(groups);
        vi.spyOn(exerciseGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        const serverGroups = [{ id: 0, title: 'still there' } as ExerciseGroup];
        const loadSpy = vi.spyOn(comp, 'loadExerciseGroups').mockReturnValue(of(new HttpResponse<Exam>({ body: { exerciseGroups: serverGroups } as Exam })));

        comp.deleteExerciseGroup(0, {});

        expect(loadSpy).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toEqual(serverGroups);
    });

    it('reads the exercise groups from the server again, e.g. when it is not known how the deletion of an exercise ended', () => {
        comp.exerciseGroups.set(groups);
        const serverGroups = [{ id: 0, title: 'still there', exercises: [{ id: 11 }] } as ExerciseGroup];
        const loadSpy = vi.spyOn(comp, 'loadExerciseGroups').mockReturnValue(of(new HttpResponse<Exam>({ body: { exerciseGroups: serverGroups } as Exam })));

        comp.reloadExerciseGroups();

        expect(loadSpy).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toEqual(serverGroups);
    });

    it('hands the exercise group to the background deletion with the title it has', () => {
        comp.exerciseGroups.set([{ id: 5, title: 'Group A' } as ExerciseGroup]);
        vi.spyOn(exerciseGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        comp.deleteExerciseGroup(5, { deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: true });

        const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
        expect(deletionService.delete).toHaveBeenCalledWith(
            expect.objectContaining({ entityType: EntityDeletionType.EXERCISE_GROUP, entityId: 5, title: 'Group A', successAlertKey: 'artemisApp.exerciseGroup.deleted' }),
            expect.anything(),
        );
    });

    it('keeps the exercise group when the background deletion failed', () => {
        (TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService).status = EntityDeletionStatus.FAILED;
        comp.exerciseGroups.set(groups);
        vi.spyOn(exerciseGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');

        comp.deleteExerciseGroup(0, {});

        expect(comp.exerciseGroups()).toHaveLength(groups.length);
        expect(broadcastSpy).not.toHaveBeenCalled();
    });

    it('announces no deletion and reads the groups from the server when the outcome of the deletion is unknown', () => {
        (TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService).status = EntityDeletionStatus.UNKNOWN;
        comp.exerciseGroups.set(groups);
        vi.spyOn(exerciseGroupService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
        const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
        const serverGroups = [...groups];
        const loadSpy = vi.spyOn(comp, 'loadExerciseGroups').mockReturnValue(of(new HttpResponse<Exam>({ body: { exerciseGroups: serverGroups } as Exam })));

        comp.deleteExerciseGroup(0, {});

        // the group is neither removed locally nor announced as deleted: the server tells what is left
        expect(broadcastSpy).not.toHaveBeenCalled();
        expect(loadSpy).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toEqual(serverGroups);
    });

    describe('resuming a running deletion after a reload', () => {
        let deletionService: MockEntityDeletionService;

        const progress = (status: EntityDeletionStatus, entityType: EntityDeletionType, entityId: number): EntityDeletionProgress => ({
            entityType,
            entityId,
            status,
            stepsCompleted: 2,
            totalSteps: 2,
            itemsProcessed: 0,
            totalItems: 0,
            weightedProgressPercent: 100,
        });

        /**
         * Loads the page for an exam of which the user is (or is not) an instructor, with two groups. Reading the groups again, which happens
         * when a deletion ended, returns {@link reloadedGroups} instead when given.
         */
        const loadPage = (isAtLeastInstructor: boolean, reloadedGroups?: ExerciseGroup[]) => {
            const examCourse = new Course();
            examCourse.id = course.id;
            examCourse.isAtLeastInstructor = isAtLeastInstructor;
            const loadedExam = new Exam();
            loadedExam.id = exam.id;
            loadedExam.title = 'Final exam';
            loadedExam.course = examCourse;
            loadedExam.exerciseGroups = [{ id: 8, title: 'Group A' } as ExerciseGroup, { id: 9, title: 'Group B' } as ExerciseGroup];
            const loaded = of(new HttpResponse<Exam>({ body: loadedExam }));
            const loadSpy = vi.spyOn(comp, 'loadExerciseGroups');
            loadSpy.mockReturnValueOnce(loaded);
            loadSpy.mockReturnValue(reloadedGroups ? of(new HttpResponse<Exam>({ body: { exerciseGroups: reloadedGroups } as Exam })) : loaded);
            vi.spyOn(comp, 'loadLatestIndividualEndDateOfExam').mockReturnValue(of(null));
            comp.ngOnInit();
        };

        beforeEach(() => {
            deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
            deletionService.resumeInExam.mockClear();
        });

        it('looks for a running deletion in the exam once the groups are loaded, for instructors', () => {
            loadPage(true);

            expect(deletionService.resumeInExam).toHaveBeenCalledOnce();
            const [courseId, examId, titleOf, lifetime] = deletionService.resumeInExam.mock.calls[0];
            expect(courseId).toBe(course.id);
            expect(examId).toBe(exam.id);
            // the titles come from what the page has loaded, so the progress dialog names the group and the exam
            expect(titleOf(EntityDeletionType.EXERCISE_GROUP, 9)).toBe('Group B');
            expect(titleOf(EntityDeletionType.EXERCISE_GROUP, 77)).toBeUndefined();
            expect(titleOf(EntityDeletionType.EXAM, exam.id!)).toBe('Final exam');
            expect(lifetime).toBeDefined();
        });

        it('does not look for a running deletion when the user is no instructor', () => {
            loadPage(false);

            expect(deletionService.resumeInExam).not.toHaveBeenCalled();
        });

        it('announces the deletion and reads the groups again when the resumed deletion of a group completes', () => {
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            const serverGroups = [{ id: 8, title: 'Group A' } as ExerciseGroup];
            deletionService.resumeInExam.mockReturnValueOnce(of(progress(EntityDeletionStatus.COMPLETED, EntityDeletionType.EXERCISE_GROUP, 9)));

            loadPage(true, serverGroups);

            expect(broadcastSpy).toHaveBeenCalledWith({ name: 'exerciseGroupOverviewModification', content: 'Deleted an exercise group' });
            // the group is gone from the list that the server returned, not only from the one that was loaded before
            expect(comp.exerciseGroups()).toEqual(serverGroups);
        });

        it('announces no deletion and reads the groups again when the resumed deletion of a group ends with an unknown outcome', () => {
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            const serverGroups = [{ id: 8 }, { id: 9 }, { id: 10 }] as ExerciseGroup[];
            deletionService.resumeInExam.mockReturnValueOnce(of(progress(EntityDeletionStatus.UNKNOWN, EntityDeletionType.EXERCISE_GROUP, 9)));

            loadPage(true, serverGroups);

            expect(broadcastSpy).not.toHaveBeenCalled();
            // the groups were read from the server again: this list is not the one that was loaded first
            expect(comp.exerciseGroups()).toEqual(serverGroups);
        });

        it('keeps the groups when the resumed deletion of a group failed, which the progress dialog reports', () => {
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            const reloadSpy = vi.spyOn(comp, 'reloadExerciseGroups');
            deletionService.resumeInExam.mockReturnValueOnce(of(progress(EntityDeletionStatus.FAILED, EntityDeletionType.EXERCISE_GROUP, 9)));

            loadPage(true);

            expect(reloadSpy).not.toHaveBeenCalled();
            expect(broadcastSpy).not.toHaveBeenCalled();
            expect(comp.exerciseGroups()).toHaveLength(2);
        });

        it('drops the exam from the shared list, reads the list again and leaves the page when the resumed deletion of the exam completes', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            deletionService.resumeInExam.mockReturnValueOnce(of(progress(EntityDeletionStatus.COMPLETED, EntityDeletionType.EXAM, exam.id!)));

            loadPage(true);

            expect(broadcastSpy).toHaveBeenNthCalledWith(1, new EventWithContent<ExamDeletedEventContent>(EXAM_DELETED_EVENT, { examId: exam.id! }));
            expect(broadcastSpy).toHaveBeenNthCalledWith(2, { name: 'examListModification', content: 'dummy' });
            expect(navigateSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', course.id, 'exams']);
            // the exam is dropped from the list before the page is left, so the overview never shows (or asks for the statistics of) a deleted exam
            expect(broadcastSpy.mock.invocationCallOrder.every((order) => order < navigateSpy.mock.invocationCallOrder[0])).toBe(true);
        });

        it('does not leave the page when the resumed deletion of the exam completes after the user left it', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            const ended$ = new Subject<EntityDeletionProgress>();
            deletionService.resumeInExam.mockReturnValueOnce(ended$);
            loadPage(true);

            fixture.destroy();
            ended$.next(progress(EntityDeletionStatus.COMPLETED, EntityDeletionType.EXAM, exam.id!));

            // lists are still refreshed, but the user is not pulled away from whatever they are doing now
            expect(broadcastSpy).toHaveBeenCalledWith({ name: 'examListModification', content: 'dummy' });
            expect(navigateSpy).not.toHaveBeenCalled();
        });

        it('only refreshes when the resumed deletion of the exam ends with an unknown outcome', () => {
            const navigateSpy = vi.spyOn(router, 'navigate');
            const broadcastSpy = vi.spyOn(eventManager, 'broadcast');
            const serverGroups = [{ id: 8 }, { id: 9 }, { id: 10 }] as ExerciseGroup[];
            deletionService.resumeInExam.mockReturnValueOnce(of(progress(EntityDeletionStatus.UNKNOWN, EntityDeletionType.EXAM, exam.id!)));

            loadPage(true, serverGroups);

            expect(broadcastSpy).toHaveBeenCalledExactlyOnceWith({ name: 'examListModification', content: 'Refresh after an unknown deletion outcome' });
            expect(navigateSpy).not.toHaveBeenCalled();
            expect(comp.exerciseGroups()).toEqual(serverGroups);
        });
    });

    it('reports a refused deletion to the confirmation dialog', () => {
        comp.exerciseGroups.set(groups);
        vi.spyOn(exerciseGroupService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409, statusText: 'Conflict' })));
        const errors: string[] = [];
        comp.dialogError.subscribe((message) => errors.push(message));

        comp.deleteExerciseGroup(0, {});

        expect(errors).toHaveLength(1);
        expect(errors[0]).not.toBe('');
        expect(comp.exerciseGroups()).toHaveLength(groups.length);
    });

    it('fetches the deletion summary of the exercise group that is about to be deleted', () => {
        const categories: EntitySummaryCategory[] = [{ titleKey: 'category', items: [{ labelKey: 'label', value: 3 }] }];
        const summarySpy = vi.spyOn(exerciseGroupService, 'getDeletionSummary').mockReturnValue(of(categories));
        expect(comp['deleteGroupSummary']()).toBeUndefined();

        comp['confirmDeleteGroup']({ id: 8, title: 'Group B' } as ExerciseGroup);
        let received: EntitySummaryCategory[] | undefined;
        comp['deleteGroupSummary']()!.subscribe((value) => (received = value));

        expect(summarySpy).toHaveBeenCalledWith(comp.courseId(), comp.examId(), 8);
        expect(received).toEqual(categories);
    });

    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'opens the import dialog for the chosen exercise type',
        (exerciseType: ExerciseType) => {
            comp.openImportModal(groups[0], exerciseType);

            expect(comp['exerciseImportVisible']()).toBe(true);
            expect(comp['exerciseImportType']()).toBe(exerciseType);
        },
    );

    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'closes the import dialog and navigates to the import page of the chosen exercise',
        (exerciseType: ExerciseType) => {
            vi.spyOn(router, 'navigate');
            comp.openImportModal(groups[0], exerciseType);

            comp['onExerciseImported']({ id: 1 } as Exercise);

            expect(comp['exerciseImportVisible']()).toBe(false);
            expect(router.navigate).toHaveBeenCalledOnce();
            expect(router.navigate).toHaveBeenCalledWith(['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'import', 1], {
                queryParams: { sourceIsExamExercise: false },
            });
        },
    );

    it.each([[ExerciseType.PROGRAMMING], [ExerciseType.TEXT], [ExerciseType.MODELING], [ExerciseType.QUIZ], [ExerciseType.FILE_UPLOAD]])(
        'closes the import dialog and navigates to the import from file page',
        (exerciseType: ExerciseType) => {
            vi.spyOn(router, 'navigate');
            comp.openImportModal(groups[0], exerciseType);

            // An exercise read from a file has no id
            const fromFile = { id: undefined, type: exerciseType } as Exercise;
            comp['onExerciseImported'](fromFile);

            expect(comp['exerciseImportVisible']()).toBe(false);
            expect(router.navigate).toHaveBeenCalledOnce();
            if (exerciseType === ExerciseType.PROGRAMMING) {
                expect(router.navigate).toHaveBeenCalledWith(['/course-management', 456, 'exams', 123, 'exercise-groups', 0, 'programming-exercises', 'import-from-file'], {
                    state: { programmingExerciseForImportFromFile: fromFile },
                });
            } else {
                // The other types open the editor of a new exercise with the settings from the file.
                expect(router.navigate).toHaveBeenCalledWith(['/course-management', 456, 'exams', 123, 'exercise-groups', 0, `${exerciseType}-exercises`, 'new'], {
                    state: { exerciseForImportFromFile: fromFile },
                });
            }
        },
    );

    it('asks for the confirmation of the deletion of a plain exercise group', () => {
        comp.localCIEnabled.set(false);
        const plainGroup = { id: 7, title: 'Plain', exercises: [{ id: 3, type: ExerciseType.TEXT }] } as ExerciseGroup;

        comp['confirmDeleteGroup'](plainGroup);

        expect(comp['deleteGroupVisible']()).toBe(true);
        expect(comp['groupToDelete']()).toBe(plainGroup);
        expect(comp['deleteGroupQuestion']()).toBe('artemisApp.examManagement.exerciseGroup.delete.question');
        expect(comp['deleteGroupChecks']()).toEqual({});
    });

    it('offers the build plan cleanup for a group with a programming exercise unless LocalCI is active', () => {
        const programmingGroup = { id: 8, title: 'Programming', exercises: [{ id: 4, type: ExerciseType.PROGRAMMING }] } as ExerciseGroup;
        comp.localCIEnabled.set(false);

        comp['confirmDeleteGroup'](programmingGroup);

        expect(comp['deleteGroupQuestion']()).toBe('artemisApp.examManagement.exerciseGroup.delete.questionLocalVC');
        expect(Object.keys(comp['deleteGroupChecks']())).toEqual(['deleteStudentReposBuildPlans', 'deleteBaseReposBuildPlans']);

        comp.localCIEnabled.set(true);
        expect(comp['deleteGroupChecks']()).toEqual({});
    });

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

    it('moves an exercise into an empty target group without an exercises array', async () => {
        comp.exerciseGroups.set(groups);
        expect(comp.exerciseGroups()!.find((g) => g.id === 1)!.exercises).toBeUndefined();
        vi.spyOn(exerciseGroupService, 'moveExerciseToGroup').mockReturnValue(of(new HttpResponse<void>()));

        expect(() => comp.onTableGroupChange({ exercise: { id: 3 } as Exercise, group: { id: 1 } as ExerciseGroup })).not.toThrow();
        await Promise.resolve();

        expect(comp.exerciseGroups()!.find((g) => g.id === 1)!.exercises).toEqual([{ id: 3, type: ExerciseType.TEXT }]);
    });

    it('keeps the groups unchanged and alerts when the move is rejected', async () => {
        comp.exerciseGroups.set(groups);
        const before = comp.exerciseGroups();
        vi.spyOn(exerciseGroupService, 'moveExerciseToGroup').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        const alertSpy = vi.spyOn(alertService, 'addAlert');

        comp.onTableGroupChange({ exercise: { id: 3 } as Exercise, group: { id: 1 } as ExerciseGroup });
        await Promise.resolve();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()).toBe(before);
    });

    it('does not show a duplicate generic alert when the move rejection already carries a server error key', async () => {
        comp.exerciseGroups.set(groups);
        vi.spyOn(exerciseGroupService, 'moveExerciseToGroup').mockReturnValue(
            throwError(() => new HttpErrorResponse({ status: 409, error: { errorKey: 'studentExamsAlreadyGenerated' } })),
        );
        const alertSpy = vi.spyOn(alertService, 'addAlert');

        comp.onTableGroupChange({ exercise: { id: 3 } as Exercise, group: { id: 1 } as ExerciseGroup });
        await Promise.resolve();

        // The server's errorKey-based alert (translated, shown independently) already informs the user; the
        // generic onError fallback must not add a second, untranslated alert on top of it.
        expect(alertSpy).not.toHaveBeenCalled();
    });

    it('opens the exercise group import in a dialog that targets the current exam', () => {
        fixture.detectChanges();
        expect(comp['groupImportVisible']()).toBe(false);
        expect(fixture.debugElement.query(By.directive(ExamImportComponent))).toBeNull();

        comp.openExerciseGroupImportModal();
        fixture.detectChanges();

        const importComponent = fixture.debugElement.query(By.directive(ExamImportComponent)).componentInstance;
        expect(importComponent.subsequentExerciseGroupSelection()).toBe(true);
        expect(importComponent.targetCourseId()).toBe(course.id);
        expect(importComponent.targetExamId()).toBe(exam.id);
    });

    it('shows the imported exercise groups and closes the import dialog', () => {
        const alertSpy = vi.spyOn(alertService, 'success');
        const exerciseGroup = { id: 1 } as ExerciseGroup;
        comp.openExerciseGroupImportModal();
        fixture.detectChanges();

        fixture.debugElement.query(By.directive(ExamImportComponent)).componentInstance.imported.emit([exerciseGroup]);
        fixture.detectChanges();

        expect(comp.exerciseGroups()).toEqual([exerciseGroup]);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importSuccessful');
        expect(comp['groupImportVisible']()).toBe(false);
        expect(fixture.debugElement.query(By.directive(ExamImportComponent))).toBeNull();
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

    it('shows an empty-state message and a create button when there are no exercise groups', () => {
        const editorCourse = new Course();
        editorCourse.id = course.id;
        editorCourse.isAtLeastEditor = true;
        comp.course.set(editorCourse);
        comp.exerciseGroups.set([]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('tumaet-ui-panel')).toHaveLength(0);
        expect(fixture.nativeElement.querySelector('#create-first-group')).not.toBeNull();
    });

    it('does not show the empty-state create button once a group exists', () => {
        comp.exerciseGroups.set(groups);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('#create-first-group')).toBeNull();
    });
    it('retains existing groups when saving a rename fails', () => {
        comp.exerciseGroups.set(groups);
        comp.openGroupEditModal(0);
        const error = vi.spyOn(alertService, 'error');
        vi.spyOn(exerciseGroupService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
        comp.onGroupEditSaved({ id: 0, title: 'Rejected title', isMandatory: true });
        expect(exerciseGroupService.update).toHaveBeenCalledWith(456, 123, { id: 0, title: 'Rejected title', isMandatory: true });
        expect(comp.exerciseGroups()).toEqual(groups);
        expect(error).toHaveBeenCalledWith('error.http.403');
    });
});

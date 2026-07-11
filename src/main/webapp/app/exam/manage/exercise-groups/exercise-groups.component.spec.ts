import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { faCheckDouble, faFileUpload, faFont, faKeyboard, faProjectDiagram } from '@fortawesome/free-solid-svg-icons';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup, ExerciseGroupOrderDTO } from 'app/exam/shared/entities/exercise-group.model';
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
import { ProgrammingExerciseInstructorStatusComponent } from 'app/programming/manage/status/programming-exercise-instructor-status.component';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { HasAnyAuthorityDirective } from 'app/foundation/auth/has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';

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
                MockComponent(ExamExerciseRowButtonsComponent),
                MockComponent(ProgrammingExerciseInstructorStatusComponent),
                MockDirective(DeleteButtonDirective),
                MockDirective(HasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FileUploadExerciseGroupCellComponent),
                MockComponent(ModelingExerciseGroupCellComponent),
                MockComponent(ProgrammingExerciseGroupCellComponent),
                MockComponent(QuizExerciseGroupCellComponent),
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

    afterEach(() => {
        vi.restoreAllMocks();
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

    it('returns the exercise icon type quiz', () => {
        const icon = faCheckDouble;
        const exercise = { type: ExerciseType.QUIZ } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type file upload', () => {
        const icon = faFileUpload;
        const exercise = { type: ExerciseType.FILE_UPLOAD } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type modeling', () => {
        const icon = faProjectDiagram;
        const exercise = { type: ExerciseType.MODELING } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type programming', () => {
        const icon = faKeyboard;
        const exercise = { type: ExerciseType.PROGRAMMING } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
    });

    it('returns the exercise icon type text', () => {
        const icon = faFont;
        const exercise = { type: ExerciseType.TEXT } as Exercise;

        expect(comp.exerciseIcon(exercise)).toBe(icon);
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

    it('serializes reorder saves: ignores a reorder click while a save is in flight and re-applies the confirmed order once it resolves', async () => {
        comp.exerciseGroups.set(groups);

        // Two independent PUTs, each controlled manually so the test can decide when (and in what order) they resolve.
        const firstSave = new Subject<HttpResponse<ExerciseGroupOrderDTO[]>>();
        const secondSave = new Subject<HttpResponse<ExerciseGroupOrderDTO[]>>();
        const updateOrderSpy = vi.spyOn(examManagementService, 'updateOrder').mockReturnValueOnce(firstSave.asObservable()).mockReturnValueOnce(secondSave.asObservable());

        // User action 1: move group at index 0 ("id 0") down. Optimistic local order becomes [1, 0, 2] and a save starts.
        comp.moveDown(0);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);
        expect(comp.orderSavePending()).toBe(true);
        expect(updateOrderSpy).toHaveBeenCalledOnce();

        // User action 2 (stale race attempt): while the first save is still in flight, further reorder actions must be
        // ignored so a second, independent PUT can never be fired (which would otherwise let a late-arriving response
        // re-apply an older order over a newer one).
        comp.moveDown(1);
        expect(updateOrderSpy).toHaveBeenCalledOnce();
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);

        // The first (and only) in-flight save now resolves with the server-confirmed order.
        firstSave.next(new HttpResponse<ExerciseGroupOrderDTO[]>({ body: [{ id: 1 }, { id: 0 }, { id: 2 }] }));
        firstSave.complete();
        await Promise.resolve();

        expect(comp.orderSavePending()).toBe(false);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 0, 2]);

        // Now that the first save has resolved, a new reorder action is allowed and fires its own PUT.
        comp.moveUp(2);
        expect(updateOrderSpy).toHaveBeenCalledTimes(2);
        expect(comp.orderSavePending()).toBe(true);

        // Resolve the two saves in reversed order relative to when they were sent (the first-sent response arrives
        // last): this only reaches the component's second, currently-pending subscription, since the first save was
        // already completed and its subscription is gone. The final rendered order must match the last user action.
        secondSave.next(new HttpResponse<ExerciseGroupOrderDTO[]>({ body: [{ id: 1 }, { id: 2 }, { id: 0 }] }));
        secondSave.complete();
        firstSave.next(new HttpResponse<ExerciseGroupOrderDTO[]>({ body: [{ id: 1 }, { id: 0 }, { id: 2 }] }));
        await Promise.resolve();

        expect(comp.orderSavePending()).toBe(false);
        expect(comp.exerciseGroups()!.map((group) => group.id)).toEqual([1, 2, 0]);
    });

    it('maps exercise types to exercise groups', () => {
        comp.exerciseGroups.set(groups);
        const firstGroupId = groups[0].id!;
        const expectedResult = [ExerciseType.TEXT, ExerciseType.PROGRAMMING];

        comp.setupExerciseGroupToExerciseTypesDict();
        const map = comp.exerciseGroupToExerciseTypesDict();

        expect(map).toBeDefined();
        expect(map.size).toBe(groups.length);
        expect(map.get(firstGroupId)).toEqual(expectedResult);
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
        expect(fixture.nativeElement.querySelector('#import-group')).toBeNull();
        expect(fixture.nativeElement.querySelector('#create-new-group')).not.toBeNull();

        const instructorCourse = new Course();
        instructorCourse.id = course.id;
        instructorCourse.isAtLeastEditor = true;
        instructorCourse.isAtLeastInstructor = true;
        comp.course.set(instructorCourse);
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#import-group')).not.toBeNull();
    });
});

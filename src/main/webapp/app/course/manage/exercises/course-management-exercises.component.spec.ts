import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BehaviorSubject, Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { CourseManagementExercisesComponent } from 'app/course/manage/exercises/course-management-exercises.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { SortService } from 'app/foundation/service/sort.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockProvider } from 'ng-mocks';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { CourseExerciseCard } from 'app/course/manage/exercises/course-exercise-cards';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_LOCALCI } from 'app/app.constants';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseDeletionApi } from 'app/openapi/api/quiz-exercise-deletion-api';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ExerciseTableComponent } from 'app/course/manage/exercises/exercise-row/exercise-table.component';
import { EntityDeletionStatus } from 'app/shared-ui/delete-dialog/entity-deletion-progress.model';
import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { EntityDeletionService } from 'app/shared-ui/delete-dialog/service/entity-deletion.service';
import { MockEntityDeletionService } from 'test/helpers/mocks/service/mock-entity-deletion.service';

describe('Course Management Exercises Component', () => {
    let comp: CourseManagementExercisesComponent;
    let fixture: ComponentFixture<CourseManagementExercisesComponent>;
    let variantGroupService: ExerciseVariantGroupService;
    let courseManagementService: CourseManagementService;
    let alertService: AlertService;
    let deleteDialogService: DeleteDialogService;

    const buildExercises = (): Exercise[] => [
        { id: 1, title: 'Intro Programming', type: ExerciseType.PROGRAMMING } as Exercise,
        { id: 2, title: 'Intro Text', type: ExerciseType.TEXT } as Exercise,
    ];
    let exercises: Exercise[];
    let course: Course;

    const parentRoute = {
        data: of({}),
    } as any as ActivatedRoute;
    const route = { parent: parentRoute, queryParams: of({}) } as any as ActivatedRoute;

    beforeEach(async () => {
        exercises = buildExercises();
        course = { id: 1, title: 'Introduction to Programming in Java', shortName: 'INTRO_JAVA', exercises, isAtLeastEditor: true, isAtLeastInstructor: true } as Course;
        (parentRoute as any).data = of({ course });

        await TestBed.configureTestingModule({
            imports: [CourseManagementExercisesComponent],
            providers: [
                { provide: EntityDeletionService, useClass: MockEntityDeletionService },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                MockProvider(DeleteDialogService),
                MockProvider(ParticipationWebsocketService, {
                    subscribeForLatestResultOfParticipation: () => new BehaviorSubject<Result | undefined>(undefined),
                }),
                MockProvider(CourseManagementService, {
                    findWithExercises: () => of(new HttpResponse({ body: course })),
                }),
                MockProvider(CourseExerciseService, {
                    findAllProgrammingExercisesForCourse: () => of(new HttpResponse({ body: [] })),
                }),
                MockProvider(ExerciseVariantGroupService, {
                    getGroupsForCourse: () => of([]),
                }),
                MockProvider(QuizExerciseService, {
                    findForCourse: () => of([]),
                }),
                MockProvider(TextExerciseService),
                MockProvider(FileUploadExerciseService),
                MockProvider(ModelingExerciseService),
                ProgrammingExerciseService,
                MockProvider(ExerciseService),
                MockProvider(SortService),
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseManagementExercisesComponent);
        comp = fixture.componentInstance;
        variantGroupService = TestBed.inject(ExerciseVariantGroupService);
        courseManagementService = TestBed.inject(CourseManagementService);
        alertService = TestBed.inject(AlertService);
        deleteDialogService = TestBed.inject(DeleteDialogService);
    });

    afterEach(() => {
        TestBed.inject(HttpTestingController).verify();
        vi.restoreAllMocks();
        localStorage.clear();
    });

    it('should set course on init', () => {
        comp.ngOnInit();
        expect(comp.course()).toBe(course);
    });

    it('should populate cards on init', () => {
        comp.ngOnInit();
        expect(comp.cards().length).toBeGreaterThan(0);
    });

    it('should propagate course permissions onto the loaded exercises', () => {
        comp.ngOnInit();
        expect(comp.exercises().every((e) => e.isAtLeastEditor === true && e.isAtLeastInstructor === true)).toBe(true);
    });

    it('loads the latest template and solution results in one course request for list warnings', () => {
        const templateParticipation = { id: 10, submissions: [{ results: [{ id: 20, score: 20 }] }] };
        const solutionParticipation = { id: 11, submissions: [{ results: [{ id: 21, score: 80 }] }] };
        const load = vi
            .spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse')
            .mockReturnValue(of(new HttpResponse({ body: [{ id: 1, type: ExerciseType.PROGRAMMING, templateParticipation, solutionParticipation }] as ProgrammingExercise[] })));

        comp.ngOnInit();

        expect(load).toHaveBeenCalledExactlyOnceWith(1);
        const programming = comp.exercises().find((exercise) => exercise.id === 1) as ProgrammingExercise;
        expect(programming.templateParticipation).toBe(templateParticipation);
        expect(programming.solutionParticipation).toBe(solutionParticipation);
        expect(programming.title).toBe('Intro Programming');
        expect(programming.isAtLeastEditor).toBe(true);
    });

    it('publishes the exercise list and groups before programming build results arrive', () => {
        const results = new Subject<HttpResponse<ProgrammingExercise[]>>();
        vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse').mockReturnValue(results);
        const groups = vi.spyOn(variantGroupService, 'getGroupsForCourse');

        comp.ngOnInit();

        expect(comp.loaded()).toBe(true);
        expect(comp.exercises()).toHaveLength(2);
        expect(comp.cards().flatMap((card) => card.exercises)).toHaveLength(2);
        expect(groups).toHaveBeenCalledWith(1);
        expect(comp['loadingBuildResults']()).toBe(true);
        results.next(new HttpResponse({ body: [] }));
        expect(comp['loadingBuildResults']()).toBe(false);
    });

    it('updates build warnings from live results across views and releases observers on navigation and destruction', () => {
        const templateResults = new BehaviorSubject<Result | undefined>(undefined);
        const solutionResults = new BehaviorSubject<Result | undefined>(undefined);
        const websocketService = TestBed.inject(ParticipationWebsocketService);
        const subscribe = vi.spyOn(websocketService, 'subscribeForLatestResultOfParticipation').mockImplementation((id) => (id === 10 ? templateResults : solutionResults));
        const unsubscribe = vi.spyOn(websocketService, 'unsubscribeForLatestResultOfParticipation');
        const routeData = new Subject<{ course: Course }>();
        (parentRoute as any).data = routeData;
        vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ id: 1, type: ExerciseType.PROGRAMMING, templateParticipation: { id: 10 }, solutionParticipation: { id: 11 } }] as ProgrammingExercise[],
                }),
            ),
        );
        vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(of([{ id: 9, title: 'Variants', exerciseIds: [1] }]));
        comp.ngOnInit();
        routeData.next({ course });
        const oldCards = comp.cards();
        const table = TestBed.createComponent(ExerciseTableComponent);
        table.componentRef.setInput('courseId', 1);
        table.componentRef.setInput('exercises', comp.cards()[0].exercises);
        expect(table.componentInstance.rows()[0].programmingBuildWarning).toBeUndefined();

        templateResults.next({ id: 30, score: 100 });
        solutionResults.next({ id: 31, score: 0 });

        expect(subscribe).toHaveBeenCalledTimes(2);
        expect(subscribe).toHaveBeenCalledWith(10, false, 1);
        expect(subscribe).toHaveBeenCalledWith(11, false, 1);
        expect(comp.cards()).not.toBe(oldCards);
        table.componentRef.setInput('exercises', comp.cards()[0].exercises);
        expect(table.componentInstance.rows()[0].programmingBuildWarning).toContain('artemisApp.programmingExercise.status.templateFulfillsTest');
        expect(table.componentInstance.rows()[0].programmingBuildWarning).toContain('artemisApp.programmingExercise.status.solutionFailsTest');
        comp.onViewChange('group');
        const programming = comp.cards().find((card) => card.group?.id === 9)?.exercises[0] as ProgrammingExercise;
        expect(programming.templateParticipation?.submissions?.[0].results?.[0].score).toBe(100);
        expect(programming.solutionParticipation?.submissions?.[0].results?.[0].score).toBe(0);
        templateResults.next({ id: 32, score: 0 });
        solutionResults.next({ id: 33, score: 100 });
        table.componentRef.setInput('exercises', comp.cards().find((card) => card.group?.id === 9)?.exercises);
        expect(table.componentInstance.rows()[0].programmingBuildWarning).toBeUndefined();

        const nextCourse = { id: 2, exercises: [] } as Course;
        vi.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: nextCourse })));
        routeData.next({ course: nextCourse });
        expect(templateResults.observed).toBe(false);
        expect(solutionResults.observed).toBe(false);
        expect(unsubscribe).toHaveBeenCalledWith(10, expect.objectContaining({ id: 1 }));
        vi.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        routeData.next({ course });
        expect(templateResults.observed).toBe(true);
        expect(solutionResults.observed).toBe(true);
        fixture.destroy();
        expect(templateResults.observed).toBe(false);
        expect(solutionResults.observed).toBe(false);
    });

    it.each(['templateParticipation', 'solutionParticipation'] as const)('preserves a newer HTTP result when %s replays an older cached result', (field) => {
        const cachedResults = new BehaviorSubject<Result | undefined>({ id: 20, score: 10 });
        vi.spyOn(TestBed.inject(ParticipationWebsocketService), 'subscribeForLatestResultOfParticipation').mockReturnValue(cachedResults);
        const programming = new ProgrammingExercise(undefined, undefined);
        programming.id = 1;
        programming[field] = { id: 10, submissions: [{ results: [{ id: 30, score: 100 }] }] };
        vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: [programming] })));

        comp.ngOnInit();

        const latest = () => (comp.exercises()[0] as ProgrammingExercise)[field]?.submissions?.[0].results?.[0];
        expect(latest()).toEqual({ id: 30, score: 100 });
        cachedResults.next({ id: 25, score: 20 });
        expect(latest()).toEqual({ id: 30, score: 100 });
        // Assessment updates can change the warning without changing the result identity.
        cachedResults.next({ id: 30, score: 80 });
        expect(latest()).toEqual({ id: 30, score: 80 });
        cachedResults.next({ id: 31, score: 0 });
        expect(latest()).toEqual({ id: 31, score: 0 });
    });

    it('keeps the exercise list available if loading build results fails', () => {
        vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse').mockReturnValue(throwError(() => new Error('Build results unavailable')));

        comp.ngOnInit();

        expect(comp.loaded()).toBe(true);
        expect(comp.exercises()).toHaveLength(2);
        expect(comp['loadingBuildResults']()).toBe(false);
    });

    it('does not request programming results when the course has no programming exercises', () => {
        course.exercises = [Object.assign(new TextExercise(undefined, undefined), { id: 2 })];
        const load = vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse');
        comp.ngOnInit();
        expect(load).not.toHaveBeenCalled();
    });

    it('ignores build results from a previous course after navigation', () => {
        const routeData = new Subject<{ course: Course }>();
        (parentRoute as any).data = routeData;
        const oldResults = new Subject<HttpResponse<ProgrammingExercise[]>>();
        vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse').mockReturnValue(oldResults);
        comp.ngOnInit();
        routeData.next({ course });
        const nextCourse: Course = { id: 2, exercises: [Object.assign(new TextExercise(undefined, undefined), { id: 7 })], isAtLeastEditor: true };
        vi.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: nextCourse })));
        routeData.next({ course: nextCourse });
        oldResults.next(new HttpResponse({ body: [] }));

        expect(comp.course()?.id).toBe(2);
        expect(comp.exercises().map((exercise) => exercise.id)).toEqual([7]);
    });

    it('preserves loaded build results in the group view', () => {
        vi.spyOn(TestBed.inject(CourseExerciseService), 'findAllProgrammingExercisesForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [
                        Object.assign(new ProgrammingExercise(undefined, undefined), {
                            id: 1,
                            templateParticipation: { id: 10, submissions: [{ results: [{ id: 20, score: 20 }] }] },
                        }),
                    ],
                }),
            ),
        );
        vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(of([{ id: 9, title: 'Variants', exerciseIds: [1] }]));
        comp.ngOnInit();
        comp.onViewChange('group');

        const member = comp.cards().find((card) => card.group?.id === 9)?.exercises[0] as ProgrammingExercise;
        expect(member?.templateParticipation?.submissions?.[0].results?.[0].score).toBe(20);
    });

    it('ignores previous-course group responses after navigation', () => {
        const routeData = new Subject<{ course: Course }>();
        (parentRoute as any).data = routeData;
        const oldGroups = new Subject<ExerciseVariantGroupDTO[]>();
        vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(oldGroups);
        comp.ngOnInit();
        routeData.next({ course });
        const nextCourse: Course = { id: 2, exercises: [Object.assign(new TextExercise(undefined, undefined), { id: 7 })], isAtLeastEditor: false };
        vi.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: nextCourse })));
        routeData.next({ course: nextCourse });
        oldGroups.next([{ id: 9, title: 'Old group', exerciseIds: [1] }]);

        expect(comp.groups()).toEqual([]);
        expect(comp.exercises().map((exercise) => exercise.id)).toEqual([7]);
    });

    it('should not load groups for tutors (non-editors)', () => {
        course.isAtLeastEditor = false;
        const groupsSpy = vi.spyOn(variantGroupService, 'getGroupsForCourse');
        comp.ngOnInit();
        expect(groupsSpy).not.toHaveBeenCalled();
    });

    it('should clear previously loaded groups when a reused component loads a non-editor course', () => {
        // Simulate group state left over from a previously shown editor course.
        comp.groups.set([{ id: 10, title: 'G', exercises: [] }]);
        course.isAtLeastEditor = false;
        const groupsSpy = vi.spyOn(variantGroupService, 'getGroupsForCourse');

        comp.ngOnInit();

        expect(groupsSpy).not.toHaveBeenCalled();
        expect(comp.groups()).toEqual([]);
        // The group view must not surface the stale group as a card / selector for the new course.
        comp.onViewChange('group');
        expect(comp.cards().every((card) => card.group === undefined)).toBe(true);
    });

    it('should mark itself loaded without exercises when the route has no course', () => {
        (parentRoute as any).data = of({});
        comp.ngOnInit();
        expect(comp.loaded()).toBe(true);
        expect(comp.exercises()).toEqual([]);
    });

    it('should filter exercises on search', () => {
        comp.ngOnInit();
        const initialCount = comp.cards().reduce((sum, b) => sum + b.exercises.length, 0);
        comp.onSearchChange('zzz_nomatch_zzz');
        const filteredCount = comp.cards().reduce((sum, b) => sum + b.exercises.length, 0);
        expect(filteredCount).toBeLessThan(initialCount);
        expect(comp.cards()).toHaveLength(0);
    });

    it('should only mark itself loaded after the initial load (gating the empty state)', () => {
        expect(comp.loaded()).toBe(false);
        expect(comp.cards().length).toBe(0);
        comp.ngOnInit();
        expect(comp.loaded()).toBe(true);
    });

    it('should default to the type view when nothing is stored', () => {
        expect(comp.view()).toBe('type');
    });

    it('should persist the selected view to local storage on change', () => {
        comp.onViewChange('week');
        expect(comp.view()).toBe('week');
        expect(localStorage.getItem('artemis.exerciseManagement.view')).toBe(JSON.stringify('week'));
    });

    it('should restore the persisted view on re-instantiation', () => {
        localStorage.setItem('artemis.exerciseManagement.view', JSON.stringify('group'));
        const restored = TestBed.createComponent(CourseManagementExercisesComponent).componentInstance;
        expect(restored.view()).toBe('group');
    });

    it('should ignore an invalid persisted view and fall back to the default', () => {
        localStorage.setItem('artemis.exerciseManagement.view', JSON.stringify('bogus'));
        const restored = TestBed.createComponent(CourseManagementExercisesComponent).componentInstance;
        expect(restored.view()).toBe('type');
    });

    describe('selection', () => {
        beforeEach(() => comp.ngOnInit());

        it('toggles individual ids on and off', () => {
            comp.toggleSelection(1);
            expect(comp.selectedIds().has(1)).toBe(true);
            expect(comp.selectedCount()).toBe(1);
            comp.toggleSelection(1);
            expect(comp.selectedCount()).toBe(0);
        });

        it('clears the whole selection', () => {
            comp.toggleSelection(1);
            comp.toggleSelection(2);
            comp.clearSelection();
            expect(comp.selectedCount()).toBe(0);
        });

        it('derives selected exercises, types and the all-programming flag', () => {
            comp.toggleSelection(1);
            expect(comp.selectedExercises().map((e) => e.id)).toEqual([1]);
            expect(comp.selectedTypes().has(ExerciseType.PROGRAMMING)).toBe(true);
            expect(comp.allSelectedAreProgramming()).toBe(true);
            expect(comp.selectedProgrammingExercises()).toHaveLength(1);

            comp.toggleSelection(2);
            expect(comp.allSelectedAreProgramming()).toBe(false);
        });

        it('selects and deselects all exercises of a card', () => {
            const card = { id: 'x', exercises: comp.exercises() } as CourseExerciseCard;
            comp.onTableSelectionAllChange(card, true);
            expect(comp.selectedCount()).toBe(2);
            comp.onTableSelectionAllChange(card, false);
            expect(comp.selectedCount()).toBe(0);
        });

        it('flags a selection that contains a variant-group member', () => {
            comp.exercises.set([
                { id: 1, title: 'Grouped', type: ExerciseType.PROGRAMMING, exerciseVariantGroup: { id: 9 } } as Exercise,
                { id: 2, title: 'Ungrouped', type: ExerciseType.PROGRAMMING } as Exercise,
            ]);

            comp.toggleSelection(2);
            expect(comp.selectionHasGroupMember()).toBe(false);

            comp.toggleSelection(1);
            expect(comp.selectionHasGroupMember()).toBe(true);
        });
    });

    describe('changeExerciseGroup', () => {
        beforeEach(() => comp.ngOnInit());

        it('assigns the exercise to the group and reloads groups', () => {
            const setSpy = vi.spyOn(variantGroupService, 'setExerciseVariantGroup').mockReturnValue(of(undefined));
            const groupsSpy = vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(of([]));

            comp.changeExerciseGroup(comp.exercises()[0], { id: 7, exercises: [] });

            expect(setSpy).toHaveBeenCalledWith(1, 1, 7);
            expect(groupsSpy).toHaveBeenCalledWith(1);
        });

        it('rejects non-individual quizzes with an alert instead of calling the server', () => {
            const setSpy = vi.spyOn(variantGroupService, 'setExerciseVariantGroup');
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');
            const quiz = { id: 9, type: ExerciseType.QUIZ, quizMode: QuizMode.SYNCHRONIZED } as QuizExercise;

            comp.changeExerciseGroup(quiz, { id: 7, exercises: [] });

            expect(setSpy).not.toHaveBeenCalled();
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.exerciseManagement.error.onlyIndividualQuiz');
        });

        it('allows individual-mode quizzes to join a group', () => {
            const setSpy = vi.spyOn(variantGroupService, 'setExerciseVariantGroup').mockReturnValue(of(undefined));
            const quiz = { id: 9, type: ExerciseType.QUIZ, quizMode: QuizMode.INDIVIDUAL } as QuizExercise;

            comp.changeExerciseGroup(quiz, { id: 7, exercises: [] });

            expect(setSpy).toHaveBeenCalledWith(1, 9, 7);
        });

        it('surfaces assignment errors via the alert service', () => {
            vi.spyOn(variantGroupService, 'setExerciseVariantGroup').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { title: 'boom' } })));
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

            comp.changeExerciseGroup(comp.exercises()[0], { id: 7, exercises: [] });

            expect(alertSpy).toHaveBeenCalled();
        });

        it('explains an automatic LocalCI test run after the assessment due date', () => {
            vi.spyOn(variantGroupService, 'setExerciseVariantGroup').mockReturnValue(
                throwError(() => new HttpErrorResponse({ error: { errorKey: 'automaticTestRunAfterAssessmentDueDate' } })),
            );
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

            comp.changeExerciseGroup(comp.exercises()[0], { id: 7, exercises: [] });

            expect(alertSpy).toHaveBeenCalledWith('artemisApp.exerciseManagement.error.automaticTestRunAfterAssessmentDueDate');
        });

        it('relays table group changes to changeExerciseGroup', () => {
            const setSpy = vi.spyOn(variantGroupService, 'setExerciseVariantGroup').mockReturnValue(of(undefined));
            comp.onTableGroupChange({ exercise: comp.exercises()[0], group: undefined });
            expect(setSpy).toHaveBeenCalledWith(1, 1, undefined);
        });
    });

    describe('outcome of a deletion that is not known', () => {
        it('reads the exercises from the server again and keeps every row, because the exercise may still exist', () => {
            comp.ngOnInit();
            const reloadSpy = vi.spyOn(courseManagementService, 'findWithExercises');
            const idsBefore = comp.exercises().map((exercise) => exercise.id);

            comp.reloadCourseExercises();

            expect(reloadSpy).toHaveBeenCalledOnce();
            expect(comp.exercises().map((exercise) => exercise.id)).toEqual(idsBefore);
        });
    });

    describe('deleteSelectedExercises', () => {
        let programmingDelete: ReturnType<typeof vi.spyOn>;
        let textDelete: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            comp.ngOnInit();
            programmingDelete = vi.spyOn(TestBed.inject(ProgrammingExerciseService), 'delete').mockReturnValue(of(new HttpResponse<void>()));
            textDelete = vi.spyOn(TestBed.inject(TextExerciseService), 'delete').mockReturnValue(of(new HttpResponse<void>()));
        });

        it('emits an empty error and skips the server when nothing is selected', () => {
            const errors: string[] = [];
            comp.selectedDeleteError$.subscribe((e) => errors.push(e));

            comp.deleteSelectedExercises();

            expect(errors).toEqual(['']);
            expect(programmingDelete).not.toHaveBeenCalled();
        });

        it('deletes each selected exercise via its type-specific service, clears the selection and reloads', () => {
            const reloadSpy = vi.spyOn(courseManagementService, 'findWithExercises');
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises();

            expect(programmingDelete).toHaveBeenCalledWith(1, undefined, undefined);
            expect(textDelete).toHaveBeenCalledWith(2);
            expect(comp.selectedCount()).toBe(0);
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('does not reload the previous course or clear the new selection when deletion completes after navigation', () => {
            const pendingDelete = new Subject<HttpResponse<void>>();
            programmingDelete.mockReturnValue(pendingDelete);
            const routeData = new Subject<{ course: Course }>();
            (parentRoute as any).data = routeData;
            const nextFixture = TestBed.createComponent(CourseManagementExercisesComponent);
            const nextComp = nextFixture.componentInstance;
            nextComp.ngOnInit();
            routeData.next({ course });
            nextComp.toggleSelection(1);
            nextComp.deleteSelectedExercises();
            const nextCourse = { id: 2, exercises: [{ id: 7, type: ExerciseType.TEXT }] } as Course;
            const reload = vi.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: nextCourse })));
            routeData.next({ course: nextCourse });
            nextComp.toggleSelection(7);
            reload.mockClear();

            pendingDelete.next(new HttpResponse<void>());
            pendingDelete.complete();

            expect(reload).not.toHaveBeenCalled();
            expect(nextComp.selectedIds()).toEqual(new Set([7]));
            expect(nextComp.exercises().map((exercise) => exercise.id)).toEqual([7]);
            nextFixture.destroy();
        });

        it('lets remaining deletes settle when one request is refused and still reloads', () => {
            programmingDelete.mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom', status: 500 })));
            const reloadSpy = vi.spyOn(courseManagementService, 'findWithExercises');
            const errors: string[] = [];
            comp.selectedDeleteError$.subscribe((e) => errors.push(e));
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises();

            expect(textDelete).toHaveBeenCalled();
            expect(errors).toEqual(['']);
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('reports a rejected request that the global error handling stays silent about, such as an unstructured 502', () => {
            const errorAlert = vi.spyOn(TestBed.inject(AlertService), 'error');
            programmingDelete.mockReturnValue(throwError(() => new HttpErrorResponse({ error: '<html>Bad Gateway</html>', status: 502 })));
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises();

            // only the rejected exercise is reported, the other one was accepted
            expect(errorAlert).toHaveBeenCalledOnce();
            expect(errorAlert).toHaveBeenCalledWith('artemisApp.deletionProgress.requestRejected', { title: expect.any(String) });
        });

        it('does not repeat an alert that the global error handling already shows for a rejected request', () => {
            const errorAlert = vi.spyOn(TestBed.inject(AlertService), 'error');
            const rejection = new HttpErrorResponse({ error: { title: 'Another deletion is already in progress' }, status: 409 });
            const alertedSpy = vi.spyOn(TestBed.inject(AlertService), 'showsHttpErrorAlert').mockReturnValue(true);
            programmingDelete.mockReturnValue(throwError(() => rejection));
            comp.toggleSelection(1);

            comp.deleteSelectedExercises();

            // the decision comes from the alert handling itself, so the rejection is not reported a second time
            expect(alertedSpy).toHaveBeenCalledWith(rejection);
            expect(errorAlert).not.toHaveBeenCalled();
        });

        it('does not open one progress dialog per exercise and announces the batch', () => {
            const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'info');
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises();

            const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
            expect(deletionService.delete).toHaveBeenCalledTimes(2);
            expect(deletionService.delete).toHaveBeenCalledWith(expect.objectContaining({ entityId: 1 }), expect.anything(), { silent: true });
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.deletionProgress.bulkStarted', { count: 2 });
        });

        it('ends the confirmation dialog and reloads when a background deletion failed, leaving the report of the failure to the service', () => {
            const deletionService = TestBed.inject(EntityDeletionService) as unknown as MockEntityDeletionService;
            deletionService.status = EntityDeletionStatus.FAILED;
            deletionService.errorMessage = 'Repository is locked';
            const reloadSpy = vi.spyOn(courseManagementService, 'findWithExercises');
            const errors: string[] = [];
            comp.selectedDeleteError$.subscribe((e) => errors.push(e));
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises();

            expect(errors).toEqual(['']);
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('adds up the deletion summaries of all selected exercises', () => {
            const summarySpy = vi
                .spyOn(TestBed.inject(ExerciseService), 'getDeletionSummary')
                .mockReturnValue(of([{ titleKey: 'work', items: [{ labelKey: 'participations', value: 300 }] }]));
            comp.toggleSelection(1);
            comp.toggleSelection(2);
            let merged: EntitySummaryCategory[] | undefined;

            comp.fetchSelectedExercisesDeletionSummary().subscribe((value) => (merged = value));

            expect(summarySpy).toHaveBeenCalledTimes(2);
            expect(merged).toEqual([{ titleKey: 'work', items: [{ labelKey: 'participations', value: 600, highlight: true }] }]);
        });

        it('has no summary when nothing is selected', () => {
            let merged: EntitySummaryCategory[] | undefined;

            comp.fetchSelectedExercisesDeletionSummary().subscribe((value) => (merged = value));

            expect(merged).toEqual([]);
        });

        it('forwards the delete dialog cleanup flags to each selected programming exercise deletion', () => {
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises({ deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: true });

            expect(programmingDelete).toHaveBeenCalledWith(1, true, true);
            // Non-programming deletions ignore the cleanup flags.
            expect(textDelete).toHaveBeenCalledWith(2);
        });

        it('reflects the LocalCI profile in localCIEnabled (gating the cleanup checkboxes)', () => {
            // The default mock profile has no active profiles, so the checkboxes are offered (non-LocalCI setup).
            expect(comp['localCIEnabled']()).toBe(false);

            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isProfileActive').mockImplementation((profile: string) => profile === PROFILE_LOCALCI);
            const localCIComp = TestBed.createComponent(CourseManagementExercisesComponent).componentInstance;
            expect(localCIComp['localCIEnabled']()).toBe(true);
        });

        it('deletes quiz, modeling and file-upload exercises via their services', () => {
            const quizDelete = vi.spyOn(TestBed.inject(QuizExerciseDeletionApi), 'deleteQuizExercise').mockReturnValue(of(undefined));
            const modelingDelete = vi.spyOn(TestBed.inject(ModelingExerciseService), 'delete').mockReturnValue(of(new HttpResponse<void>()));
            const fileUploadDelete = vi.spyOn(TestBed.inject(FileUploadExerciseService), 'delete').mockReturnValue(of(new HttpResponse<void>()));
            comp.exercises.set([
                { id: 3, type: ExerciseType.QUIZ } as Exercise,
                { id: 4, type: ExerciseType.MODELING } as Exercise,
                { id: 5, type: ExerciseType.FILE_UPLOAD } as Exercise,
            ]);
            comp.toggleSelection(3);
            comp.toggleSelection(4);
            comp.toggleSelection(5);

            comp.deleteSelectedExercises();

            expect(quizDelete).toHaveBeenCalledWith(3);
            expect(modelingDelete).toHaveBeenCalledWith(4);
            expect(fileUploadDelete).toHaveBeenCalledWith(5);
        });
    });

    describe('bulk programming exercise deletion requests', () => {
        it('omits cleanup flags on LocalCI so the server deletes the repositories by default', () => {
            vi.spyOn(TestBed.inject(ProfileService), 'isProfileActive').mockImplementation((profile: string) => profile === PROFILE_LOCALCI);
            const localCIComp = TestBed.createComponent(CourseManagementExercisesComponent).componentInstance;
            localCIComp.ngOnInit();
            localCIComp.toggleSelection(1);

            // LocalCI does not offer cleanup checkboxes, so the delete dialog emits an empty object.
            localCIComp.deleteSelectedExercises({});

            const request = TestBed.inject(HttpTestingController).expectOne((req) => req.method === 'DELETE' && req.url === 'api/programming/programming-exercises/1');
            expect(request.request.params.has('deleteStudentReposBuildPlans')).toBe(false);
            expect(request.request.params.has('deleteBaseReposBuildPlans')).toBe(false);
            request.flush(null);
            expect(localCIComp.selectedCount()).toBe(0);
        });

        it.each([
            [false, false],
            [false, true],
            [true, false],
            [true, true],
        ])('preserves explicit cleanup choices (student: %s, base: %s)', (deleteStudentReposBuildPlans, deleteBaseReposBuildPlans) => {
            comp.ngOnInit();
            comp.toggleSelection(1);

            comp.deleteSelectedExercises({ deleteStudentReposBuildPlans, deleteBaseReposBuildPlans });

            const request = TestBed.inject(HttpTestingController).expectOne((req) => req.method === 'DELETE' && req.url === 'api/programming/programming-exercises/1');
            expect(request.request.params.get('deleteStudentReposBuildPlans')).toBe(String(deleteStudentReposBuildPlans));
            expect(request.request.params.get('deleteBaseReposBuildPlans')).toBe(String(deleteBaseReposBuildPlans));
            request.flush(null);
            expect(comp.selectedCount()).toBe(0);
        });
    });

    describe('bulk actions on programming exercises', () => {
        beforeEach(() => comp.ngOnInit());

        it('shows the edit-selected modal and reloads when it saves', () => {
            const reloadSpy = vi.spyOn(courseManagementService, 'findWithExercises');
            comp.toggleSelection(1);

            comp.editSelectedExercises();

            expect(comp.showEditSelected()).toBe(true);
            expect(comp.editSelectedData()).toEqual(comp.selectedProgrammingExercises());
            comp.reloadCourseExercises();
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('does not show the edit-selected modal when a variant-group member is selected', () => {
            comp.exercises.set([{ id: 1, title: 'Grouped', type: ExerciseType.PROGRAMMING, exerciseVariantGroup: { id: 9 } } as Exercise]);
            comp.toggleSelection(1);

            comp.editSelectedExercises();

            expect(comp.showEditSelected()).toBe(false);
        });

        it('shows the consistency check dialog with the selected programming exercises', () => {
            comp.toggleSelection(1);

            comp.consistencyCheckSelected();

            expect(comp.showConsistencyCheck()).toBe(true);
            expect(comp.consistencyExercises()).toEqual(comp.selectedProgrammingExercises());
        });
    });

    describe('add / import / export dialogs', () => {
        beforeEach(() => comp.ngOnInit());

        it('opens the add modal in create mode', () => {
            comp.openCreateModal();
            expect(comp.addModalMode()).toBe('create');
            expect(comp.addModalVisible()).toBe(true);
        });

        it('opens the add modal in import mode', () => {
            comp.openImportModal();
            expect(comp.addModalMode()).toBe('import');
            expect(comp.addModalVisible()).toBe(true);
        });

        it('opens the add modal in export mode', () => {
            comp.openExportModal();
            expect(comp.addModalMode()).toBe('export');
            expect(comp.addModalVisible()).toBe(true);
        });

        it('allows the export to instructors only', () => {
            expect(comp.canExport()).toBe(true);
            comp.course.set({ ...course, isAtLeastInstructor: false });
            expect(comp.canExport()).toBe(false);
        });
    });

    describe('group create / edit / delete', () => {
        beforeEach(() => comp.ngOnInit());

        it('switches to the group view and shows a blank group-edit dialog on group create', () => {
            comp.onAddModalGroupCreate();

            expect(comp.view()).toBe('group');
            expect(comp.showGroupEdit()).toBe(true);
            expect(comp.groupEditGroup()).toEqual({ exercises: [] });
        });

        it('persists a newly created group and adds it to the view', () => {
            const dto: ExerciseVariantGroupDTO = { id: 10, title: 'New group', exerciseIds: [] };
            const createSpy = vi.spyOn(variantGroupService, 'createGroup').mockReturnValue(of(dto));

            comp.onAddModalGroupCreate();
            comp['onGroupEditSaved']({ title: 'New group', exercises: [] });

            expect(createSpy).toHaveBeenCalledWith(1, expect.objectContaining({ title: 'New group' }));
            expect(comp.groups().map((g) => g.id)).toContain(10);
        });

        it('shows the edit dialog for an existing group by id and updates it on save', () => {
            comp.groups.set([{ id: 10, title: 'Old', exercises: [] }]);
            const dto: ExerciseVariantGroupDTO = { id: 10, title: 'Renamed', exerciseIds: [] };
            const updateSpy = vi.spyOn(variantGroupService, 'updateGroup').mockReturnValue(of(dto));

            comp.openGroupEditModal(10);
            expect(comp.showGroupEdit()).toBe(true);
            comp['onGroupEditSaved']({ id: 10, title: 'Renamed', exercises: [] });

            expect(updateSpy).toHaveBeenCalledWith(1, expect.objectContaining({ id: 10, title: 'Renamed' }));
            expect(comp.groups()[0].title).toBe('Renamed');
        });

        it('does nothing when editing an unknown group id', () => {
            comp.groups.set([]);
            comp.openGroupEditModal(999);
            expect(comp.showGroupEdit()).toBe(false);
        });

        it('re-syncs member exercise timelines after a group update', () => {
            comp.exercises.set([{ id: 1, type: ExerciseType.TEXT, dueDate: dayjs('2020-01-01') } as Exercise]);
            comp.groups.set([{ id: 10, title: 'G', exercises: [] }]);
            const newDueDate = dayjs('2026-06-06T00:00:00Z');
            vi.spyOn(variantGroupService, 'updateGroup').mockReturnValue(of({ id: 10, title: 'G', dueDate: newDueDate, exerciseIds: [1] }));

            comp.onGroupEditModalSave({ id: 10, title: 'G', exercises: [] }, false);

            expect(comp.exercises()[0].dueDate).toBe(newDueDate);
            expect(comp.groups()[0].exercises?.map((e) => e.id)).toEqual([1]);
        });

        it('surfaces create errors via the alert service', () => {
            vi.spyOn(variantGroupService, 'createGroup').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { title: 'nope' } })));
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

            comp.onGroupEditModalSave({ title: 'X', exercises: [] }, true);

            expect(alertSpy).toHaveBeenCalled();
        });

        it('surfaces update errors via the alert service', () => {
            vi.spyOn(variantGroupService, 'updateGroup').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { title: 'nope' } })));
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

            comp.onGroupEditModalSave({ id: 10, title: 'X', exercises: [] }, false);

            expect(alertSpy).toHaveBeenCalled();
        });

        it('does not persist a group save without a course', () => {
            const createSpy = vi.spyOn(variantGroupService, 'createGroup');
            comp.course.set(undefined);
            comp.onGroupEditModalSave({ title: 'X', exercises: [] }, true);
            expect(createSpy).not.toHaveBeenCalled();
        });

        it('opens the delete dialog and deletes the group on confirm', () => {
            const deleteSpy = vi.spyOn(variantGroupService, 'deleteGroup').mockReturnValue(of(undefined));
            const groupsSpy = vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(of([]));
            let dialogOptions: any;
            vi.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation((options: any) => (dialogOptions = options));

            comp.confirmDeleteGroup({ id: 10, title: 'G', exercises: [] });
            dialogOptions.delete();

            expect(deleteSpy).toHaveBeenCalledWith(1, 10);
            expect(groupsSpy).toHaveBeenCalled();
        });

        it('does not crash when group deletion fails (error routed to the dialog)', () => {
            vi.spyOn(variantGroupService, 'deleteGroup').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom' })));
            let dialogOptions: any;
            vi.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation((options: any) => (dialogOptions = options));

            comp.confirmDeleteGroup({ id: 10, title: 'G', exercises: [] });
            expect(() => dialogOptions.delete()).not.toThrow();
        });

        it('permits group deletion only for instructors', () => {
            expect(comp.canDeleteGroups()).toBe(true);
            comp.course.set({ ...course, isAtLeastInstructor: false });
            expect(comp.canDeleteGroups()).toBe(false);
        });
    });

    describe('group loading', () => {
        it('merges loaded groups into the exercise list', () => {
            vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(of([{ id: 10, title: 'G', exerciseIds: [1] }]));

            comp.ngOnInit();

            expect(comp.groups().map((g) => g.id)).toEqual([10]);
            expect(comp.exercises().find((e) => e.id === 1)?.exerciseVariantGroup?.id).toBe(10);
        });

        it('clears stale groups and alerts when the group fetch fails', () => {
            vi.spyOn(variantGroupService, 'getGroupsForCourse').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { title: 'nope' } })));
            const alertSpy = vi.spyOn(alertService, 'addErrorAlert');

            comp.ngOnInit();

            expect(comp.groups()).toEqual([]);
            expect(alertSpy).toHaveBeenCalled();
        });
    });

    describe('quiz batch loading', () => {
        it('merges quiz batches and editability from the dedicated quiz endpoint', () => {
            const quiz = { id: 3, type: ExerciseType.QUIZ } as QuizExercise;
            course.exercises = [quiz];
            const loadedQuiz = { id: 3, type: ExerciseType.QUIZ, quizBatches: [{ id: 1, started: true }], isEditable: false } as QuizExercise;
            const quizService = TestBed.inject(QuizExerciseService);
            vi.spyOn(quizService, 'findForCourse').mockReturnValue(of([loadedQuiz]));
            vi.spyOn(quizService, 'getStatus').mockReturnValue(QuizStatus.ACTIVE);

            comp.ngOnInit();

            const merged = comp.exercises()[0] as QuizExercise;
            expect(merged.quizBatches).toEqual(loadedQuiz.quizBatches);
            expect(merged.isEditable).toBe(false);
        });
    });

    describe('exercise updates from row actions', () => {
        beforeEach(() => {
            comp.ngOnInit();
            comp.groups.set([{ id: 10, title: 'G', exercises: [comp.exercises()[0]] }]);
        });

        it('replaces an updated exercise in the list and its group', () => {
            const updated = { id: 1, title: 'Renamed', type: ExerciseType.PROGRAMMING } as Exercise;

            comp.onExerciseUpdated(updated);

            expect(comp.exercises().find((e) => e.id === 1)?.title).toBe('Renamed');
            expect(comp.groups()[0].exercises?.[0].title).toBe('Renamed');
        });

        it.each(['row', 'generated draft'])('removes a deleted %s from the list, cards, group and selection', (source) => {
            comp.toggleSelection(1);

            if (source === 'row') {
                comp.onExerciseDeleted(comp.exercises()[0]);
            } else {
                comp.onGeneratedExerciseDeleted(1);
            }
            expect(
                comp
                    .cards()
                    .flatMap((card) => card.exercises)
                    .some((exercise) => exercise.id === 1),
            ).toBe(false);

            expect(comp.exercises().map((e) => e.id)).toEqual([2]);
            expect(comp.groups()[0].exercises).toHaveLength(0);
            expect(comp.selectedIds().has(1)).toBe(false);
        });
    });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { CourseManagementExercisesComponent } from 'app/course/manage/exercises/course-management-exercises.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ProgrammingExerciseEditSelectedComponent } from 'app/programming/manage/edit-selected/programming-exercise-edit-selected.component';
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
import { DialogService } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';

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
    const route = { parent: parentRoute, queryParams: of({}), url: of([]) } as any as ActivatedRoute;

    beforeEach(async () => {
        exercises = buildExercises();
        course = { id: 1, title: 'Introduction to Programming in Java', shortName: 'INTRO_JAVA', exercises, isAtLeastEditor: true, isAtLeastInstructor: true } as Course;
        (parentRoute as any).data = of({ course });

        await TestBed.configureTestingModule({
            imports: [CourseManagementExercisesComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                MockProvider(DeleteDialogService),
                MockProvider(CourseManagementService, {
                    findWithExercises: () => of(new HttpResponse({ body: course })),
                }),
                MockProvider(ExerciseVariantGroupService, {
                    getGroupsForCourse: () => of([]),
                }),
                MockProvider(QuizExerciseService, {
                    findForCourse: () => of(new HttpResponse({ body: [] })),
                }),
                MockProvider(TextExerciseService),
                MockProvider(FileUploadExerciseService),
                MockProvider(ModelingExerciseService),
                MockProvider(ProgrammingExerciseService),
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            // The edit-selected modal is always instantiated (visibility is an input, not an @if), and its nested
            // timeline component pulls in ActivatedRoute.snapshot/Athena wiring that is irrelevant to this page's
            // empty-state rendering, so it is swapped for a stub whenever the template is actually rendered.
            .overrideComponent(CourseManagementExercisesComponent, {
                remove: { imports: [ProgrammingExerciseEditSelectedComponent] },
                add: { imports: [MockComponent(ProgrammingExerciseEditSelectedComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(CourseManagementExercisesComponent);
        comp = fixture.componentInstance;
        variantGroupService = TestBed.inject(ExerciseVariantGroupService);
        courseManagementService = TestBed.inject(CourseManagementService);
        alertService = TestBed.inject(AlertService);
        deleteDialogService = TestBed.inject(DeleteDialogService);
    });

    afterEach(() => {
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
    });

    it('shows the empty-state message and a create button when the course has no exercises at all', () => {
        course.exercises = [];
        comp.ngOnInit();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="create-first-exercise-button"]')).not.toBeNull();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.exerciseManagement.noExercisesYet');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.exerciseManagement.noExercisesMatch');
    });

    it('opens the create modal when the create-first-exercise button is clicked', () => {
        course.exercises = [];
        comp.ngOnInit();
        fixture.detectChanges();

        const createFirstButton = fixture.debugElement.query(By.css('[data-testid="create-first-exercise-button"]'));
        createFirstButton.triggerEventHandler('clicked', null);

        expect(comp.addModalVisible()).toBe(true);
        expect(comp.addModalMode()).toBe('create');
    });

    it('does not show the create-first button in the empty state to non-editors', () => {
        course.exercises = [];
        course.isAtLeastEditor = false;
        comp.ngOnInit();
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="create-first-exercise-button"]')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.exerciseManagement.noExercisesYet');
    });

    it('shows the search-empty message, not the empty-course state, when a search matches nothing but exercises exist', () => {
        comp.ngOnInit();
        comp.onSearchChange('zzz_nomatch_zzz');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="create-first-exercise-button"]')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('artemisApp.exerciseManagement.noExercisesMatch');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.exerciseManagement.noExercisesYet');
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

        it('relays table group changes to changeExerciseGroup', () => {
            const setSpy = vi.spyOn(variantGroupService, 'setExerciseVariantGroup').mockReturnValue(of(undefined));
            comp.onTableGroupChange({ exercise: comp.exercises()[0], group: undefined });
            expect(setSpy).toHaveBeenCalledWith(1, 1, undefined);
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

            expect(programmingDelete).toHaveBeenCalledWith(1, false, false);
            expect(textDelete).toHaveBeenCalledWith(2);
            expect(comp.selectedCount()).toBe(0);
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('lets remaining deletes settle and surfaces the first error on partial failure', () => {
            programmingDelete.mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'boom', status: 500 })));
            const errors: string[] = [];
            comp.selectedDeleteError$.subscribe((e) => errors.push(e));
            comp.toggleSelection(1);
            comp.toggleSelection(2);

            comp.deleteSelectedExercises();

            expect(textDelete).toHaveBeenCalled();
            expect(errors).toHaveLength(1);
            expect(errors[0]).not.toBe('');
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
            const quizDelete = vi.spyOn(TestBed.inject(QuizExerciseService), 'delete').mockReturnValue(of(new HttpResponse<void>()));
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

    describe('bulk actions on programming exercises', () => {
        beforeEach(() => comp.ngOnInit());

        it('shows the edit-selected modal and reloads when it saves', () => {
            const reloadSpy = vi.spyOn(courseManagementService, 'findWithExercises');
            comp.toggleSelection(1);

            comp.editSelectedExercises();

            expect(comp.showEditSelected()).toBe(true);
            expect(comp.editSelectedData()).toEqual(comp.selectedProgrammingExercises());
            comp['onEditSelectedSaved']();
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

        it('shows the quiz export dialog and returns to the add modal on back', () => {
            comp.openQuizExportDialog();
            expect(comp.showQuizExport()).toBe(true);

            comp['onQuizExportBack']();
            expect(comp.addModalMode()).toBe('create');
            expect(comp.addModalVisible()).toBe(true);
        });

        it('does not reopen the add modal when the export dialog closes without back', () => {
            comp.openQuizExportDialog();
            expect(comp.addModalVisible()).toBe(false);
        });

        it('does not show the export dialog without a course id', () => {
            comp.course.set(undefined);
            comp.openQuizExportDialog();
            expect(comp.showQuizExport()).toBe(false);
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
            vi.spyOn(quizService, 'findForCourse').mockReturnValue(of(new HttpResponse({ body: [loadedQuiz] })));
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

        it('removes a deleted exercise from the list, its group and the selection', () => {
            comp.toggleSelection(1);

            comp.onExerciseDeleted(comp.exercises()[0]);

            expect(comp.exercises().map((e) => e.id)).toEqual([2]);
            expect(comp.groups()[0].exercises).toHaveLength(0);
            expect(comp.selectedIds().has(1)).toBe(false);
        });
    });
});

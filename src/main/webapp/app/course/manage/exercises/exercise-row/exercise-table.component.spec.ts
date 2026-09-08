import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseTableComponent, NO_GROUP_OPTION_VALUE, TableGroupChange } from 'app/course/manage/exercises/exercise-row/exercise-table.component';
import { ExerciseActionsComponent } from 'app/course/manage/exercises/exercise-row/exercise-actions.component';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';

/** Stands in for the real actions component, whose service tree is irrelevant to what the table template renders. */
@Component({ selector: 'jhi-exercise-actions', template: '' })
class ExerciseActionsStubComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);
    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();
    readonly quizActionsMinWidth = output<number>();
}

describe('ExerciseTableComponent', () => {
    let component: ExerciseTableComponent;
    let fixture: ComponentFixture<ExerciseTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTableComponent],
            // The row template renders RouterLink, so the rendering tests below need a router.
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExerciseTableComponent, {
                remove: { imports: [ExerciseActionsComponent] },
                add: { imports: [ExerciseActionsStubComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('exercises', []);
    });

    describe('sortedExercises', () => {
        const dated1: Exercise = { id: 1, title: 'Dated early', type: ExerciseType.TEXT, dueDate: dayjs('2026-01-05T10:00:00Z') } as Exercise;
        const dated2: Exercise = { id: 2, title: 'Dated late', type: ExerciseType.TEXT, dueDate: dayjs('2026-01-10T10:00:00Z') } as Exercise;
        const undated: Exercise = { id: 3, title: 'Undated', type: ExerciseType.TEXT } as Exercise;

        it('sorts by title ascending by default and flips direction on repeat', () => {
            fixture.componentRef.setInput('exercises', [dated2, undated, dated1]);
            expect(component.sortedExercises().map((e) => e.title)).toEqual(['Dated early', 'Dated late', 'Undated']);

            component.sortBy('title');
            expect(component.sortedExercises().map((e) => e.title)).toEqual(['Undated', 'Dated late', 'Dated early']);
        });

        it('sorts undated exercises last regardless of ascending/descending direction', () => {
            fixture.componentRef.setInput('exercises', [dated2, undated, dated1]);

            component.sortBy('dueDate');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([1, 2, 3]);

            component.sortBy('dueDate');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([2, 1, 3]);
        });

        it('uses the owning group timeline for due-date sorting', () => {
            const grouped: Exercise = { id: 4, title: 'Grouped', type: ExerciseType.TEXT, dueDate: dayjs('2026-01-01T00:00:00Z') } as Exercise;
            const group: CourseExerciseGroup = { id: 10, exercises: [grouped], dueDate: dayjs('2026-01-20T00:00:00Z') };
            fixture.componentRef.setInput('exercises', [grouped, dated1]);
            fixture.componentRef.setInput('groups', [group]);

            component.sortBy('dueDate');
            // The group's later due date governs the grouped exercise, so it sorts after dated1.
            expect(component.sortedExercises().map((e) => e.id)).toEqual([1, 4]);
        });

        it('sorts by points and difficulty', () => {
            const easy: Exercise = { id: 1, title: 'a', type: ExerciseType.TEXT, maxPoints: 5, difficulty: DifficultyLevel.EASY } as Exercise;
            const hard: Exercise = { id: 2, title: 'b', type: ExerciseType.TEXT, maxPoints: 1, difficulty: DifficultyLevel.HARD } as Exercise;
            fixture.componentRef.setInput('exercises', [easy, hard]);

            component.sortBy('points');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([2, 1]);

            component.sortBy('difficulty');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([1, 2]);
        });

        it('ignores a drop within the same table (no persisted order exists, so reordering is not offered)', () => {
            const group: CourseExerciseGroup = { id: 10, exercises: [] };
            fixture.componentRef.setInput('group', group);
            fixture.componentRef.setInput('exercises', [dated1, dated2]);
            const changes: TableGroupChange[] = [];
            component.groupChange.subscribe((c) => changes.push(c));

            const container = { id: 'same' } as any;
            component.onDrop({ previousContainer: container, container, previousIndex: 0, currentIndex: 1 } as CdkDragDrop<Exercise[]>);

            expect(changes).toEqual([]);
            expect(component.sortColumn()).toBe('title');
            expect(component.sortedExercises().map((e) => e.id)).toEqual([1, 2]);
        });

        it('emits a group change when an exercise is dropped from another table', () => {
            const group: CourseExerciseGroup = { id: 10, exercises: [] };
            fixture.componentRef.setInput('group', group);
            const dragged = dated1;
            const changes: TableGroupChange[] = [];
            component.groupChange.subscribe((c) => changes.push(c));

            const previousContainer = { id: 'other' } as any;
            const container = { id: 'this' } as any;
            component.onDrop({ previousContainer, container, item: { data: dragged }, previousIndex: 0, currentIndex: 0 } as CdkDragDrop<Exercise[]>);

            expect(changes).toEqual([{ exercise: dragged, group }]);
        });
    });

    describe('sort state', () => {
        it('toggles the direction on the active column and resets to ascending on a new one', () => {
            component.sortBy('points');
            expect(component.sortColumn()).toBe('points');
            expect(component.sortAsc()).toBe(true);

            component.sortBy('points');
            expect(component.sortAsc()).toBe(false);

            component.sortBy('title');
            expect(component.sortColumn()).toBe('title');
            expect(component.sortAsc()).toBe(true);
        });
    });

    describe('selection state', () => {
        const one: Exercise = { id: 1, title: 'a', type: ExerciseType.TEXT } as Exercise;
        const two: Exercise = { id: 2, title: 'b', type: ExerciseType.TEXT } as Exercise;

        it('computes allSelected and someSelected', () => {
            fixture.componentRef.setInput('exercises', [one, two]);

            fixture.componentRef.setInput('selectedIds', new Set<number>());
            expect(component.allSelected()).toBe(false);
            expect(component.someSelected()).toBe(false);

            fixture.componentRef.setInput('selectedIds', new Set([1]));
            expect(component.allSelected()).toBe(false);
            expect(component.someSelected()).toBe(true);

            fixture.componentRef.setInput('selectedIds', new Set([1, 2]));
            expect(component.allSelected()).toBe(true);
            expect(component.someSelected()).toBe(false);
        });

        it('is never all-selected for an empty table', () => {
            fixture.componentRef.setInput('exercises', []);
            expect(component.allSelected()).toBe(false);
        });
    });

    describe('quiz actions column width', () => {
        it('keeps the largest reported width as the column floor', () => {
            expect(component.actionsMinWidthVar()).toBeUndefined();

            component.onQuizActionsMinWidth(120);
            expect(component.actionsMinWidthVar()).toBe('120px');

            component.onQuizActionsMinWidth(80);
            expect(component.actionsMinWidthVar()).toBe('120px');

            component.onQuizActionsMinWidth(200);
            expect(component.actionsMinWidthVar()).toBe('200px');
        });
    });

    describe('group resolution and effective dates', () => {
        const groupDueDate = dayjs('2026-03-03T00:00:00Z');
        const member: Exercise = { id: 1, title: 'member', type: ExerciseType.TEXT, dueDate: dayjs('2026-01-01T00:00:00Z') } as Exercise;
        const group: CourseExerciseGroup = { id: 10, title: 'G', exercises: [member], releaseDate: dayjs('2026-02-02T00:00:00Z'), dueDate: groupDueDate };

        it('resolves effective dates from the owning group', () => {
            fixture.componentRef.setInput('exercises', [member]);
            fixture.componentRef.setInput('groups', [group]);

            expect(component.effectiveDueDate(member)).toBe(groupDueDate);
            expect(component.effectiveReleaseDate(member)?.isSame(dayjs('2026-02-02T00:00:00Z'))).toBe(true);
            expect(component.effectiveAssessmentDueDate(member)).toBeUndefined();
            expect(component.owningGroupId(member)).toBe(10);
        });

        it('falls back to the exercise dates when ungrouped', () => {
            fixture.componentRef.setInput('exercises', [member]);
            fixture.componentRef.setInput('groups', []);

            expect(component.effectiveDueDate(member)?.isSame(dayjs('2026-01-01T00:00:00Z'))).toBe(true);
            expect(component.owningGroupId(member)).toBeUndefined();
        });

        it('emits the resolved group on select', () => {
            fixture.componentRef.setInput('groups', [group]);
            const changes: TableGroupChange[] = [];
            component.groupChange.subscribe((c) => changes.push(c));

            component.onGroupSelect(member, 10);
            component.onGroupSelect(member, undefined);

            expect(changes[0].group).toBe(group);
            expect(changes[1].group).toBeUndefined();
        });

        it('offers a no-group option followed by all groups', () => {
            fixture.componentRef.setInput('groups', [group]);
            const options = component.groupOptions();
            expect(options).toHaveLength(2);
            expect(options[0].value).toBe(NO_GROUP_OPTION_VALUE);
            expect(options[1].value).toBe(10);
        });
    });

    describe('owningGroupForExercise', () => {
        it('does not cross-match different draft exercises that both have an undefined id', () => {
            const draftA: Exercise = { title: 'Draft A', type: ExerciseType.TEXT } as Exercise;
            const draftB: Exercise = { title: 'Draft B', type: ExerciseType.TEXT } as Exercise;
            const groupA = { id: 1, exercises: [draftA] } as CourseExerciseGroup;
            const groupB = { id: 2, exercises: [draftB] } as CourseExerciseGroup;
            fixture.componentRef.setInput('exercises', []);
            fixture.componentRef.setInput('groups', [groupA, groupB]);

            expect(component.owningGroupForExercise(draftA)).toBe(groupA);
            expect(component.owningGroupForExercise(draftB)).toBe(groupB);
        });

        it('still matches saved exercises by id', () => {
            const saved: Exercise = { id: 42, title: 'Saved', type: ExerciseType.TEXT } as Exercise;
            const group = { id: 1, exercises: [saved] } as CourseExerciseGroup;
            fixture.componentRef.setInput('exercises', []);
            fixture.componentRef.setInput('groups', [group]);

            expect(component.owningGroupForExercise({ id: 42, title: 'Saved (different object)', type: ExerciseType.TEXT } as Exercise)).toBe(group);
        });
    });

    describe('row helpers', () => {
        it('builds the title link for saved exercises and none for drafts', () => {
            const saved: Exercise = { id: 5, type: ExerciseType.PROGRAMMING } as Exercise;
            expect(component.titleLink(saved)).toEqual(['/course-management', 1, 'programming-exercises', 5]);
            expect(component.titleLink({ type: ExerciseType.TEXT } as Exercise)).toBeUndefined();
            expect(component.urlSegment(saved)).toBe('programming-exercises');
            expect(component.icon(saved)).toBeDefined();
        });

        it('maps difficulty to a tag severity', () => {
            expect(component.difficultySeverity({ difficulty: DifficultyLevel.EASY } as Exercise)).toBe('success');
            expect(component.difficultySeverity({ difficulty: DifficultyLevel.MEDIUM } as Exercise)).toBe('warning');
            expect(component.difficultySeverity({ difficulty: DifficultyLevel.HARD } as Exercise)).toBe('danger');
            expect(component.difficultySeverity({} as Exercise)).toBe('secondary');
        });

        it('flags non-individual quizzes and provides a tooltip only for them', () => {
            const syncQuiz = { type: ExerciseType.QUIZ, quizMode: QuizMode.SYNCHRONIZED } as QuizExercise;
            const individualQuiz = { type: ExerciseType.QUIZ, quizMode: QuizMode.INDIVIDUAL } as QuizExercise;
            const text = { type: ExerciseType.TEXT } as Exercise;

            expect(component.isQuizNonIndividual(syncQuiz)).toBe(true);
            expect(component.isQuizNonIndividual(individualQuiz)).toBe(false);
            expect(component.isQuizNonIndividual(text)).toBe(false);
            expect(component.nonIndividualQuizTooltip(syncQuiz)).toBeDefined();
            expect(component.nonIndividualQuizTooltip(individualQuiz)).toBeUndefined();
        });

        it('maps quiz status to label keys and badge classes', () => {
            expect(component.quizStatusLabel({ status: QuizStatus.INVISIBLE } as QuizExercise)).toBe('artemisApp.quizExercise.quizStatus.invisible');
            expect(component.quizStatusLabel({ status: QuizStatus.VISIBLE } as QuizExercise)).toBe('artemisApp.quizExercise.quizStatus.visible');
            expect(component.quizStatusLabel({ status: QuizStatus.ACTIVE } as QuizExercise)).toBe('artemisApp.quizExercise.quizStatus.active');
            expect(component.quizStatusLabel({ status: QuizStatus.OPEN_FOR_PRACTICE } as QuizExercise)).toBe('artemisApp.quizExercise.practiceMode');
            expect(component.quizStatusLabel({} as QuizExercise)).toBeUndefined();

            expect(component.quizStatusSeverity({ status: QuizStatus.INVISIBLE } as QuizExercise)).toBe('secondary');
            expect(component.quizStatusSeverity({ status: QuizStatus.VISIBLE } as QuizExercise)).toBe('info');
            expect(component.quizStatusSeverity({ status: QuizStatus.ACTIVE } as QuizExercise)).toBe('success');
            // Practice mode shares `info` with the visible state; the two can never appear on the same row.
            expect(component.quizStatusSeverity({ status: QuizStatus.OPEN_FOR_PRACTICE } as QuizExercise)).toBe('info');
            expect(component.quizStatusSeverity({} as QuizExercise)).toBe('secondary');
        });

        it('builds the quiz mode translation key', () => {
            expect(component.quizModeKey({ quizMode: QuizMode.SYNCHRONIZED } as QuizExercise)).toBe('artemisApp.quizExercise.quizMode.synchronized');
            expect(component.quizModeKey({} as QuizExercise)).toBe('artemisApp.quizExercise.quizMode.');
        });

        it('tracks quiz rows by lifecycle-relevant state and other rows by id', () => {
            const trackKey = (exercise: Exercise) => component.exerciseTrackKey(exercise);
            const text = { id: 1, type: ExerciseType.TEXT } as Exercise;
            expect(trackKey(text)).toBe(1);

            const draft = { type: ExerciseType.TEXT } as Exercise;
            expect(trackKey(draft)).toBe(draft);

            const quiz = { id: 2, type: ExerciseType.QUIZ, status: QuizStatus.VISIBLE, visibleToStudents: true } as QuizExercise;
            const key = trackKey(quiz);
            expect(key).toContain('2|');
            expect(trackKey({ ...quiz, status: QuizStatus.ACTIVE } as QuizExercise)).not.toBe(key);
        });
    });

    /**
     * The row template reads precomputed `ExerciseRow` fields, and PrimeNG's `let-row` context is untyped, so a wrong
     * field name would silently render nothing rather than fail to compile. These tests render the table for real.
     */
    describe('rendering', () => {
        const quiz = {
            id: 7,
            title: 'Quiz exercise',
            type: ExerciseType.QUIZ,
            maxPoints: 10,
            difficulty: DifficultyLevel.HARD,
            quizMode: QuizMode.SYNCHRONIZED,
            status: QuizStatus.VISIBLE,
            dueDate: dayjs('2026-03-01T10:00:00Z'),
        } as QuizExercise;

        function renderRows(exercises: Exercise[]): HTMLElement {
            fixture.componentRef.setInput('exercises', exercises);
            fixture.detectChanges();
            return fixture.nativeElement as HTMLElement;
        }

        it('drives the select-all header checkbox: dash on partial selection, tick on all', async () => {
            const a = { id: 1, title: 'a', type: ExerciseType.TEXT } as Exercise;
            const b = { id: 2, title: 'b', type: ExerciseType.TEXT } as Exercise;
            fixture.componentRef.setInput('showCheckbox', true);
            fixture.componentRef.setInput('selectedIds', new Set([1]));
            const element = renderRows([a, b]);
            // ngModel writes through a resolved promise, so the rendered checkbox state settles after a flush.
            await fixture.whenStable();
            fixture.detectChanges();

            const headerCheckbox = element.querySelector('thead tum-ui-checkbox input') as HTMLInputElement;
            expect(headerCheckbox.indeterminate).toBe(true);
            expect(element.querySelector('thead tum-ui-checkbox svg[data-icon="minus"]')).not.toBeNull();

            fixture.componentRef.setInput('selectedIds', new Set([1, 2]));
            await fixture.whenStable();
            fixture.detectChanges();
            expect(headerCheckbox.indeterminate).toBe(false);
            expect(headerCheckbox.checked).toBe(true);
            expect(element.querySelector('thead tum-ui-checkbox svg[data-icon="check"]')).not.toBeNull();
        });

        it('renders a row per exercise with its title link, points and difficulty badge', () => {
            const text = { id: 3, title: 'Text exercise', type: ExerciseType.TEXT, maxPoints: 5, difficulty: DifficultyLevel.EASY } as Exercise;
            const element = renderRows([text]);

            const link = element.querySelector('.col-title a') as HTMLAnchorElement;
            expect(link.textContent).toContain('Text exercise');
            expect(link.getAttribute('href')).toBe('/course-management/1/text-exercises/3');
            expect(element.querySelector('.col-points')?.textContent).toContain('5pts');

            const difficultyTag = element.querySelector('tum-ui-tag');
            expect(difficultyTag?.textContent).toContain(DifficultyLevel.EASY);
            // The kit tag publishes its severity on the host, so a consumer can style or assert on it without
            // reaching into the component's internals.
            expect(difficultyTag?.getAttribute('data-severity')).toBe('success');
        });

        it('renders the effective dates of a row', () => {
            // Scoped to the body cell: the header also carries `col-dates`.
            const element = renderRows([quiz]);
            expect(element.querySelector('td.col-dates')?.textContent).toContain('artemisApp.exercise.due');
        });

        it('renders the quiz status and mode badges for a quiz row', () => {
            const element = renderRows([quiz]);
            const tags = Array.from(element.querySelectorAll('tum-ui-tag')).map((tag) => tag.textContent ?? '');

            expect(tags.some((text) => text.includes('artemisApp.quizExercise.quizStatus.visible'))).toBe(true);
            expect(tags.some((text) => text.includes('artemisApp.quizExercise.quizMode.synchronized'))).toBe(true);
        });

        it('renders the "none" placeholder only when a row has neither categories nor a quiz badge', () => {
            const bare = { id: 4, title: 'Bare', type: ExerciseType.TEXT, maxPoints: 1 } as Exercise;
            expect(renderRows([bare]).textContent).toContain('artemisApp.exerciseManagement.table.none');
            // The quiz row carries status and mode badges, so the categories cell must not fall back to the placeholder.
            expect(renderRows([quiz]).querySelector('tum-ui-tag')).not.toBeNull();
        });

        it('disables the drag handle for a non-individual quiz', () => {
            fixture.componentRef.setInput('showDragHandle', true);
            const handle = renderRows([quiz]).querySelector('.drag-handle') as HTMLElement;
            expect(handle.classList).toContain('disabled');
        });

        it('renders both the drag handle and the group dropdown when both are enabled (group view)', () => {
            const text = { id: 3, title: 'Text exercise', type: ExerciseType.TEXT, maxPoints: 5, difficulty: DifficultyLevel.EASY } as Exercise;
            const group: CourseExerciseGroup = { id: 10, title: 'Group A', exercises: [text] };
            fixture.componentRef.setInput('groups', [group]);
            fixture.componentRef.setInput('showDragHandle', true);
            fixture.componentRef.setInput('showGroupSelector', true);

            const element = renderRows([text]);
            // Drag-and-drop and the per-row group dropdown coexist in the group view.
            expect(element.querySelector('.drag-handle')).not.toBeNull();
            expect(element.querySelector('tum-ui-select')).not.toBeNull();
            // The group column replaces the difficulty column, so the difficulty badge is not rendered.
            expect(element.querySelector('tum-ui-tag')).toBeNull();
        });

        it('labels the group dropdown of an ungrouped exercise with "no group" rather than leaving it blank', async () => {
            // tum-ui-select renders the placeholder for an undefined value, so "no group" is modelled as a sentinel
            // (NO_GROUP_OPTION_VALUE) — this asserts the label the sentinel exists to keep visible.
            const text = { id: 3, title: 'Text exercise', type: ExerciseType.TEXT, maxPoints: 5 } as Exercise;
            fixture.componentRef.setInput('groups', [{ id: 10, title: 'Group A', exercises: [] } as CourseExerciseGroup]);
            fixture.componentRef.setInput('showGroupSelector', true);

            const element = renderRows([text]);
            // ngModel writes the value through a resolved promise, so the trigger label only settles after a flush.
            await fixture.whenStable();
            fixture.detectChanges();

            // The kit select renders its current label inside the combobox trigger button.
            const trigger = element.querySelector('[role="combobox"]');
            expect(trigger?.textContent).toContain('artemisApp.exerciseManagement.table.noGroup');
        });

        it('reflects the active sort column in the header', () => {
            renderRows([quiz]);
            const titleHeader = fixture.nativeElement.querySelector('th[tumUiSortableColumn="title"]') as HTMLElement;
            expect(titleHeader.getAttribute('aria-sort')).toBe('ascending');

            component.sortBy('title');
            fixture.detectChanges();
            expect(titleHeader.getAttribute('aria-sort')).toBe('descending');
        });

        it('sorts when a sortable header is clicked', () => {
            // Proves the onSortChange adapter is wired: the kit table is controlled, so a header click only
            // emits (field, order) and this component must apply it to sortColumn/sortAsc.
            renderRows([quiz]);
            const pointsHeader = fixture.nativeElement.querySelector('th[tumUiSortableColumn="points"]') as HTMLElement;
            // The kit puts the activation on a real button inside the header, not on the <th> itself.
            const pointsSortButton = pointsHeader.querySelector('.tum-ui-sort-button') as HTMLButtonElement;

            pointsSortButton.click();
            fixture.detectChanges();
            expect(component.sortColumn()).toBe('points');
            expect(component.sortAsc()).toBe(true);
            expect(pointsHeader.getAttribute('aria-sort')).toBe('ascending');

            pointsSortButton.click();
            fixture.detectChanges();
            expect(component.sortAsc()).toBe(false);
            expect(pointsHeader.getAttribute('aria-sort')).toBe('descending');
        });
    });
});

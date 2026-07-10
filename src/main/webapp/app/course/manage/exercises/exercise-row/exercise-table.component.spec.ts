import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import dayjs from 'dayjs/esm';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseTableComponent, TableGroupChange } from 'app/course/manage/exercises/exercise-row/exercise-table.component';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';

describe('ExerciseTableComponent', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseTableComponent;
    let fixture: ComponentFixture<ExerciseTableComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseTableComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

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

        it('keeps the manual order after a drag-and-drop reorder', () => {
            fixture.componentRef.setInput('exercises', [dated1, dated2]);
            const reordered: Exercise[][] = [];
            component.rowsReordered.subscribe((rows) => reordered.push(rows));

            const container = { id: 'same' } as any;
            component.onDrop({ previousContainer: container, container, previousIndex: 0, currentIndex: 1 } as CdkDragDrop<Exercise[]>);

            expect(component.sortColumn()).toBe('manual');
            expect(reordered[0].map((e) => e.id)).toEqual([2, 1]);
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

    describe('sort header helpers', () => {
        it('reports icon and aria-sort per column state', () => {
            component.sortBy('points');
            expect(component.ariaSort('points')).toBe('ascending');
            expect(component.ariaSort('title')).toBe('none');
            expect(component.sortIcon('points')).toBe(component['faCaretUp']);
            expect(component.sortIcon('title')).toBe(component['faSort']);

            component.sortBy('points');
            expect(component.ariaSort('points')).toBe('descending');
            expect(component.sortIcon('points')).toBe(component['faCaretDown']);
        });

        it('handles keyboard sorting and prevents the default scroll', () => {
            const event = { preventDefault: vi.fn() } as unknown as Event;
            component.onSortKeydown(event, 'points');
            expect(event.preventDefault).toHaveBeenCalled();
            expect(component.sortColumn()).toBe('points');
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
            expect(component.actionsMinWidthVar()).toBeNull();

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
            expect(options[0].value).toBeUndefined();
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

        it('maps difficulty to a badge class', () => {
            expect(component.difficultyBadgeClass({ difficulty: DifficultyLevel.EASY } as Exercise)).toBe('bg-success');
            expect(component.difficultyBadgeClass({ difficulty: DifficultyLevel.MEDIUM } as Exercise)).toBe('bg-warning');
            expect(component.difficultyBadgeClass({ difficulty: DifficultyLevel.HARD } as Exercise)).toBe('bg-danger');
            expect(component.difficultyBadgeClass({} as Exercise)).toBe('bg-secondary');
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

            expect(component.quizStatusClass({ status: QuizStatus.INVISIBLE } as QuizExercise)).toBe('bg-secondary');
            expect(component.quizStatusClass({ status: QuizStatus.VISIBLE } as QuizExercise)).toBe('bg-info');
            expect(component.quizStatusClass({ status: QuizStatus.ACTIVE } as QuizExercise)).toBe('bg-success');
            expect(component.quizStatusClass({ status: QuizStatus.OPEN_FOR_PRACTICE } as QuizExercise)).toBe('bg-primary');
            expect(component.quizStatusClass({} as QuizExercise)).toBe('bg-light text-dark');
        });

        it('builds the quiz mode translation key', () => {
            expect(component.quizModeKey({ quizMode: QuizMode.SYNCHRONIZED } as QuizExercise)).toBe('artemisApp.quizExercise.quizMode.synchronized');
            expect(component.quizModeKey({} as QuizExercise)).toBe('artemisApp.quizExercise.quizMode.');
        });

        it('tracks quiz rows by lifecycle-relevant state and other rows by id', () => {
            const trackBy = component['rowTrackBy'];
            const text = { id: 1, type: ExerciseType.TEXT } as Exercise;
            expect(trackBy(0, text)).toBe(1);

            const draft = { type: ExerciseType.TEXT } as Exercise;
            expect(trackBy(0, draft)).toBe(draft);

            const quiz = { id: 2, type: ExerciseType.QUIZ, status: QuizStatus.VISIBLE, visibleToStudents: true } as QuizExercise;
            const key = trackBy(0, quiz);
            expect(key).toContain('2|');
            expect(trackBy(0, { ...quiz, status: QuizStatus.ACTIVE } as QuizExercise)).not.toBe(key);
        });
    });
});

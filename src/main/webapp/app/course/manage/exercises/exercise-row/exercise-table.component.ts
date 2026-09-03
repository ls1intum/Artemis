import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY } from 'rxjs';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faBars } from '@fortawesome/free-solid-svg-icons';
import {
    TumUiCheckboxComponent,
    TumUiSelectComponent,
    TumUiTableDirective,
    TumUiTableSortEvent,
    TumUiTableSortableColumnComponent,
    TumUiTagComponent,
    TumUiTagSeverity,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDragPreview, CdkDropList } from '@angular/cdk/drag-drop';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';
import { DifficultyLevel, Exercise, ExerciseType, IncludedInOverallScore, getExerciseUrlSegment, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { QuizExercise, QuizMode, QuizStatus } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExerciseActionsComponent } from 'app/course/manage/exercises/exercise-row/exercise-actions.component';

type SortColumn = 'title' | 'dueDate' | 'points' | 'difficulty';

/**
 * Value of the group dropdown's "no group" entry. Not `undefined`, which `tum-ui-select` reads as "nothing
 * selected" and renders as a blank trigger. A negative id cannot collide with a real group id.
 */
export const NO_GROUP_OPTION_VALUE = -1;

const DIFFICULTY_ORDER: Record<string, number> = {
    [DifficultyLevel.EASY]: 0,
    [DifficultyLevel.MEDIUM]: 1,
    [DifficultyLevel.HARD]: 2,
};

export interface TableGroupChange {
    exercise: Exercise;
    group: CourseExerciseGroup | undefined;
}

/**
 * Everything one table row renders, derived once per row instead of per change-detection cycle. Bindings must read
 * these fields rather than call the helper methods behind them, which Angular cannot memoize.
 */
interface ExerciseRow {
    /** The raw entity, still needed for drag payloads, the child components and the plain field reads. */
    exercise: Exercise;
    icon: IconProp;
    titleLink: (string | number)[] | undefined;
    releaseDate: dayjs.Dayjs | undefined;
    dueDate: dayjs.Dayjs | undefined;
    assessmentDueDate: dayjs.Dayjs | undefined;
    difficultySeverity: TumUiTagSeverity;
    owningGroupId: number | undefined;
    isQuizNonIndividual: boolean;
    nonIndividualQuizTooltip: string | undefined;
    /** i18n key for the quiz status badge, or `undefined` when no badge should be shown. */
    quizStatusLabel: string | undefined;
    /** Severity of the quiz status badge. Only read when {@link quizStatusLabel} is set, so non-quiz rows carry a default. */
    quizStatusSeverity: TumUiTagSeverity;
    /** i18n key for the quiz mode badge, or `undefined` when the quiz has no mode. */
    quizModeKey: string | undefined;
    hasCategories: boolean;
    /** True when the row has neither categories nor any quiz badge, so the "none" placeholder is shown instead. */
    showNoCategoriesPlaceholder: boolean;
    /** This row's own group-dropdown options — see {@link ExerciseTableComponent.groupOptionsFor}. */
    groupOptions: { label: string; value: number }[];
}

@Component({
    selector: 'jhi-exercise-table',
    templateUrl: './exercise-table.component.html',
    styleUrl: './exercise-table.component.scss',
    // Floor the actions column at the widest quiz-button + ellipsis width its rows report, so the main buttons
    // collapse into the ellipsis before the table scrolls. Unset when no row has quiz buttons.
    host: { '[style.--actions-min-width]': 'actionsMinWidthVar()' },
    imports: [
        RouterLink,
        FormsModule,
        FaIconComponent,
        TumUiTableDirective,
        TumUiTableSortableColumnComponent,
        TumUiSelectComponent,
        TumUiCheckboxComponent,
        TumUiTagComponent,
        TumUiTooltipDirective,
        CdkDropList,
        CdkDrag,
        CdkDragHandle,
        CdkDragPreview,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ExerciseCategoriesComponent,
        ExerciseActionsComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseTableComponent {
    protected readonly faBars = faBars;

    private readonly translateService = inject(TranslateService);

    /** Emits on every language switch, so computeds building translated strings read it to be re-derived. */
    private readonly languageChange = toSignal(this.translateService.onLangChange ?? EMPTY);

    readonly exercises = input.required<Exercise[]>();
    readonly group = input<CourseExerciseGroup | undefined>(undefined);
    readonly courseId = input.required<number>();
    readonly course = input<Course | undefined>(undefined);
    readonly showDragHandle = input<boolean>(false);
    readonly showGroupSelector = input<boolean>(false);
    readonly showTypeIcon = input<boolean>(false);
    readonly showCheckbox = input<boolean>(false);
    readonly selectedIds = input<Set<number>>(new Set());
    readonly groups = input<CourseExerciseGroup[]>([]);
    /** CDK drop-list id for this table's exercises (the owning card's id). */
    readonly dropListId = input<string>('');
    /** Ids of the sibling exercise tables this one can exchange exercises with (enables cross-group drag-and-drop). */
    readonly connectedDropLists = input<string[]>([]);

    readonly groupChange = output<TableGroupChange>();
    readonly exerciseUpdated = output<Exercise>();
    readonly exerciseDeleted = output<Exercise>();
    readonly selectionToggle = output<number>();
    readonly selectionAllChange = output<boolean>();

    /** Only the enums the template still references need a passthrough; the rest are used from TypeScript only. */
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly NO_GROUP_OPTION_VALUE = NO_GROUP_OPTION_VALUE;

    readonly sortColumn = signal<SortColumn>('title');
    readonly sortAsc = signal(true);

    readonly sortedExercises = computed(() => {
        const col = this.sortColumn();
        const asc = this.sortAsc();
        return [...this.exercises()].sort((a, b) => {
            let cmp = 0;
            switch (col) {
                case 'title':
                    cmp = (a.title ?? '').localeCompare(b.title ?? '');
                    break;
                case 'dueDate': {
                    const da = effectiveDate(a, this.effectiveGroupFor(a), 'dueDate');
                    const db = effectiveDate(b, this.effectiveGroupFor(b), 'dueDate');
                    if (da === undefined || db === undefined) {
                        // Undated exercises sort last in both directions, so pre-negate to cancel the flip below
                        // (mirrors course-exercise-cards.ts).
                        const undefinedLast = da === undefined && db === undefined ? 0 : da === undefined ? 1 : -1;
                        cmp = asc ? undefinedLast : -undefinedLast;
                    } else {
                        cmp = da.valueOf() - db.valueOf();
                    }
                    break;
                }
                case 'points':
                    cmp = (a.maxPoints ?? 0) - (b.maxPoints ?? 0);
                    break;
                case 'difficulty':
                    cmp = (DIFFICULTY_ORDER[a.difficulty ?? ''] ?? -1) - (DIFFICULTY_ORDER[b.difficulty ?? ''] ?? -1);
                    break;
            }
            return asc ? cmp : -cmp;
        });
    });

    /** The rendered rows: every value the row template needs, derived once per row rather than per change-detection cycle. */
    readonly rows = computed<ExerciseRow[]>(() => {
        // The tooltip is a translated string, so re-derive the rows on a language switch.
        this.languageChange();
        return this.sortedExercises().map((exercise) => {
            const quiz = exercise.type === ExerciseType.QUIZ ? this.asQuiz(exercise) : undefined;
            const quizStatusLabel = quiz ? this.quizStatusLabel(quiz) : undefined;
            const hasCategories = (exercise.categories?.length ?? 0) > 0;
            return {
                exercise,
                icon: this.icon(exercise),
                titleLink: this.titleLink(exercise),
                releaseDate: this.effectiveReleaseDate(exercise),
                dueDate: this.effectiveDueDate(exercise),
                assessmentDueDate: this.effectiveAssessmentDueDate(exercise),
                difficultySeverity: this.difficultySeverity(exercise),
                owningGroupId: this.owningGroupId(exercise),
                isQuizNonIndividual: this.isQuizNonIndividual(exercise),
                nonIndividualQuizTooltip: this.nonIndividualQuizTooltip(exercise),
                quizStatusLabel,
                quizStatusSeverity: quiz ? this.quizStatusSeverity(quiz) : 'secondary',
                quizModeKey: quiz?.quizMode ? this.quizModeKey(quiz) : undefined,
                hasCategories,
                showNoCategoriesPlaceholder: !hasCategories && !quiz?.quizMode && !quizStatusLabel,
                groupOptions: this.groupOptionsFor(exercise),
            };
        });
    });

    /**
     * Largest actions-column width (px) any row reported, so the shared column fits the widest quiz row. Grows only: a
     * stale floor makes the table scroll sooner but never clips. Stays 0 without quiz buttons, keeping the SCSS default.
     */
    private readonly maxQuizActionsMinWidth = signal(0);
    /** CSS value for the actions-column floor, or undefined when no quiz buttons are present. */
    readonly actionsMinWidthVar = computed(() => {
        const width = this.maxQuizActionsMinWidth();
        return width > 0 ? `${width}px` : undefined;
    });

    onQuizActionsMinWidth(width: number): void {
        if (width > this.maxQuizActionsMinWidth()) {
            this.maxQuizActionsMinWidth.set(width);
        }
    }

    readonly allSelected = computed(() => {
        const ids = this.selectedIds();
        const exercises = this.sortedExercises();
        return exercises.length > 0 && exercises.every((e) => e.id === undefined || ids.has(e.id));
    });

    readonly someSelected = computed(() => {
        const ids = this.selectedIds();
        return this.sortedExercises().some((e) => e.id !== undefined && ids.has(e.id)) && !this.allSelected();
    });

    /**
     * Precomputed exercise-id → owning-group lookup, rebuilt only when {@link groups} changes. Keeps
     * {@link owningGroupForExercise} O(1); it is called from the sort comparator and every date cell.
     */
    private readonly owningGroupByExerciseId = computed<ReadonlyMap<number, CourseExerciseGroup>>(() => {
        const map = new Map<number, CourseExerciseGroup>();
        for (const group of this.groups()) {
            for (const member of group.exercises ?? []) {
                if (member.id !== undefined) {
                    map.set(member.id, group);
                }
            }
        }
        return map;
    });

    /**
     * The group-dropdown options for one exercise, filtered by what it may actually target (mirrors the server-side
     * guards in `ExerciseVariantGroupResource.setExerciseVariantGroup`):
     * - a `UserStoryExercise` may only move between milestone groups, and can never be ungrouped (it always belongs
     *   to one — see `MilestoneExerciseGroup`), so "no group" is omitted for it;
     * - a plain `ProgrammingExercise` may never join a milestone group (it has no relevant task/test-case wiring to
     *   one), so those are excluded for it;
     * - every other type (quiz/text/modeling/file-upload) may join either kind of group.
     */
    private groupOptionsFor(exercise: Exercise): { label: string; value: number }[] {
        const isUserStory = exercise.type === ExerciseType.USER_STORY;
        const isPlainProgramming = exercise.type === ExerciseType.PROGRAMMING;
        const eligibleGroups = this.groups().filter((group) => {
            if (isUserStory) {
                return group.type === 'milestone';
            }
            if (isPlainProgramming) {
                return group.type !== 'milestone';
            }
            return true;
        });
        const groupOptions = eligibleGroups
            .filter((g): g is CourseExerciseGroup & { id: number } => g.id !== undefined)
            .map((g) => ({
                label: g.title ?? this.translateService.instant('artemisApp.exerciseManagement.card.group', { id: g.id }),
                value: g.id,
            }));
        if (isUserStory) {
            return groupOptions;
        }
        return [{ label: this.translateService.instant('artemisApp.exerciseManagement.table.noGroup'), value: NO_GROUP_OPTION_VALUE }, ...groupOptions];
    }

    sortBy(col: SortColumn): void {
        if (this.sortColumn() === col) {
            this.sortAsc.set(!this.sortAsc());
        } else {
            this.sortColumn.set(col);
            this.sortAsc.set(true);
        }
    }

    /**
     * Applies a sort requested by a `[tumUiSortableColumn]` header. The kit table is controlled: it only reports the
     * field and order (1 ascending / -1 descending), the state stays here. Its toggle rule mirrors {@link sortBy}.
     */
    protected onSortChange(event: TumUiTableSortEvent): void {
        this.sortColumn.set(event.field as SortColumn);
        this.sortAsc.set(event.order > 0);
    }

    onDrop(event: CdkDragDrop<Exercise[]>): void {
        // Same-container drops are ignored: a manual order would be discarded by the next card rebuild, so
        // drag-and-drop only moves exercises between groups.
        if (event.previousContainer !== event.container) {
            this.groupChange.emit({ exercise: event.item.data, group: this.group() });
        }
    }

    urlSegment(exercise: Exercise): string {
        return getExerciseUrlSegment(exercise.type);
    }

    /** Detail-page link for the exercise, or `undefined` for unsaved drafts (RouterLink disables navigation for null/undefined). */
    titleLink(exercise: Exercise): (string | number)[] | undefined {
        if (exercise.id === undefined) {
            return undefined;
        }
        return ['/course-management', this.courseId(), this.urlSegment(exercise), exercise.id];
    }

    icon(exercise: Exercise) {
        return getIcon(exercise.type);
    }

    /**
     * The group whose timeline governs this exercise: the card's group in the group view, otherwise resolved
     * from the full groups list, so the displayed dates stay consistent across views.
     */
    private effectiveGroupFor(exercise: Exercise): CourseExerciseGroup | undefined {
        return this.group() ?? this.owningGroupForExercise(exercise);
    }

    effectiveReleaseDate(exercise: Exercise) {
        return effectiveDate(exercise, this.effectiveGroupFor(exercise), 'releaseDate');
    }

    effectiveDueDate(exercise: Exercise) {
        return effectiveDate(exercise, this.effectiveGroupFor(exercise), 'dueDate');
    }

    effectiveAssessmentDueDate(exercise: Exercise) {
        return effectiveDate(exercise, this.effectiveGroupFor(exercise), 'assessmentDueDate');
    }

    difficultySeverity(exercise: Exercise): TumUiTagSeverity {
        switch (exercise.difficulty) {
            case DifficultyLevel.EASY:
                return 'success';
            case DifficultyLevel.MEDIUM:
                return 'warn';
            case DifficultyLevel.HARD:
                return 'danger';
            default:
                return 'secondary';
        }
    }

    owningGroupForExercise(exercise: Exercise): CourseExerciseGroup | undefined {
        if (exercise.id !== undefined) {
            return this.owningGroupByExerciseId().get(exercise.id);
        }
        // Unsaved drafts have no id yet, so fall back to reference identity to still resolve their owning group.
        return this.groups().find((g) => g.exercises?.some((e) => e === exercise));
    }

    owningGroupId(exercise: Exercise): number | undefined {
        return this.owningGroupForExercise(exercise)?.id;
    }

    onGroupSelect(exercise: Exercise, groupId: number | undefined): void {
        // The dropdown reports "no group" as the sentinel (see NO_GROUP_OPTION_VALUE); map it back to "no group".
        const group = groupId === NO_GROUP_OPTION_VALUE ? undefined : this.groups().find((g) => g.id === groupId);
        this.groupChange.emit({ exercise, group });
    }

    asQuiz(exercise: Exercise): QuizExercise {
        return exercise;
    }

    exerciseTrackKey(exercise: Exercise): unknown {
        if (exercise.type !== ExerciseType.QUIZ || exercise.id === undefined) return exercise.id ?? exercise;
        const q = exercise as QuizExercise;
        // A same-id row may not re-evaluate in zoneless Angular, so key on the properties that drive the
        // lifecycle buttons to force a recreate when they change.
        return `${exercise.id}|${q.exerciseVariantGroup?.id ?? ''}|${q.status ?? ''}|${q.visibleToStudents ?? ''}`;
    }

    /** Only individual-mode quizzes support per-student dates, so only they can share a group's timeline. */
    isQuizNonIndividual(exercise: Exercise): boolean {
        return exercise.type === ExerciseType.QUIZ && this.asQuiz(exercise).quizMode !== undefined && this.asQuiz(exercise).quizMode !== QuizMode.INDIVIDUAL;
    }

    nonIndividualQuizTooltip(exercise: Exercise): string | undefined {
        return this.isQuizNonIndividual(exercise) ? this.translateService.instant('artemisApp.exerciseManagement.table.nonIndividualQuizTooltip') : undefined;
    }

    /** Translation key for the quiz status badge, or `undefined` when no badge should be shown. */
    quizStatusLabel(exercise: QuizExercise): string | undefined {
        switch (exercise.status) {
            case QuizStatus.INVISIBLE:
                return 'artemisApp.quizExercise.quizStatus.invisible';
            case QuizStatus.VISIBLE:
                return 'artemisApp.quizExercise.quizStatus.visible';
            case QuizStatus.ACTIVE:
                return 'artemisApp.quizExercise.quizStatus.active';
            case QuizStatus.OPEN_FOR_PRACTICE:
                return 'artemisApp.quizExercise.practiceMode';
            default:
                return undefined;
        }
    }

    /** Translation key for the quiz mode badge (e.g. `artemisApp.quizExercise.quizMode.synchronized`). */
    quizModeKey(exercise: QuizExercise): string {
        return `artemisApp.quizExercise.quizMode.${(exercise.quizMode ?? '').toLowerCase()}`;
    }

    /**
     * The practice state shares `info` with the visible state: the kit tag has no `primary`, and its `secondary`
     * default would be indistinguishable from the badges beside it. A quiz has one status, so the two never collide.
     */
    quizStatusSeverity(exercise: QuizExercise): TumUiTagSeverity {
        switch (exercise.status) {
            case QuizStatus.INVISIBLE:
                return 'secondary';
            case QuizStatus.VISIBLE:
                return 'info';
            case QuizStatus.ACTIVE:
                return 'success';
            case QuizStatus.OPEN_FOR_PRACTICE:
                return 'info';
            default:
                return 'secondary';
        }
    }
}

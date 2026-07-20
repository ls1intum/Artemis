import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { EMPTY } from 'rxjs';
import dayjs from 'dayjs/esm';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCaretDown, faCaretUp, faSort } from '@fortawesome/free-solid-svg-icons';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
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

/** The severities a PrimeNG `p-tag` accepts. */
type TagSeverity = 'success' | 'secondary' | 'info' | 'warn' | 'danger' | 'contrast';

type SortColumn = 'title' | 'dueDate' | 'points' | 'difficulty';

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
 * Everything one table row renders, derived once per row instead of per change-detection cycle. Template bindings must
 * read these fields rather than call the (argument-taking) helper methods they are built from — a method call in a
 * binding cannot be memoized by Angular and re-runs on every check, which on a table this size adds up.
 */
interface ExerciseRow {
    /** The raw entity, still needed for drag payloads, the child components and the plain field reads. */
    exercise: Exercise;
    icon: IconProp;
    titleLink: (string | number)[] | undefined;
    releaseDate: dayjs.Dayjs | undefined;
    dueDate: dayjs.Dayjs | undefined;
    assessmentDueDate: dayjs.Dayjs | undefined;
    difficultySeverity: TagSeverity;
    owningGroupId: number | undefined;
    isQuizNonIndividual: boolean;
    nonIndividualQuizTooltip: string | undefined;
    /** i18n key for the quiz status badge, or `undefined` when no badge should be shown. */
    quizStatusLabel: string | undefined;
    /** `undefined` renders the tag in the brand primary colour — see {@link ExerciseTableComponent.quizStatusSeverity}. */
    quizStatusSeverity: TagSeverity | undefined;
    /** i18n key for the quiz mode badge, or `undefined` when the quiz has no mode. */
    quizModeKey: string | undefined;
    hasCategories: boolean;
    /** True when the row has neither categories nor any quiz badge, so the "none" placeholder is shown instead. */
    showNoCategoriesPlaceholder: boolean;
}

/** Sort indicator (caret icon + `aria-sort` value) for one column header. */
interface SortIndicator {
    icon: IconProp;
    ariaSort: 'ascending' | 'descending' | 'none';
}

@Component({
    selector: 'jhi-exercise-table',
    templateUrl: './exercise-table.component.html',
    styleUrl: './exercise-table.component.scss',
    // Floor the actions column at `--actions-min-width` — the widest always-visible quiz-button + ellipsis width the rows
    // actually report — so it collapses every main button into the ellipsis before the table scrolls, yet never clips the
    // (non-collapsing) quiz buttons. Stays unset when no row has quiz buttons, so the column collapses fully (SCSS default).
    host: { '[style.--actions-min-width]': 'actionsMinWidthVar()' },
    imports: [
        RouterLink,
        FormsModule,
        FaIconComponent,
        TableModule,
        SelectModule,
        CheckboxModule,
        TagModule,
        TooltipModule,
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
    private readonly translateService = inject(TranslateService);

    /**
     * Emits on every language switch. `TranslateService.instant` is not signal-tracked, so any computed that builds a
     * translated string must read this to be re-derived when the language changes (same approach as ArtemisTranslatePipe).
     */
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
    readonly variantAdded = output<Exercise>();
    readonly selectionToggle = output<number>();
    readonly selectionAllChange = output<boolean>();

    /** Only the enums the template still references need a passthrough; the rest are used from TypeScript only. */
    protected readonly IncludedInOverallScore = IncludedInOverallScore;

    protected readonly faSort = faSort;
    protected readonly faCaretUp = faCaretUp;
    protected readonly faCaretDown = faCaretDown;

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
                        // Undated exercises must sort last regardless of asc/desc. The shared `asc ? cmp : -cmp` flip
                        // below negates cmp for desc, so pre-negate here for desc to cancel that out and keep the
                        // "undefined sorts last" result stable in both directions (mirrors course-exercise-cards.ts).
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
                quizStatusSeverity: quiz ? this.quizStatusSeverity(quiz) : undefined,
                quizModeKey: quiz?.quizMode ? this.quizModeKey(quiz) : undefined,
                hasCategories,
                showNoCategoriesPlaceholder: !hasCategories && !quiz?.quizMode && !quizStatusLabel,
            };
        });
    });

    /** Caret icon and `aria-sort` value per sortable column, so the header does not call a method per binding. */
    readonly sortIndicators = computed<Record<SortColumn, SortIndicator>>(() => {
        const indicatorFor = (column: SortColumn): SortIndicator => ({ icon: this.sortIcon(column), ariaSort: this.ariaSort(column) });
        return {
            title: indicatorFor('title'),
            dueDate: indicatorFor('dueDate'),
            points: indicatorFor('points'),
            difficulty: indicatorFor('difficulty'),
        };
    });

    /**
     * Largest actions-column width (px) any row has reported for its always-visible quiz buttons + ellipsis. Kept as a
     * running max so the shared column fits the widest quiz row. It only grows: if the widest quiz row later disappears
     * the floor may stay marginally larger than needed, which at worst makes the table scroll a touch sooner — never
     * clips a quiz button. Stays 0 when no row has quiz lifecycle buttons (no quizzes, or quizzes in a state with no
     * actions), so the column falls back to the narrow default and the ellipsis can collapse fully.
     */
    private readonly maxQuizActionsMinWidth = signal(0);
    /** CSS value for the actions-column floor, or undefined when no quiz buttons are present (so the SCSS default applies). */
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
     * {@link owningGroupForExercise} O(1) instead of re-scanning every group (and its members) per call — it is invoked
     * from the sort comparator (once per comparison) and from every effective-date cell binding on each row.
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

    readonly groupOptions = computed(() => {
        // The labels are translated strings, so rebuild the options on a language switch.
        this.languageChange();
        return [
            { label: this.translateService.instant('artemisApp.exerciseManagement.table.noGroup'), value: undefined as number | undefined },
            ...this.groups().map((g) => ({
                label: g.title ?? this.translateService.instant('artemisApp.exerciseManagement.card.group', { id: g.id }),
                value: g.id,
            })),
        ];
    });

    sortBy(col: SortColumn): void {
        if (this.sortColumn() === col) {
            this.sortAsc.set(!this.sortAsc());
        } else {
            this.sortColumn.set(col);
            this.sortAsc.set(true);
        }
    }

    sortIcon(col: SortColumn) {
        if (this.sortColumn() !== col) return this.faSort;
        return this.sortAsc() ? this.faCaretUp : this.faCaretDown;
    }

    /** Current sort state of a column for `aria-sort`, so assistive tech announces the active order. */
    ariaSort(col: SortColumn): 'ascending' | 'descending' | 'none' {
        if (this.sortColumn() !== col) return 'none';
        return this.sortAsc() ? 'ascending' : 'descending';
    }

    /** Keyboard equivalent of the header click; prevents default so Space does not scroll the page. */
    onSortKeydown(event: Event, col: SortColumn): void {
        event.preventDefault();
        this.sortBy(col);
    }

    onDrop(event: CdkDragDrop<Exercise[]>): void {
        // Same-container drops are ignored: a manual order would only live in the rendered card and be discarded by the
        // next rebuild (search, view switch, group refresh, reload), so drag-and-drop is limited to moving between groups.
        if (event.previousContainer !== event.container) {
            // Dropped from another group's table: move the exercise into this table's group.
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
     * The group whose timeline governs this exercise. In the group view the card's group is passed in
     * directly; in the type/week/list views the card has no single group, so we resolve the exercise's
     * owning group from the full groups list. This keeps the displayed dates consistent across all views.
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

    difficultySeverity(exercise: Exercise): TagSeverity {
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
        const group = this.groups().find((g) => g.id === groupId);
        this.groupChange.emit({ exercise, group });
    }

    asQuiz(exercise: Exercise): QuizExercise {
        return exercise;
    }

    exerciseTrackKey(exercise: Exercise): unknown {
        if (exercise.type !== ExerciseType.QUIZ || exercise.id === undefined) return exercise.id ?? exercise;
        const q = exercise as QuizExercise;
        // In zoneless Angular, ngFor embedded views may not re-evaluate when the row gets a new
        // object reference with the same id. Including the properties that drive lifecycle-button
        // rendering forces the row to be destroyed/recreated when they change, so the fresh
        // exercise-actions instance always sees the up-to-date exercise.
        return `${exercise.id}|${q.exerciseVariantGroup?.id ?? ''}|${q.status ?? ''}|${q.visibleToStudents ?? ''}`;
    }

    protected readonly rowTrackBy = (_index: number, row: ExerciseRow): unknown => this.exerciseTrackKey(row.exercise);

    /**
     * Only individual-mode quizzes support per-student dates, so only they can reasonably share a group's timeline.
     * Synchronized/batched quizzes must stay out of groups; the server enforces this independently.
     */
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
     * Practice mode returns `undefined` on purpose: `p-tag` applies a severity class only for the six named
     * severities, so an unset severity falls back to the base tag, which the theme paints in the brand primary
     * colour — the equivalent of the `bg-primary` badge this replaced, and distinct from the (cyan) visible state.
     */
    quizStatusSeverity(exercise: QuizExercise): TagSeverity | undefined {
        switch (exercise.status) {
            case QuizStatus.INVISIBLE:
                return 'secondary';
            case QuizStatus.VISIBLE:
                return 'info';
            case QuizStatus.ACTIVE:
                return 'success';
            case QuizStatus.OPEN_FOR_PRACTICE:
                return undefined;
            default:
                return 'secondary';
        }
    }
}

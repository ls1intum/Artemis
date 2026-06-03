import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { SelectButtonModule } from 'primeng/selectbutton';
import { PanelModule } from 'primeng/panel';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { CheckboxModule } from 'primeng/checkbox';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import {
    faCalendarDays,
    faCheck,
    faCircleInfo,
    faCode,
    faFileExport,
    faFileImport,
    faGear,
    faGripVertical,
    faLayerGroup,
    faList,
    faPen,
    faPlus,
} from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType, getExerciseUrlSegment, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { ExerciseRowCompactComponent } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-row-compact.component';
import { ExerciseRowColumnarComponent } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-row-columnar.component';
import { ExerciseTableComponent, TableGroupChange } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-table.component';
import { ExerciseManagementDevSettingsModalComponent } from 'app/core/course/manage/exercises-experimental/dev-settings/exercise-management-dev-settings-modal.component';
import { ExerciseManagementDevSettingsService } from 'app/core/course/manage/exercises-experimental/dev-settings/exercise-management-dev-settings.service';
import { AddModalMode, ExerciseAddModalComponent } from 'app/core/course/manage/exercises-experimental/create-modal/exercise-add-modal.component';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { CourseTitleBarTitleDirective } from 'app/core/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarActionsDirective } from 'app/core/course/shared/directives/course-title-bar-actions.directive';
import { CourseTitleBarToolbarDirective } from 'app/core/course/shared/directives/course-title-bar-toolbar.directive';

type View = 'type' | 'week' | 'group' | 'list';
type SortField = 'title' | 'dueDate' | 'releaseDate' | 'assessmentDueDate' | 'points' | 'difficulty';

interface Bucket {
    id: string;
    title: string;
    icon?: IconProp;
    group?: CourseExerciseGroup;
    exerciseType?: ExerciseType;
    exercises: Exercise[];
}

const TYPE_ORDER: ExerciseType[] = [ExerciseType.PROGRAMMING, ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD];
const TYPE_TITLES: Record<string, string> = {
    [ExerciseType.PROGRAMMING]: 'Programming',
    [ExerciseType.QUIZ]: 'Quiz',
    [ExerciseType.MODELING]: 'Modeling',
    [ExerciseType.TEXT]: 'Text',
    [ExerciseType.FILE_UPLOAD]: 'File Upload',
};

const EXERCISE_TYPE_CREATE_ROUTES: Partial<Record<ExerciseType, string>> = {
    [ExerciseType.PROGRAMMING]: 'programming-exercises/new',
    [ExerciseType.QUIZ]: 'quiz-exercises/new',
    [ExerciseType.MODELING]: 'modeling-exercises/new',
    [ExerciseType.TEXT]: 'text-exercises/new',
    [ExerciseType.FILE_UPLOAD]: 'file-upload-exercises/new',
};

const ALL_EXERCISE_TYPES: { label: string; type: ExerciseType }[] = [
    { label: 'Programming', type: ExerciseType.PROGRAMMING },
    { label: 'Quiz', type: ExerciseType.QUIZ },
    { label: 'Modeling', type: ExerciseType.MODELING },
    { label: 'Text', type: ExerciseType.TEXT },
    { label: 'File Upload', type: ExerciseType.FILE_UPLOAD },
];

const DIFFICULTY_ORDER: Record<string, number> = { EASY: 0, MEDIUM: 1, HARD: 2 };

@Component({
    selector: 'jhi-course-management-exercises-experimental',
    templateUrl: './course-management-exercises-experimental.component.html',
    styleUrl: './course-management-exercises-experimental.component.scss',
    imports: [
        RouterLink,
        FormsModule,
        SelectButtonModule,
        SelectModule,
        CheckboxModule,
        PanelModule,
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        TooltipModule,
        CdkDropList,
        CdkDrag,
        CdkDragHandle,
        FaIconComponent,
        ExerciseRowCompactComponent,
        ExerciseRowColumnarComponent,
        ExerciseTableComponent,
        ExerciseManagementDevSettingsModalComponent,
        ExerciseAddModalComponent,
        SearchFilterComponent,
        ArtemisDatePipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarActionsDirective,
        CourseTitleBarToolbarDirective,
    ],
})
export class CourseManagementExercisesExperimentalComponent implements OnInit {
    protected readonly faGripVertical = faGripVertical;
    protected readonly faGear = faGear;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faFileExport = faFileExport;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faPen = faPen;
    protected readonly faCheck = faCheck;
    protected readonly faCode = faCode;
    protected readonly ExerciseType = ExerciseType;

    protected readonly ALL_EXERCISE_TYPES = ALL_EXERCISE_TYPES;
    protected readonly EXERCISE_TYPE_CREATE_ROUTES = EXERCISE_TYPE_CREATE_ROUTES;

    readonly viewOptions: { label: string; value: View; icon: IconProp }[] = [
        { label: 'List', value: 'list', icon: faList },
        { label: 'Type', value: 'type', icon: faCode },
        { label: 'Week', value: 'week', icon: faCalendarDays },
        { label: 'Group', value: 'group', icon: faLayerGroup },
    ];

    readonly sortOptions: { label: string; value: SortField }[] = [
        { label: 'Title', value: 'title' },
        { label: 'Due Date', value: 'dueDate' },
        { label: 'Release Date', value: 'releaseDate' },
        { label: 'Assessment Due', value: 'assessmentDueDate' },
        { label: 'Points', value: 'points' },
        { label: 'Difficulty', value: 'difficulty' },
    ];

    readonly view = signal<View>('type');
    readonly search = signal('');
    readonly sortField = signal<SortField>('title');
    readonly settingsVisible = signal(false);

    readonly course = signal<Course | undefined>(undefined);
    readonly exercises = signal<Exercise[]>([]);
    readonly groups = signal<CourseExerciseGroup[]>([]);
    readonly buckets = signal<Bucket[]>([]);

    readonly selectedIds = signal<Set<number>>(new Set());
    readonly editingGroupIds = signal<Set<number>>(new Set());
    private readonly groupSnapshots = new Map<
        number,
        { title?: string; releaseDate?: dayjs.Dayjs; startDate?: dayjs.Dayjs; dueDate?: dayjs.Dayjs; assessmentDueDate?: dayjs.Dayjs; maxPoints?: number; handInLimit?: number }
    >();
    private readonly newlyCreatedGroupIds = new Set<number>();
    readonly addModalVisible = signal(false);
    readonly addModalMode = signal<AddModalMode>('create');

    readonly isGroup = computed(() => this.view() === 'group');
    readonly exerciseCount = computed(() => this.exercises().length);
    readonly visibleCount = computed(() => this.buckets().reduce((sum, bucket) => sum + bucket.exercises.length, 0));
    readonly courseId = computed(() => this.course()?.id);
    readonly selectedCount = computed(() => this.selectedIds().size);

    readonly selectedTypes = computed(() => {
        const ids = this.selectedIds();
        return new Set(
            this.exercises()
                .filter((e) => e.id !== undefined && ids.has(e.id!))
                .map((e) => e.type),
        );
    });
    readonly allSelectedAreProgramming = computed(() => {
        const t = this.selectedTypes();
        return t.size === 1 && t.has(ExerciseType.PROGRAMMING);
    });

    private readonly route = inject(ActivatedRoute);
    private readonly mockService = inject(ExerciseManagementMockService);
    protected readonly devSettings = inject(ExerciseManagementDevSettingsService);

    ngOnInit(): void {
        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
        });

        this.exercises.set(this.mockService.getExercises());
        this.groups.set(this.mockService.getGroups());
        this.buildBuckets();
    }

    onViewChange(view: View): void {
        this.view.set(view);
        this.editingGroupIds.set(new Set());
        this.buildBuckets();
    }

    onSearchChange(term: string): void {
        this.search.set(term);
        this.buildBuckets();
    }

    onSortChange(field: SortField): void {
        this.sortField.set(field);
        this.buildBuckets();
    }

    owningGroup(exercise: Exercise): CourseExerciseGroup | undefined {
        return this.groups().find((group) => group.exercises?.some((member) => member.id === exercise.id));
    }

    dropExercise(bucket: Bucket, event: CdkDragDrop<Exercise[]>): void {
        moveItemInArray(bucket.exercises, event.previousIndex, event.currentIndex);
        this.buckets.set([...this.buckets()]);
    }

    dropBucket(event: CdkDragDrop<Bucket[]>): void {
        const buckets = [...this.buckets()];
        moveItemInArray(buckets, event.previousIndex, event.currentIndex);
        this.buckets.set(buckets);
    }

    toggleSelection(id: number): void {
        const current = new Set(this.selectedIds());
        if (current.has(id)) {
            current.delete(id);
        } else {
            current.add(id);
        }
        this.selectedIds.set(current);
    }

    isSelected(id: number): boolean {
        return this.selectedIds().has(id);
    }

    clearSelection(): void {
        this.selectedIds.set(new Set());
    }

    createGroupForExercise(exercise: Exercise): void {
        const nextId = Math.max(0, ...this.groups().map((g) => g.id ?? 0)) + 1;
        const newGroup: CourseExerciseGroup = { id: nextId, title: `Group ${nextId}`, order: nextId, exercises: [exercise] };
        const stripped = this.groups().map((g) => ({ ...g, exercises: (g.exercises ?? []).filter((e) => e.id !== exercise.id) }));
        this.groups.set([...stripped, newGroup]);
        this.buildBuckets();
    }

    changeExerciseGroup(exercise: Exercise, newGroup: CourseExerciseGroup | undefined): void {
        const updated = this.groups().map((g) => ({
            ...g,
            exercises: (g.exercises ?? []).filter((e) => e.id !== exercise.id),
        }));
        if (newGroup) {
            const target = updated.find((g) => g.id === newGroup.id);
            if (target) {
                target.exercises = [...(target.exercises ?? []), exercise];
            }
        }
        this.groups.set(updated);
        this.buildBuckets();
    }

    deleteSelected(): void {
        const ids = this.selectedIds();
        this.exercises.set(this.exercises().filter((e) => e.id === undefined || !ids.has(e.id)));
        this.clearSelection();
        this.buildBuckets();
    }

    editSelectedExercises(): void {}
    downloadReposSelected(): void {}
    consistencyCheckSelected(): void {}

    openCreateModal(): void {
        this.addModalMode.set('create');
        this.addModalVisible.set(true);
    }

    openImportModal(): void {
        this.addModalMode.set('import');
        this.addModalVisible.set(true);
    }

    openExportModal(): void {
        this.addModalMode.set('export');
        this.addModalVisible.set(true);
    }

    openAddModal(): void {
        this.addModalMode.set('unified');
        this.addModalVisible.set(true);
    }

    onAddModalGroupCreate(): void {
        this.view.set('group');
        this.editingGroupIds.set(new Set());
        const nextId = Math.max(0, ...this.groups().map((g) => g.id ?? 0)) + 1;
        const newGroup: CourseExerciseGroup = { id: nextId, title: `Group ${nextId}`, order: nextId, exercises: [] };
        this.groups.set([...this.groups(), newGroup]);
        this.newlyCreatedGroupIds.add(nextId);
        this.buildBuckets();
        this.startEditGroup(nextId);
    }

    onTableGroupChange(event: TableGroupChange): void {
        this.changeExerciseGroup(event.exercise, event.group);
    }

    onTableGroupCreate(exercise: Exercise): void {
        this.createGroupForExercise(exercise);
    }

    onTableSelectionAllChange(bucket: Bucket, selectAll: boolean): void {
        const current = new Set(this.selectedIds());
        for (const exercise of bucket.exercises) {
            if (exercise.id !== undefined) {
                if (selectAll) {
                    current.add(exercise.id);
                } else {
                    current.delete(exercise.id);
                }
            }
        }
        this.selectedIds.set(current);
    }

    onTableRowsReordered(bucket: Bucket, reorderedExercises: Exercise[]): void {
        bucket.exercises = reorderedExercises;
        this.buckets.set([...this.buckets()]);
    }

    onExerciseUpdated(updated: Exercise): void {
        this.exercises.set(this.exercises().map((e) => (e.id === updated.id ? updated : e)));
        this.groups.set(
            this.groups().map((g) => ({
                ...g,
                exercises: (g.exercises ?? []).map((e) => (e.id === updated.id ? updated : e)),
            })),
        );
        this.buildBuckets();
    }

    createRouteForType(courseId: number, type: ExerciseType): (string | number)[] {
        const segment = EXERCISE_TYPE_CREATE_ROUTES[type];
        return segment ? ['/course-management', courseId, segment] : [];
    }

    private hasSearch(): boolean {
        return this.search().trim().length > 0;
    }

    private matches(exercise: Exercise): boolean {
        const term = this.search().trim().toLowerCase();
        return !term || (exercise.title ?? '').toLowerCase().includes(term);
    }

    private visibleExercises(): Exercise[] {
        return this.exercises().filter((exercise) => this.matches(exercise));
    }

    private sortExercises(exercises: Exercise[]): Exercise[] {
        const field = this.sortField();
        return [...exercises].sort((a, b) => {
            switch (field) {
                case 'title':
                    return (a.title ?? '').localeCompare(b.title ?? '');
                case 'dueDate': {
                    const da = effectiveDate(a, this.owningGroup(a), 'dueDate');
                    const db = effectiveDate(b, this.owningGroup(b), 'dueDate');
                    return (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
                }
                case 'releaseDate': {
                    const da = effectiveDate(a, this.owningGroup(a), 'releaseDate');
                    const db = effectiveDate(b, this.owningGroup(b), 'releaseDate');
                    return (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
                }
                case 'assessmentDueDate': {
                    const da = effectiveDate(a, this.owningGroup(a), 'assessmentDueDate');
                    const db = effectiveDate(b, this.owningGroup(b), 'assessmentDueDate');
                    return (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
                }
                case 'points':
                    return (a.maxPoints ?? 0) - (b.maxPoints ?? 0);
                case 'difficulty':
                    return (DIFFICULTY_ORDER[a.difficulty ?? ''] ?? -1) - (DIFFICULTY_ORDER[b.difficulty ?? ''] ?? -1);
                default:
                    return 0;
            }
        });
    }

    private buildBuckets(): void {
        switch (this.view()) {
            case 'group':
                this.buckets.set(this.buildGroupBuckets());
                break;
            case 'type':
                this.buckets.set(this.buildTypeBuckets());
                break;
            case 'week':
                this.buckets.set(this.buildWeekBuckets());
                break;
            case 'list':
                this.buckets.set(this.buildListBuckets());
                break;
        }
    }

    private buildListBuckets(): Bucket[] {
        const exercises = this.sortExercises(this.visibleExercises());
        if (exercises.length === 0) return [];
        return [{ id: 'all', title: 'All Exercises', exercises }];
    }

    private buildGroupBuckets(): Bucket[] {
        const groupedIds = new Set<number>();
        const searching = this.hasSearch();
        const buckets: Bucket[] = this.groups()
            .slice()
            .sort((a, b) => (a.order ?? 0) - (b.order ?? 0))
            .map((group) => {
                const members = group.exercises ?? [];
                members.forEach((exercise) => exercise.id !== undefined && groupedIds.add(exercise.id));
                return {
                    id: `group-${group.id}`,
                    title: group.title ?? `Group ${group.id}`,
                    group,
                    exercises: this.sortExercises(members.filter((exercise) => this.matches(exercise))),
                };
            })
            .filter((bucket) => !searching || bucket.exercises.length > 0);

        const ungrouped = this.sortExercises(this.visibleExercises().filter((exercise) => exercise.id === undefined || !groupedIds.has(exercise.id)));
        if (ungrouped.length > 0) {
            buckets.push({ id: 'ungrouped', title: 'Ungrouped', exercises: ungrouped });
        }
        return buckets;
    }

    private buildTypeBuckets(): Bucket[] {
        return TYPE_ORDER.map((type) => ({
            id: `type-${type}`,
            title: TYPE_TITLES[type] ?? type,
            icon: getIcon(type),
            exerciseType: type,
            exercises: this.sortExercises(this.visibleExercises().filter((exercise) => exercise.type === type)),
        })).filter((bucket) => bucket.exercises.length > 0);
    }

    private buildWeekBuckets(): Bucket[] {
        const startOf = (exercise: Exercise): dayjs.Dayjs | undefined =>
            effectiveDate(exercise, this.owningGroup(exercise), 'startDate') ?? effectiveDate(exercise, this.owningGroup(exercise), 'releaseDate');

        const visible = this.visibleExercises();
        const dated = visible.filter((exercise) => startOf(exercise));
        const undated = visible.filter((exercise) => !startOf(exercise));

        const base = dated.reduce<dayjs.Dayjs | undefined>((min, exercise) => {
            const date = startOf(exercise)!;
            return !min || date.isBefore(min) ? date : min;
        }, undefined);

        const byWeek = new Map<number, Exercise[]>();
        for (const exercise of dated) {
            const weekIndex = base ? startOf(exercise)!.diff(base, 'week') : 0;
            let list = byWeek.get(weekIndex);
            if (!list) {
                list = [];
                byWeek.set(weekIndex, list);
            }
            list.push(exercise);
        }

        const buckets: Bucket[] = [...byWeek.keys()]
            .sort((a, b) => a - b)
            .map((weekIndex) => ({ id: `week-${weekIndex}`, title: `Week ${weekIndex + 1}`, exercises: this.sortExercises(byWeek.get(weekIndex)!) }));

        if (undated.length > 0) {
            buckets.push({ id: 'unscheduled', title: 'Unscheduled', exercises: this.sortExercises(undated) });
        }
        return buckets;
    }

    isEditingGroup(id: number | undefined): boolean {
        return id !== undefined && this.editingGroupIds().has(id);
    }

    startEditGroup(id: number): void {
        const group = this.groups().find((g) => g.id === id);
        if (group) {
            this.groupSnapshots.set(id, {
                title: group.title,
                releaseDate: group.releaseDate,
                startDate: group.startDate,
                dueDate: group.dueDate,
                assessmentDueDate: group.assessmentDueDate,
                maxPoints: group.maxPoints,
                handInLimit: group.handInLimit,
            });
        }
        const next = new Set(this.editingGroupIds());
        next.add(id);
        this.editingGroupIds.set(next);
    }

    cancelEditGroup(id: number): void {
        const snapshot = this.groupSnapshots.get(id);
        if (snapshot !== undefined) {
            const group = this.groups().find((g) => g.id === id);
            if (group) {
                group.title = snapshot.title;
                group.releaseDate = snapshot.releaseDate;
                group.startDate = snapshot.startDate;
                group.dueDate = snapshot.dueDate;
                group.assessmentDueDate = snapshot.assessmentDueDate;
                group.maxPoints = snapshot.maxPoints;
                group.handInLimit = snapshot.handInLimit;
            }
            this.groupSnapshots.delete(id);
        }
        if (this.newlyCreatedGroupIds.has(id)) {
            this.newlyCreatedGroupIds.delete(id);
            this.groups.set(this.groups().filter((g) => g.id !== id));
        }
        const next = new Set(this.editingGroupIds());
        next.delete(id);
        this.editingGroupIds.set(next);
        this.buildBuckets();
    }

    saveGroup(id: number): void {
        this.groupSnapshots.delete(id);
        this.newlyCreatedGroupIds.delete(id);
        const next = new Set(this.editingGroupIds());
        next.delete(id);
        this.editingGroupIds.set(next);
    }

    getGroupDateValue(date: dayjs.Dayjs | undefined): string {
        return date?.format('YYYY-MM-DDTHH:mm') ?? '';
    }

    setGroupDate(group: CourseExerciseGroup, field: 'releaseDate' | 'startDate' | 'dueDate' | 'assessmentDueDate', value: string): void {
        group[field] = value ? dayjs(value) : undefined;
    }

    getExerciseTypeIcon(type: ExerciseType): IconProp {
        return getIcon(type);
    }

    getCreateUrlSegment(type: ExerciseType): string {
        return getExerciseUrlSegment(type);
    }
}

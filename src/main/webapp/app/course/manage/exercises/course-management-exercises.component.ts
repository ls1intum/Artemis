import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { DialogService } from 'primeng/dynamicdialog';
import { QUIZ_EXPORT_BACK, QuizExerciseExportComponent } from 'app/quiz/manage/export/quiz-exercise-export.component';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { FormsModule } from '@angular/forms';
import { CdkDrag, CdkDragDrop, CdkDragHandle, CdkDropList, moveItemInArray } from '@angular/cdk/drag-drop';
import { SelectButtonModule } from 'primeng/selectbutton';
import { PanelModule } from 'primeng/panel';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCalendarDays, faCircleInfo, faCode, faFileExport, faFileImport, faGripVertical, faLayerGroup, faList, faPen, faPlus } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseGroup, effectiveDate } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { ALL_MOCK_COMPETENCIES } from 'app/core/course/manage/exercises/mock/intro-to-programming-java-competencies';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { MockDataService } from 'app/core/interceptor/mock-data.service';
import { ExerciseTableComponent, TableGroupChange } from 'app/core/course/manage/exercises-experimental/exercise-row/exercise-table.component';
import { AddModalMode, ExerciseAddModalComponent } from 'app/core/course/manage/exercises-experimental/create-modal/exercise-add-modal.component';
import { ExerciseGroupEditModalComponent } from 'app/core/course/manage/exercises-experimental/group-edit-modal/exercise-group-edit-modal.component';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { CourseTitleBarTitleDirective } from 'app/course/shared/directives/course-title-bar-title.directive';
import { CourseTitleBarToolbarDirective } from 'app/course/shared/directives/course-title-bar-toolbar.directive';

type View = 'type' | 'week' | 'group' | 'list';

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

@Component({
    selector: 'jhi-course-management-exercises',
    templateUrl: './course-management-exercises.component.html',
    styleUrl: './course-management-exercises.component.scss',
    imports: [
        FormsModule,
        SelectButtonModule,
        PanelModule,
        ButtonModule,
        TooltipModule,
        CdkDropList,
        CdkDrag,
        CdkDragHandle,
        FaIconComponent,
        ExerciseTableComponent,
        ExerciseAddModalComponent,
        ExerciseGroupEditModalComponent,
        SearchFilterComponent,
        ArtemisDatePipe,
        CourseTitleBarTitleDirective,
        CourseTitleBarToolbarDirective,
    ],
})
export class CourseManagementExercisesComponent implements OnInit {
    protected readonly allMockCompetencies = ALL_MOCK_COMPETENCIES;

    protected readonly faGripVertical = faGripVertical;
    protected readonly faPlus = faPlus;
    protected readonly faFileImport = faFileImport;
    protected readonly faFileExport = faFileExport;
    protected readonly faCircleInfo = faCircleInfo;
    protected readonly faPen = faPen;
    protected readonly ExerciseType = ExerciseType;

    readonly viewOptions: { label: string; value: View; icon: IconProp }[] = [
        { label: 'List', value: 'list', icon: faList },
        { label: 'Type', value: 'type', icon: faCode },
        { label: 'Week', value: 'week', icon: faCalendarDays },
        { label: 'Group', value: 'group', icon: faLayerGroup },
    ];

    readonly view = signal<View>('type');
    readonly search = signal('');

    readonly course = signal<Course | undefined>(undefined);
    readonly exercises = signal<Exercise[]>([]);
    readonly groups = signal<CourseExerciseGroup[]>([]);
    readonly buckets = signal<Bucket[]>([]);

    readonly selectedIds = signal<Set<number>>(new Set());
    readonly addModalVisible = signal(false);
    readonly addModalMode = signal<AddModalMode>('create');
    readonly groupEditModalVisible = signal(false);
    readonly groupEditModalId = signal<number | undefined>(undefined);

    readonly isGroup = computed(() => this.view() === 'group');
    readonly groupEditModalGroup = computed(() => this.groups().find((g) => g.id === this.groupEditModalId()));
    readonly exerciseCount = computed(() => this.exercises().length);
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
    private readonly mockDataService = inject(MockDataService);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly dialogService = inject(DialogService);
    private readonly translateService = inject(TranslateService);

    ngOnInit(): void {
        this.route.parent!.data.subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
            }
            if (this.mockDataService.enabled()) {
                this.exercises.set(this.mockService.getExercises());
                this.groups.set(this.mockService.getGroups());
                this.buildBuckets();
            } else if (course?.id) {
                this.courseManagementService.findWithExercises(course.id).subscribe({
                    next: (response) => {
                        this.exercises.set(response.body?.exercises ?? []);
                        this.buildBuckets();
                    },
                });
            } else {
                this.buildBuckets();
            }
        });
    }

    onViewChange(view: View): void {
        this.view.set(view);
        this.buildBuckets();
    }

    onSearchChange(term: string): void {
        this.search.set(term);
        this.buildBuckets();
    }

    owningGroup(exercise: Exercise): CourseExerciseGroup | undefined {
        return this.groups().find((group) => group.exercises?.some((member) => member.id === exercise.id));
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

    openQuizExportDialog(): void {
        // Quiz exercises are the only exportable type. The develop quiz export page is shown as a modal component
        // (no dedicated route). With mock data enabled it is populated from the mock quiz catalogue via MockCourseInterceptor.
        const id = this.courseId();
        if (id === undefined) {
            return;
        }
        const dialogRef = this.dialogService.open(QuizExerciseExportComponent, {
            header: this.translateService.instant('artemisApp.exercise.exportAction'),
            width: '60rem',
            modal: true,
            closable: true,
            closeOnEscape: true,
            draggable: false,
            data: { courseId: id },
        });
        dialogRef?.onClose.subscribe((result: string | undefined) => {
            // "Back" in the export dialog returns to the manage-exercises modal (its default Create view).
            if (result === QUIZ_EXPORT_BACK) {
                this.addModalMode.set('create');
                this.addModalVisible.set(true);
            }
        });
    }

    onAddModalGroupCreate(): void {
        this.view.set('group');
        const nextId = Math.max(0, ...this.groups().map((g) => g.id ?? 0)) + 1;
        const newGroup: CourseExerciseGroup = { id: nextId, title: `Group ${nextId}`, order: nextId, exercises: [] };
        this.groups.set([...this.groups(), newGroup]);
        this.buildBuckets();
        this.openGroupEditModal(nextId);
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
        return [...exercises].sort((a, b) => {
            const da = effectiveDate(a, this.owningGroup(a), 'dueDate');
            const db = effectiveDate(b, this.owningGroup(b), 'dueDate');
            return (da?.valueOf() ?? 0) - (db?.valueOf() ?? 0);
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

    openGroupEditModal(id: number): void {
        this.groupEditModalId.set(id);
        this.groupEditModalVisible.set(true);
    }

    onGroupEditModalSave(updated: CourseExerciseGroup): void {
        this.groups.set(this.groups().map((g) => (g.id === updated.id ? { ...g, ...updated } : g)));
        this.buildBuckets();
    }

    getGroupCompetenciesDisplay(group: CourseExerciseGroup): string {
        return (group.competencyLinks ?? []).map((l) => l.title).join(', ');
    }
}

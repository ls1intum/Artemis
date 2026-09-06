import { ChangeDetectionStrategy, Component, DestroyRef, TrackByFunction, computed, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { merge } from 'rxjs';
import { finalize, map } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { faGear, faPlus, faUmbrellaBeach } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    CellTemplateRef,
    ColumnDef,
    TumUiButtonDirective,
    TumUiMessageComponent,
    TumUiSearchFieldComponent,
    TumUiTableComponent,
    TumUiTableQueryEvent,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { Course } from 'app/course/shared/entities/course.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MeetingPatternPipe } from 'app/tutorialgroup/shared/pipe/meeting-pattern.pipe';
import { CourseTitleBarActionsDirective } from 'app/course/shared/directives/course-title-bar-actions.directive';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { convertTutorialGroupArrayDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';
import { tutorialGroupUtilization } from 'app/tutorialgroup/shared/util/tutorial-group-utilization';
import { TutorialGroupsImportButtonComponent } from './tutorial-groups-import-button/tutorial-groups-import-button.component';
import { TutorialGroupsExportButtonComponent } from './tutorial-groups-export-button.component/tutorial-groups-export-button.component';
import { TutorialGroupRowButtonsComponent } from './tutorial-group-row-buttons/tutorial-group-row-buttons.component';
import { TutorialGroupUtilizationIndicatorComponent } from 'app/tutorialgroup/manage/tutorial-group-utilization-indicator/tutorial-group-utilization-indicator.component';

/**
 * Sorts a row that has nothing to order by - no schedule, no measurable utilization - behind every row that has
 * data. Both cases say the same thing, so they sort to the same end rather than to opposite ones.
 */
const NO_DATA_SORT_KEY = Number.MAX_SAFE_INTEGER;

const SORTABLE_FIELDS = ['title', 'tutor', 'utilization', 'registrations', 'room', 'campus', 'schedule'] as const;
type SortableField = (typeof SORTABLE_FIELDS)[number];

/** Flattened projection of a tutorial group, so filtering, sorting and paging stay plain data work. */
interface TutorialGroupRow extends Record<SortableField, string | number> {
    readonly group: TutorialGroup;
    readonly title: string;
    readonly tutor: string;
    readonly utilization: number;
    readonly registrations: number;
    readonly room: string;
    /** Untranslated, so the column keeps its order when the reader switches language. */
    readonly campus: string;
    /** What the cell renders: the campus, or the translated mode standing in for a missing one. */
    readonly campusLabel: string;
    readonly schedule: number;
    readonly searchIndex: string;
}

/** Minutes since the start of the week, so a single number orders the schedule column. */
function scheduleSortKey(schedule?: TutorialGroupSchedule): number {
    if (!schedule?.dayOfWeek) {
        return NO_DATA_SORT_KEY;
    }
    const [hours, minutes] = (schedule.startTime ?? '').split(':');
    return schedule.dayOfWeek * 24 * 60 + (Number(hours) || 0) * 60 + (Number(minutes) || 0);
}

function toRow(group: TutorialGroup, modeLabel: string): TutorialGroupRow {
    const tutor = group.teachingAssistantName ?? '';
    const room = group.tutorialGroupSchedule?.location ?? '';
    // A group that names no campus falls back to its mode, which says more than a blank cell would. The cell shows
    // the translated mode, while sorting keys off the untranslated one so the order does not shift with the language.
    const namedCampus = group.campus?.trim();
    const campus = namedCampus || (group.isOnline ? 'online' : 'offline');
    const campusLabel = namedCampus || modeLabel;
    return {
        group,
        title: group.title ?? '',
        tutor,
        utilization: tutorialGroupUtilization(group) ?? NO_DATA_SORT_KEY,
        registrations: group.numberOfRegisteredUsers ?? 0,
        room,
        campus,
        campusLabel,
        schedule: scheduleSortKey(group.tutorialGroupSchedule),
        // Both spellings are indexed, so a search matches the label the reader sees as well as the stand-in.
        searchIndex: [group.title, tutor, room, campus, campusLabel].join(' ').toLowerCase(),
    };
}

function isSortableField(field: string): field is SortableField {
    return (SORTABLE_FIELDS as readonly string[]).includes(field);
}

function compareRows(a: TutorialGroupRow, b: TutorialGroupRow, field: SortableField): number {
    const left = a[field];
    const right = b[field];
    if (typeof left === 'number' && typeof right === 'number') {
        return left - right;
    }
    return String(left).localeCompare(String(right));
}

@Component({
    selector: 'jhi-tutorial-groups-management',
    templateUrl: './tutorial-groups-management.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        MeetingPatternPipe,
        CourseTitleBarActionsDirective,
        TumUiTableComponent,
        TumUiButtonDirective,
        TumUiMessageComponent,
        TumUiSearchFieldComponent,
        TumUiTooltipDirective,
        TutorialGroupsImportButtonComponent,
        TutorialGroupsExportButtonComponent,
        TutorialGroupRowButtonsComponent,
        TutorialGroupUtilizationIndicatorComponent,
    ],
})
export class TutorialGroupsManagementComponent {
    private readonly tutorialGroupApiService = inject(TutorialGroupApi);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly alertService = inject(AlertService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly translateService = inject(TranslateService);

    readonly course = signal<Course | undefined>(undefined);
    readonly courseId = computed(() => this.course()?.id);
    readonly isAtLeastInstructor = computed(() => this.course()?.isAtLeastInstructor ?? false);
    readonly isAtLeastEditor = computed(() => this.course()?.isAtLeastEditor ?? false);
    readonly configuration = computed(() => this.course()?.tutorialGroupsConfiguration);

    readonly isLoading = signal(false);
    readonly tutorialGroups = signal<TutorialGroup[]>([]);
    readonly searchTerm = signal('');

    private readonly table = viewChild(TumUiTableComponent<TutorialGroupRow>);
    private readonly query = signal<TumUiTableQueryEvent | undefined>(undefined);

    private readonly titleColumn = viewChild<CellTemplateRef<TutorialGroupRow>>('titleColumn');
    private readonly tutorColumn = viewChild<CellTemplateRef<TutorialGroupRow>>('tutorColumn');
    private readonly utilizationColumn = viewChild<CellTemplateRef<TutorialGroupRow>>('utilizationColumn');
    private readonly registrationsColumn = viewChild<CellTemplateRef<TutorialGroupRow>>('registrationsColumn');
    private readonly campusColumn = viewChild<CellTemplateRef<TutorialGroupRow>>('campusColumn');
    private readonly scheduleColumn = viewChild<CellTemplateRef<TutorialGroupRow>>('scheduleColumn');

    protected readonly columns = computed<ColumnDef<TutorialGroupRow>[]>(() => [
        { field: 'title', headerKey: 'artemisApp.entities.tutorialGroup.title', sort: true, width: '11rem', templateRef: this.titleColumn() },
        { field: 'tutor', headerKey: 'artemisApp.entities.tutorialGroup.teachingAssistant', sort: true, width: '11rem', templateRef: this.tutorColumn() },
        {
            field: 'utilization',
            headerKey: 'artemisApp.entities.tutorialGroup.utilization',
            headerTooltip: 'artemisApp.entities.tutorialGroup.utilizationHelp',
            sort: true,
            width: '10rem',
            hideBelow: 'md',
            templateRef: this.utilizationColumn(),
        },
        {
            field: 'registrations',
            headerKey: 'artemisApp.entities.tutorialGroup.registrationsWithCapacity',
            sort: true,
            width: '8rem',
            templateRef: this.registrationsColumn(),
        },
        { field: 'room', headerKey: 'artemisApp.entities.tutorialGroup.room', sort: true, width: '10rem', hideBelow: 'xl' },
        { field: 'campus', headerKey: 'artemisApp.entities.tutorialGroup.campus', sort: true, width: '8rem', hideBelow: 'lg', templateRef: this.campusColumn() },
        { field: 'schedule', headerKey: 'artemisApp.entities.tutorialGroup.schedule', sort: true, width: '13rem', templateRef: this.scheduleColumn() },
    ]);

    /** Re-projects the rows on a language change, so the mode standing in for a missing campus follows it. */
    private readonly translationChanges = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange), { initialValue: undefined });

    private readonly rows = computed(() => {
        this.translationChanges();
        const online = this.translateService.instant('artemisApp.generic.online');
        const offline = this.translateService.instant('artemisApp.generic.offline');
        return this.tutorialGroups().map((group) => toRow(group, group.isOnline ? online : offline));
    });

    private readonly filteredRows = computed(() => {
        const term = this.searchTerm().trim().toLowerCase();
        return term ? this.rows().filter((row) => row.searchIndex.includes(term)) : this.rows();
    });

    private readonly sortedRows = computed(() => {
        const sort = this.query()?.sort;
        const rows = [...this.filteredRows()];
        if (!sort || !isSortableField(sort.field)) {
            return rows;
        }
        const field = sort.field;
        const direction = sort.direction === 'asc' ? 1 : -1;
        return rows.sort((a, b) => direction * compareRows(a, b, field));
    });

    protected readonly totalRecords = computed(() => this.filteredRows().length);

    protected readonly pagedRows = computed(() => {
        const query = this.query();
        if (!query) {
            return [];
        }
        const start = query.pageIndex * query.pageSize;
        return this.sortedRows().slice(start, start + query.pageSize);
    });

    /** True once loading finished and the course has no tutorial groups at all, as opposed to none matching a search. */
    protected readonly hasNoTutorialGroups = computed(() => !this.isLoading() && this.tutorialGroups().length === 0);

    // TutorialGroup.id is optional, and two rows sharing an undefined key would be NG0955 plus lost row reuse.
    protected readonly trackByRow: TrackByFunction<TutorialGroupRow> = (index, row) => row.group.id ?? index;

    protected readonly faPlus = faPlus;
    protected readonly faGear = faGear;
    protected readonly faUmbrellaBeach = faUmbrellaBeach;

    constructor() {
        this.activatedRoute.data.pipe(takeUntilDestroyed()).subscribe(({ course }) => {
            if (course) {
                this.course.set(course);
                this.loadTutorialGroups();
            }
        });
    }

    loadTutorialGroups(): void {
        const courseId = this.courseId();
        if (courseId === undefined) {
            return;
        }
        this.isLoading.set(true);
        this.tutorialGroupApiService
            .getTutorialGroupsForCourse(courseId)
            .pipe(
                map((tutorialGroups: TutorialGroup[]) => convertTutorialGroupArrayDatesFromServer(tutorialGroups)),
                finalize(() => this.isLoading.set(false)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: (tutorialGroups: TutorialGroup[]) => this.tutorialGroups.set(tutorialGroups),
                error: (response: HttpErrorResponse) => onError(this.alertService, response),
            });
    }

    /** Filtering happens outside the table, so the reader must not be left on a page of the previous result set. */
    protected onSearch(term: string): void {
        this.searchTerm.set(term);
        this.table()?.resetPage();
    }

    protected onDataRequest(event: TumUiTableQueryEvent): void {
        this.query.set(event);
    }
}

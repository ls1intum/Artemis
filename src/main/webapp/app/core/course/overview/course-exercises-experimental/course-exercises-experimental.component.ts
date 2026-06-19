import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NgClass } from '@angular/common';
import { filter } from 'rxjs/operators';
import { faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways, TimeGroupCategory } from 'app/foundation/types/sidebar';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { CourseExerciseGroup, buildGroupsFromExercises } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { MockDataService } from 'app/core/interceptor/mock-data.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    future: true,
    current: false,
    dueSoon: false,
    past: true,
    noDate: true,
};

const DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
    future: false,
    current: false,
    dueSoon: false,
    past: false,
    noDate: false,
};

function emptyAccordionGroups(): AccordionGroups {
    return {
        future: { entityData: [] },
        current: { entityData: [] },
        dueSoon: { entityData: [] },
        past: { entityData: [] },
        noDate: { entityData: [] },
    };
}

/**
 * Experimental student exercise overview. Reuses the real exercise sidebar unchanged (search, filter,
 * date/week grouping all preserved). Exercises belonging to the same course-level group are nested under
 * a single group header card (via the sidebar's optional {@link SidebarCardElement.groupedItems}).
 * Reachable at /courses/:courseId/exercises.
 */
@Component({
    selector: 'jhi-course-exercises-experimental',
    templateUrl: './course-exercises-experimental.component.html',
    styleUrls: ['../../../../course/overview/course-overview/course-overview.scss', './course-exercises-experimental.component.scss'],
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseExercisesExperimentalComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly mockDataService = inject(MockDataService);
    private readonly courseOverviewService = inject(CourseOverviewService);
    private readonly courseManagementService = inject(CourseManagementService);
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly _exerciseSelected = signal(false);
    private readonly _exercises = signal<Exercise[]>([]);
    readonly exerciseSelected = computed(() => this._exerciseSelected());

    readonly courseId = signal(0);
    readonly sidebarData = computed(() => this.buildSidebarData());

    // The overview only wires its collapse toggle to known child components, so this view is never collapsed.
    protected readonly isCollapsed = false;
    protected readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    constructor() {
        this.courseId.set(Number(this.route.parent?.snapshot.params.courseId));
        if (this.mockDataService.enabled()) {
            this._exercises.set(this.mockService.getExercises());
        } else {
            this.courseManagementService
                .findOneForDashboard(this.courseId())
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({ next: (response) => this._exercises.set(response.body?.exercises ?? []) });
        }
        this.updateSelection();
        this.router.events
            .pipe(
                filter((event) => event instanceof NavigationEnd),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(() => this.updateSelection());
    }

    onSubRouteDeactivate(): void {
        this.updateSelection();
    }

    private updateSelection(): void {
        this._exerciseSelected.set(!!this.route.firstChild);
    }

    private buildSidebarData(): SidebarData {
        const exercises = this._exercises();

        // Exercises in the same course-level group are nested under a single group header card.
        const grouped = this.buildGroupedData(exercises);
        return {
            groupByCategory: true,
            sidebarType: 'exercise',
            storageId: 'exercise',
            groupedData: grouped.groupedData,
            ungroupedData: grouped.ungroupedData,
        };
    }

    /**
     * Builds the date-category accordion where each course-level exercise group becomes a single group
     * header card (placed by its due date) whose variant cards are nested via groupedItems; ungrouped
     * exercises are mapped as usual.
     */
    private buildGroupedData(exercises: Exercise[]): Pick<SidebarData, 'groupedData' | 'ungroupedData'> {
        const groupByExerciseId = new Map<number, CourseExerciseGroup>();
        // Mock mode uses the mock groups; with real data the groups are reconstructed from each
        // exercise's embedded variant-group reference (already delivered, release-filtered, by the dashboard).
        const groups = this.mockDataService.enabled() ? this.mockService.getGroups() : buildGroupsFromExercises(exercises);
        for (const group of groups) {
            for (const member of group.exercises ?? []) {
                if (member.id !== undefined) {
                    groupByExerciseId.set(member.id, group);
                }
            }
        }

        const groupedData = emptyAccordionGroups();
        const ungroupedData: SidebarCardElement[] = [];
        const emittedGroups = new Set<number>();

        for (const exercise of exercises) {
            const group = exercise.id !== undefined ? groupByExerciseId.get(exercise.id) : undefined;
            if (group) {
                if (group.id !== undefined && !emittedGroups.has(group.id)) {
                    emittedGroups.add(group.id);
                    const members = exercises.filter((candidate) => candidate.id !== undefined && groupByExerciseId.get(candidate.id) === group);
                    const card = this.groupCard(group, members);
                    groupedData[this.categorizeGroup(group, members)].entityData.push(card);
                    ungroupedData.push(card);
                }
            } else {
                const card = this.courseOverviewService.mapExerciseToSidebarCardElement(exercise);
                groupedData[this.courseOverviewService.getCorrespondingExerciseGroupByDate(exercise)].entityData.push(card);
                ungroupedData.push(card);
            }
        }

        return { groupedData, ungroupedData };
    }

    /**
     * An exercise with several variants, rendered as one connected sidebar stack: a card header (the
     * exercise title and icon) sits above its nested variant cards. Clicking it opens the detail page
     * that lists the variants.
     */
    private groupCard(group: CourseExerciseGroup, members: Exercise[]): SidebarCardElement {
        const dueDate = group.dueDate ?? members[0]?.dueDate;
        return {
            title: group.title ?? '',
            id: group.id ?? '',
            targetComponentSubRoute: 'group',
            icon: faLayerGroup,
            // Show the group's due date in the header card, mirroring how exercise tiles display theirs (see mapExerciseToSidebarCardElement).
            subtitleLeft: dueDate?.format('MMM DD, YYYY') ?? this.translateService.instant('artemisApp.courseOverview.sidebar.noDueDate'),
            startDate: dueDate,
            size: 'M',
            groupHeaderStyle: 'card',
            groupConnected: true,
            groupClickable: 'group',
            routerLink: `/courses/${this.courseId()}/exercises/group/${group.id}`,
            groupedItems: members.map((member) => this.courseOverviewService.mapExerciseToSidebarCardElement(member)),
        };
    }

    /** Categorises a group into a date bucket using the group dates (falling back to the first member's dates). */
    private categorizeGroup(group: CourseExerciseGroup, members: Exercise[]): TimeGroupCategory {
        const first = members[0];
        const representative = {
            type: first?.type,
            releaseDate: group.releaseDate ?? first?.releaseDate,
            startDate: group.startDate ?? first?.startDate,
            dueDate: group.dueDate ?? first?.dueDate,
        } as Exercise;
        return this.courseOverviewService.getCorrespondingExerciseGroupByDate(representative);
    }
}

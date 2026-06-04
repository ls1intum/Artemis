import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NgClass } from '@angular/common';
import { filter } from 'rxjs/operators';
import { faLayerGroup } from '@fortawesome/free-solid-svg-icons';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways, TimeGroupCategory } from 'app/shared/types/sidebar';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { CourseExerciseGroup } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';

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
 * date/week grouping all preserved). The only addition is that exercises belonging to the same
 * course-level group are nested under a single group header card (via the sidebar's optional
 * {@link SidebarCardElement.groupedItems}). Ungrouped exercises behave exactly as before. The detail
 * (problem statement etc.) opens in the right-hand pane. Dev-only; reachable at
 * /courses/:courseId/exercises/experimental.
 */
@Component({
    selector: 'jhi-course-exercises-experimental',
    templateUrl: './course-exercises-experimental.component.html',
    styleUrls: ['../course-overview/course-overview.scss'],
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective],
})
export class CourseExercisesExperimentalComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly courseOverviewService = inject(CourseOverviewService);
    private readonly destroyRef = inject(DestroyRef);

    private readonly _exerciseSelected = signal(false);
    readonly exerciseSelected = computed(() => this._exerciseSelected());

    readonly courseId = signal(0);
    readonly sidebarData: SidebarData;

    // The overview only wires its collapse toggle to known child components, so this view is never collapsed.
    protected readonly isCollapsed = false;
    protected readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    constructor() {
        this.courseId.set(Number(this.route.parent?.snapshot.params.courseId));
        this.sidebarData = this.buildSidebarData();

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

    /**
     * Builds the sidebar data using the same date-category accordion as the real view. Each course-level
     * exercise group becomes a single group header card (placed by its due date) whose variant cards are
     * nested via {@link SidebarCardElement.groupedItems}; ungrouped exercises are mapped as usual.
     */
    private buildSidebarData(): SidebarData {
        const groups = this.mockService.getGroups();
        const exercises = this.mockService.getExercises();

        const groupByExerciseId = new Map<number, CourseExerciseGroup>();
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

        return {
            groupByCategory: true,
            sidebarType: 'exercise',
            storageId: 'exercise',
            groupedData,
            ungroupedData,
        };
    }

    /** A group rendered as a sidebar card (same design) with the group icon, group due date and its variant cards nested. */
    private groupCard(group: CourseExerciseGroup, members: Exercise[]): SidebarCardElement {
        const dueDate = group.dueDate ?? members[0]?.dueDate;
        return {
            title: group.title ?? '',
            // Opens the first variant on click; the nested variant cards let the student pick a specific one.
            id: members[0]?.id ?? group.id ?? '',
            icon: faLayerGroup,
            subtitleLeft: dueDate?.format('MMM DD, YYYY'),
            startDate: dueDate,
            size: 'M',
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

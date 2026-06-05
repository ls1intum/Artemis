import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { NgClass } from '@angular/common';
import { filter } from 'rxjs/operators';
import { faLayerGroup, faTriangleExclamation } from '@fortawesome/free-solid-svg-icons';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways, TimeGroupCategory } from 'app/shared/types/sidebar';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { ExerciseManagementMockService } from 'app/core/course/manage/exercises-experimental/exercise-management-mock.service';
import { CourseExerciseGroup, handInLimitFor } from 'app/core/course/manage/exercises/mock/course-exercise-group.model';
import { StudentExerciseDevSettingsService } from './dev-settings/student-exercise-dev-settings.service';
import { StudentExerciseDevSettingsModalComponent } from './dev-settings/student-exercise-dev-settings-modal.component';
import { GroupHandInSelectionService } from './group-hand-in-selection.service';

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
 * a single group header card (via the sidebar's optional {@link SidebarCardElement.groupedItems}). A
 * dev-settings gear (see {@link StudentExerciseDevSettingsService}) switches between view versions so we
 * can iterate on different designs. Dev-only; reachable at /courses/:courseId/exercises/experimental.
 */
@Component({
    selector: 'jhi-course-exercises-experimental',
    templateUrl: './course-exercises-experimental.component.html',
    styleUrls: ['../course-overview/course-overview.scss', './course-exercises-experimental.component.scss'],
    imports: [NgClass, SidebarComponent, RouterOutlet, TranslateDirective, StudentExerciseDevSettingsModalComponent],
})
export class CourseExercisesExperimentalComponent {
    private readonly route = inject(ActivatedRoute);
    private readonly router = inject(Router);
    private readonly mockService = inject(ExerciseManagementMockService);
    private readonly courseOverviewService = inject(CourseOverviewService);
    private readonly selectionService = inject(GroupHandInSelectionService);
    private readonly destroyRef = inject(DestroyRef);
    protected readonly devSettings = inject(StudentExerciseDevSettingsService);

    private readonly _exerciseSelected = signal(false);
    readonly exerciseSelected = computed(() => this._exerciseSelected());

    readonly courseId = signal(0);
    readonly sidebarData = computed(() => this.buildSidebarData());

    // The overview only wires its collapse toggle to known child components, so this view is never collapsed.
    protected readonly isCollapsed = false;
    protected readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    constructor() {
        this.courseId.set(Number(this.route.parent?.snapshot.params.courseId));

        // Tell the course overview to show the dev-settings gear (in place of notifications) while this view is active.
        this.devSettings.active.set(true);
        this.destroyRef.onDestroy(() => this.devSettings.active.set(false));

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
        const exercises = this.mockService.getExercises();

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
        for (const group of this.mockService.getGroups()) {
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

    /** A group rendered as a sidebar card (same design) with the group icon, hand-in limit and its variant cards nested. */
    private groupCard(group: CourseExerciseGroup, members: Exercise[]): SidebarCardElement {
        const dueDate = group.dueDate ?? members[0]?.dueDate;
        const sidebarStyle = this.devSettings.groupSidebarStyle();
        // 'connected' renders one connected tile stack with the group as a card header; 'clickable' uses a
        // plain heading. Both navigate to the group page. 'select' instead uses per-variant checkboxes.
        const connected = sidebarStyle === 'connected';
        const selectable = sidebarStyle === 'select';
        const clickable = !selectable;

        // Below the title we show how many exercises have been handed in out of the hand-in limit (instead
        // of a date). Until at least one is handed in it reads "0 / Y" in warning yellow with a triangle and
        // a tooltip prompting the student to open the group and pick exercises.
        const handedInCount = group.id !== undefined ? this.selectionService.getSubmittedSelection(group.id).length : 0;
        const handInText = `Handed in: ${handedInCount} / ${handInLimitFor(group)}`;
        const nothingHandedIn = handedInCount === 0;
        return {
            title: group.title ?? '',
            // For the connected card header, route to the group page (id = group id, sub-route 'group').
            // Otherwise the id only tracks/selects the label header; the first variant id is fine there.
            id: (connected ? group.id : members[0]?.id) ?? group.id ?? '',
            targetComponentSubRoute: connected ? 'group' : undefined,
            icon: faLayerGroup,
            // Card header shows the hand-in status below the title; the label header uses the hint line instead.
            subtitleLeft: connected ? handInText : undefined,
            groupPickHint: connected ? undefined : handInText,
            subtitleLeftIcon: nothingHandedIn ? faTriangleExclamation : undefined,
            subtitleLeftClass: nothingHandedIn ? 'text-warning fw-semibold' : undefined,
            subtitleLeftTooltip: nothingHandedIn ? 'No exercise handed in yet — click the group to select exercises and hand them in.' : undefined,
            startDate: dueDate,
            size: 'M',
            groupHeaderStyle: connected ? 'card' : 'label',
            groupConnected: connected,
            groupClickable: clickable ? 'group' : undefined,
            // Clicking the group opens its mock detail page (the variant is chosen by the click-action setting).
            routerLink: clickable ? `/courses/${this.courseId()}/exercises/experimental/group/${group.id}` : undefined,
            groupSelectable: selectable,
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

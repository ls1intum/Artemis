import { ChangeDetectorRef, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Course } from 'app/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { LtiService } from 'app/foundation/service/lti.service';
import { NgStyle } from '@angular/common';
import { SidebarComponent } from 'app/course/sidebar/sidebar.component';
import { CourseSidebarToggleButtonComponent } from 'app/course/shared/course-sidebar-toggle-button/course-sidebar-toggle-button.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Subscription, forkJoin } from 'rxjs';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { CourseExerciseDetailsComponent } from 'app/course/overview/exercise-details/course-exercise-details.component';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { InitializationState, Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';

function isStudentParticipationChange(participation: Participation | undefined): participation is StudentParticipation {
    return !!participation && participation.type !== ParticipationType.TEMPLATE && participation.type !== ParticipationType.SOLUTION;
}

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    dueSoon: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

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

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview/course-overview.scss'],
    imports: [SidebarComponent, CourseSidebarToggleButtonComponent, NgStyle, RouterOutlet, TranslateDirective],
})
export class CourseExercisesComponent {
    private courseStorageService = inject(CourseStorageService);
    private route = inject(ActivatedRoute);
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private router = inject(Router);
    private courseOverviewService = inject(CourseOverviewService);
    private ltiService = inject(LtiService);
    private exerciseService = inject(ExerciseService);
    private sessionStorageService = inject(SessionStorageService);
    private participationWebsocketService = inject(ParticipationWebsocketService);
    private destroyRef = inject(DestroyRef);
    private changeDetectorRef = inject(ChangeDetectorRef);
    private courseOverviewExercisesService = inject(CourseOverviewExercisesService);

    private readonly _course = signal<Course | undefined>(undefined);
    private readonly _courseId = signal<number>(0);
    private readonly _sortedExercises = signal<Exercise[] | undefined>(undefined);
    private readonly _exerciseSelected = signal(true);
    private readonly _accordionExerciseGroups = signal<AccordionGroups>(DEFAULT_UNIT_GROUPS);
    private readonly _sidebarData = signal<SidebarData | undefined>(undefined);
    private readonly _sidebarExercises = signal<SidebarCardElement[]>([]);
    private readonly _isCollapsed = signal(false);
    private readonly _isShownViaLti = signal(false);
    private readonly _isMultiLaunch = signal(false);
    private readonly _multiLaunchExerciseIDs = signal<number[]>([]);
    private readonly _activeExerciseDetails = signal<CourseExerciseDetailsComponent | undefined>(undefined);
    readonly pageTitle = signal<string>('');
    private courseUpdateSubscription?: Subscription;
    private exercisesLoadSubscription?: Subscription;

    readonly course = this._course.asReadonly();
    readonly courseId = this._courseId.asReadonly();
    readonly sortedExercises = this._sortedExercises.asReadonly();
    readonly exerciseSelected = this._exerciseSelected.asReadonly();
    readonly accordionExerciseGroups = this._accordionExerciseGroups.asReadonly();
    readonly sidebarData = this._sidebarData.asReadonly();
    readonly sidebarExercises = this._sidebarExercises.asReadonly();

    readonly isCollapsed = this._isCollapsed.asReadonly();
    readonly isShownViaLti = this._isShownViaLti.asReadonly();
    readonly isMultiLaunch = this._isMultiLaunch.asReadonly();
    readonly multiLaunchExerciseIDs = this._multiLaunchExerciseIDs.asReadonly();

    protected readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    constructor() {
        this._isCollapsed.set(this.courseOverviewService.getSidebarCollapseStateFromStorage('exercise'));

        this.route.parent!.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this._courseId.set(Number(params.courseId));
            this.initializeAfterCourseIdSet();
        });

        this.route.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            if (params['exerciseIDs']) {
                this._multiLaunchExerciseIDs.set(params['exerciseIDs'].split(',').map((id: string) => Number(id)));
            }
        });

        this.ltiService.isShownViaLti$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((isShownViaLti) => {
            this._isShownViaLti.set(isShownViaLti);
        });

        this.ltiService.isMultiLaunch$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((isMultiLaunch) => {
            this._isMultiLaunch.set(isMultiLaunch);
        });

        this.participationWebsocketService
            .subscribeForParticipationChanges()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((changedParticipation) => this.handleParticipationChange(changedParticipation));

        effect(() => {
            this._activeExerciseDetails()?.setSidebarToggle(this._isCollapsed(), () => this.toggleSidebar());
        });

        this.destroyRef.onDestroy(() => {
            this.exercisesLoadSubscription?.unsubscribe();
        });
    }

    private initializeAfterCourseIdSet(): void {
        this._course.set(this.courseStorageService.getCourse(this._courseId()));
        this.onCourseLoad();
        this.prepareSidebarData();

        // The course container only loads the course itself; the exercises (with participations and scores) belong to
        // this tab, so they are fetched here. The result is published on the stored course, which the subscription
        // below picks up — the statistics tab shares the same load.
        this.exercisesLoadSubscription?.unsubscribe();
        this.exercisesLoadSubscription = this.courseOverviewExercisesService.loadIfNeeded(this._courseId()).subscribe();

        // Cancel previous course update subscription to avoid duplicates when courseId changes
        this.courseUpdateSubscription?.unsubscribe();
        this.courseUpdateSubscription = this.courseStorageService
            .subscribeToCourseUpdates(this._courseId())
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((course: Course) => {
                this._course.set(course);
                this.prepareSidebarData();
                this.onCourseLoad();
                // This update can arrive from an async, non-template-event source (e.g. a started exercise's
                // participation propagated into the cached course while the student is navigating to the code editor).
                // Under zoneless change detection that signal write does not reliably schedule a render in time, so the
                // sidebar card could stay at "Not yet started" until an unrelated event ticked CD. Explicitly mark for
                // check so the live re-map paints immediately.
                this.changeDetectorRef.markForCheck();
            });

        // If no exercise is selected navigate to the lastSelected or upcoming exercise
        this.navigateToExercise();
    }

    navigateToExercise() {
        const upcomingExercise = this.courseOverviewService.getUpcomingExercise(this._course()?.exercises);
        const lastSelectedExercise = this.getLastSelectedExercise();
        let exerciseId = this.route.firstChild?.snapshot?.params.exerciseId;

        if (!exerciseId) {
            // Get the exerciseId from the URL
            const url = this.router.url;
            const urlParts = url.split('/');
            const indexOfExercise = urlParts.indexOf('exercises');
            if (indexOfExercise !== -1 && urlParts.length > indexOfExercise + 1) {
                const segment = urlParts[indexOfExercise + 1];
                // Already on a group detail page — treat as selected, no redirect needed.
                if (segment === 'group') {
                    this._exerciseSelected.set(true);
                    return;
                }
                if (urlParts.length === indexOfExercise + 2) {
                    exerciseId = segment;
                }
            }
        }

        if (!exerciseId && lastSelectedExercise) {
            void this.router.navigate([lastSelectedExercise], { relativeTo: this.route, replaceUrl: true });
        } else if (!exerciseId && upcomingExercise) {
            // A grouped upcoming exercise must open its group detail page, not a single raw variant, matching the
            // sidebar's group route. Ungrouped exercises keep navigating directly to their exercise id.
            const groupId = upcomingExercise.exerciseVariantGroup?.id;
            const target = groupId !== undefined ? ['group', groupId] : [upcomingExercise.id];
            void this.router.navigate(target, { relativeTo: this.route, replaceUrl: true });
        } else {
            this._exerciseSelected.set(!!exerciseId);
        }
    }

    toggleSidebar() {
        this._isCollapsed.update((value) => !value);
        this.courseOverviewService.setSidebarCollapseState('exercise', this._isCollapsed());
    }

    setPageTitle(pageTitle: string): void {
        this.pageTitle.set(pageTitle);
    }

    getLastSelectedExercise(): string | undefined {
        return this.sessionStorageService.retrieve<string>('sidebar.lastSelectedItem.exercise.byCourse.' + this._courseId());
    }

    prepareSidebarData() {
        const exercises: Exercise[] = [];
        const multiLaunchExerciseIDs = this._multiLaunchExerciseIDs();

        if (multiLaunchExerciseIDs?.length > 0) {
            const exerciseObservables = multiLaunchExerciseIDs.map((exerciseId) => this.exerciseService.find(exerciseId));

            forkJoin(exerciseObservables).subscribe((exerciseResponses) => {
                exerciseResponses.forEach((response) => {
                    exercises.push(response.body!);
                });

                this.processExercises(exercises);
            });
        } else {
            const course = this._course();
            if (!course?.exercises) {
                return;
            }
            this.processExercises(course.exercises);
        }
    }

    processExercises(exercises: Exercise[]): void {
        const sortedExercises = this.courseOverviewService.sortExercises(this.preserveSidebarParticipationSnapshots(exercises));
        this._sortedExercises.set(sortedExercises);
        const { groupedData, ungroupedData } = this.courseOverviewService.buildGroupedExerciseData(sortedExercises, this._courseId());
        this._sidebarExercises.set(ungroupedData);
        this._accordionExerciseGroups.set(groupedData);
        this.updateSidebarData();
    }

    private preserveSidebarParticipationSnapshots(exercises: Exercise[]): Exercise[] {
        const sidebarParticipationsByExerciseId = this.getSidebarParticipationsByExerciseId();
        if (!sidebarParticipationsByExerciseId.size) {
            return exercises;
        }

        let didUpdate = false;
        const updatedExercises = exercises.map((exercise) => {
            if (exercise.id === undefined) {
                return exercise;
            }

            const sidebarParticipation = sidebarParticipationsByExerciseId.get(exercise.id);
            if (!sidebarParticipation) {
                return exercise;
            }

            const participations = exercise.studentParticipations ?? [];
            const currentParticipation = participations.find((participation) => this.isSameParticipationSlot(participation, sidebarParticipation));
            if (!this.shouldPreserveSidebarParticipation(sidebarParticipation, currentParticipation)) {
                return exercise;
            }

            didUpdate = true;
            const updatedParticipations = currentParticipation
                ? participations.map((participation) => (this.isSameParticipationSlot(participation, sidebarParticipation) ? sidebarParticipation : participation))
                : participations.concat(sidebarParticipation);
            return { ...exercise, studentParticipations: updatedParticipations };
        });
        return didUpdate ? updatedExercises : exercises;
    }

    private getSidebarParticipationsByExerciseId(): Map<number, StudentParticipation> {
        const sidebarParticipationsByExerciseId = new Map<number, StudentParticipation>();
        this._sidebarExercises().forEach((sidebarExercise) => {
            const exerciseId = sidebarExercise.exercise?.id ?? (typeof sidebarExercise.id === 'number' ? sidebarExercise.id : undefined);
            if (exerciseId !== undefined && sidebarExercise.studentParticipation) {
                sidebarParticipationsByExerciseId.set(exerciseId, sidebarExercise.studentParticipation);
            }
        });
        return sidebarParticipationsByExerciseId;
    }

    private shouldPreserveSidebarParticipation(sidebarParticipation: StudentParticipation, currentParticipation: StudentParticipation | undefined): boolean {
        if (!currentParticipation) {
            return !!sidebarParticipation.initializationState || !!sidebarParticipation.submissions?.length;
        }

        const sidebarResultCount = getAllResultsOfAllSubmissions(sidebarParticipation.submissions).length;
        const currentResultCount = getAllResultsOfAllSubmissions(currentParticipation.submissions).length;
        return (
            sidebarResultCount > currentResultCount ||
            ((sidebarParticipation.submissions?.length ?? 0) > (currentParticipation.submissions?.length ?? 0) && sidebarResultCount >= currentResultCount) ||
            (sidebarParticipation.initializationState === InitializationState.FINISHED && currentParticipation.initializationState !== InitializationState.FINISHED)
        );
    }

    private handleParticipationChange(changedParticipation: Participation | undefined): void {
        if (!isStudentParticipationChange(changedParticipation) || changedParticipation.exercise?.id === undefined) {
            return;
        }

        this.updateCourseParticipationSnapshot(changedParticipation);
        const sourceExercises = this._sortedExercises() ?? this._course()?.exercises;
        const updatedExercises = this.updateExercisesWithParticipation(sourceExercises, changedParticipation);
        if (!updatedExercises || updatedExercises === sourceExercises) {
            return;
        }
        this.processExercises(updatedExercises);
        this.changeDetectorRef.markForCheck();
    }

    private updateCourseParticipationSnapshot(changedParticipation: StudentParticipation): void {
        const course = this._course();
        const updatedCourseExercises = this.updateExercisesWithParticipation(course?.exercises, changedParticipation);
        if (!course || !updatedCourseExercises || updatedCourseExercises === course.exercises) {
            return;
        }
        this._course.set({ ...course, exercises: updatedCourseExercises });
    }

    private updateExercisesWithParticipation(exercises: Exercise[] | undefined, changedParticipation: StudentParticipation): Exercise[] | undefined {
        const exerciseId = changedParticipation.exercise?.id;
        if (exerciseId === undefined || !exercises?.length) {
            return exercises;
        }

        let didUpdate = false;
        const updatedExercises = exercises.map((exercise) => {
            if (exercise.id !== exerciseId) {
                return exercise;
            }

            didUpdate = true;
            const participations = exercise.studentParticipations ?? [];
            const hasParticipation = participations.some((participation) => this.isSameParticipationSlot(participation, changedParticipation));
            const updatedParticipations = hasParticipation
                ? participations.map((participation) => (this.isSameParticipationSlot(participation, changedParticipation) ? changedParticipation : participation))
                : participations.concat(changedParticipation);
            return { ...exercise, studentParticipations: updatedParticipations };
        });
        return didUpdate ? updatedExercises : exercises;
    }

    private isSameParticipationSlot(participation: StudentParticipation, otherParticipation: StudentParticipation): boolean {
        if (participation.id !== undefined && otherParticipation.id !== undefined) {
            return participation.id === otherParticipation.id;
        }
        return !!participation.testRun === !!otherParticipation.testRun;
    }

    updateSidebarData() {
        this._sidebarData.set({
            groupByCategory: true,
            sidebarType: 'exercise',
            storageId: 'exercise',
            groupedData: this._accordionExerciseGroups(),
            ungroupedData: this._sidebarExercises(),
        });
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this._course()?.exercises, true);
    }

    onSubRouteDeactivate() {
        this._activeExerciseDetails.set(undefined);
        if (this.route.firstChild) {
            return;
        }
        this.navigateToExercise();
    }

    onSubRouteActivate(componentRef: unknown) {
        if (componentRef instanceof CourseExerciseDetailsComponent) {
            this._activeExerciseDetails.set(componentRef);
        }
    }
}

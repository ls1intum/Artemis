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
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseOverviewService } from 'app/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/foundation/types/sidebar';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Subscription, forkJoin } from 'rxjs';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { CourseExerciseDetailsComponent } from 'app/course/overview/exercise-details/course-exercise-details.component';

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
    imports: [SidebarComponent, NgStyle, RouterOutlet, TranslateDirective],
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
    private destroyRef = inject(DestroyRef);
    private changeDetectorRef = inject(ChangeDetectorRef);

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

        effect(() => {
            this._activeExerciseDetails()?.setSidebarToggle(this._isCollapsed(), () => this.toggleSidebar());
        });
    }

    private initializeAfterCourseIdSet(): void {
        this._course.set(this.courseStorageService.getCourse(this._courseId()));
        this.onCourseLoad();
        this.prepareSidebarData();

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
            this.router.navigate([lastSelectedExercise], { relativeTo: this.route, replaceUrl: true });
        } else if (!exerciseId && upcomingExercise) {
            this.router.navigate([upcomingExercise.id], { relativeTo: this.route, replaceUrl: true });
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
        const sortedExercises = this.courseOverviewService.sortExercises(exercises);
        this._sortedExercises.set(sortedExercises);
        const { groupedData, ungroupedData } = this.courseOverviewService.buildGroupedExerciseData(sortedExercises, this._courseId());
        this._sidebarExercises.set(ungroupedData);
        this._accordionExerciseGroups.set(groupedData);
        this.updateSidebarData();
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

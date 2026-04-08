import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { LtiService } from 'app/shared/service/lti.service';
import { NgClass, NgStyle } from '@angular/common';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/shared/types/sidebar';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Subscription, forkJoin } from 'rxjs';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

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
    imports: [NgClass, SidebarComponent, NgStyle, RouterOutlet, TranslateDirective],
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
    private courseUpdateSubscription?: Subscription;

    readonly course = computed(() => this._course());
    readonly courseId = computed(() => this._courseId());
    readonly sortedExercises = computed(() => this._sortedExercises());
    readonly exerciseSelected = computed(() => this._exerciseSelected());
    readonly accordionExerciseGroups = computed(() => this._accordionExerciseGroups());
    readonly sidebarData = computed(() => this._sidebarData());
    readonly sidebarExercises = computed(() => this._sidebarExercises());
    // isCollapsed is exposed as a getter for compatibility with CourseOverviewComponent
    get isCollapsed(): boolean {
        return this._isCollapsed();
    }
    readonly isShownViaLti = computed(() => this._isShownViaLti());
    readonly isMultiLaunch = computed(() => this._isMultiLaunch());
    readonly multiLaunchExerciseIDs = computed(() => this._multiLaunchExerciseIDs());

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
            if (indexOfExercise !== -1 && urlParts.length === indexOfExercise + 2) {
                exerciseId = urlParts[indexOfExercise + 1];
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
        this._sidebarExercises.set(this.courseOverviewService.mapExercisesToSidebarCardElements(sortedExercises));
        this._accordionExerciseGroups.set(this.courseOverviewService.groupExercisesByDueDate(sortedExercises));
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
        if (this.route.firstChild) {
            return;
        }
        this.navigateToExercise();
    }
}

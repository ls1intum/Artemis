import { Component, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { AccountService } from 'app/core/auth/account.service';
import { sum, flatten, maxBy } from 'lodash';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { isOrion } from 'app/shared/orion/orion';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { LocalStorageService } from 'ngx-webstorage';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

enum ExerciseFilter {
    OVERDUE = 'OVERDUE',
    NEEDS_WORK = 'NEEDS_WORK',
}

enum ExerciseSortingOrder {
    DUE_DATE_ASC = 1,
    DUE_DATE_DESC = -1,
}

enum SortFilterStorageKey {
    FILTER = 'artemis.course.exercises.filter',
    ORDER = 'artemis.course.exercises.order',
}

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesComponent implements OnInit, OnChanges, OnDestroy {
    private courseId: number;
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    public course?: Course;
    public weeklyIndexKeys: string[];
    public weeklyExercisesGrouped: object;
    public upcomingExercises: Exercise[] = [];
    public exerciseCountMap: Map<string, number>;

    readonly ASC = ExerciseSortingOrder.DUE_DATE_ASC;
    readonly DESC = ExerciseSortingOrder.DUE_DATE_DESC;
    readonly filterType = ExerciseFilter;
    sortingOrder: ExerciseSortingOrder;
    activeFilters: Set<ExerciseFilter>;
    numberOfExercises: number;
    exerciseForGuidedTour?: Exercise;
    nextRelevantExercise?: Exercise;

    constructor(
        private courseService: CourseManagementService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseManagementService,
        private translateService: TranslateService,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private route: ActivatedRoute,
        private guidedTourService: GuidedTourService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private localStorage: LocalStorageService,
    ) {}

    ngOnInit() {
        this.exerciseCountMap = new Map<string, number>();
        this.numberOfExercises = 0;
        const filters = this.localStorage.retrieve(SortFilterStorageKey.FILTER);
        const filtersInStorage = filters
            ? filters
                  .split(',')
                  .map((filter: string) => ExerciseFilter[filter])
                  .filter(Boolean)
            : [];
        this.activeFilters = new Set(filtersInStorage);
        const orderInStorage = this.localStorage.retrieve(SortFilterStorageKey.ORDER);
        const parsedOrderInStorage = Object.keys(ExerciseSortingOrder).find((exerciseOrder) => exerciseOrder === orderInStorage);
        this.sortingOrder = parsedOrderInStorage ? (+parsedOrderInStorage as ExerciseSortingOrder) : ExerciseSortingOrder.DUE_DATE_ASC;
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseService.getCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.courseCalculationService.updateCourse(course);
            this.course = this.courseCalculationService.getCourse(this.courseId);
            this.onCourseLoad();
        });

        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.applyFiltersAndOrder();
        });

        this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, courseExerciseOverviewTour, true);
    }

    ngOnChanges() {
        this.nextRelevantExercise = this.exerciseService.getNextExerciseForHours(this.course?.exercises);
    }

    ngOnDestroy(): void {
        this.translateSubscription.unsubscribe();
        this.courseUpdatesSubscription.unsubscribe();
        this.paramSubscription.unsubscribe();
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this.course!.exercises, true);
        this.applyFiltersAndOrder();
    }

    private calcNumberOfExercises() {
        this.numberOfExercises = sum(Array.from(this.exerciseCountMap.values()));
    }

    /**
     * Reorders all displayed exercises
     */
    flipOrder() {
        this.sortingOrder = this.sortingOrder === this.ASC ? this.DESC : this.ASC;
        this.localStorage.store(SortFilterStorageKey.ORDER, this.sortingOrder.toString());
        this.applyFiltersAndOrder();
    }

    /**
     * Filters all displayed exercises by applying the selected activeFilters
     * @param filters The filters which should be applied
     */
    toggleFilters(filters: ExerciseFilter[]) {
        filters.forEach((filter) => (this.activeFilters.has(filter) ? this.activeFilters.delete(filter) : this.activeFilters.add(filter)));
        this.localStorage.store(SortFilterStorageKey.FILTER, Array.from(this.activeFilters).join(','));
        this.applyFiltersAndOrder();
    }

    /**
     * Checks if the given exercise still needs work, i.e. wasn't even started yet or is not graded with 100%
     * @param exercise The exercise which should get checked
     */
    private needsWork(exercise: Exercise): boolean {
        const latestResult = maxBy(flatten(exercise.studentParticipations?.map((participation) => participation.results)), 'completionDate');
        return !latestResult || latestResult.score !== 100;
    }

    /**
     * Applies all selected activeFilters and orders and groups the user's exercises
     */
    private applyFiltersAndOrder() {
        const needsWorkFilterActive = this.activeFilters.has(ExerciseFilter.NEEDS_WORK);
        const overdueFilterActive = this.activeFilters.has(ExerciseFilter.OVERDUE);
        const filtered = this.course?.exercises?.filter(
            (exercise) =>
                (!needsWorkFilterActive || this.needsWork(exercise)) &&
                (!exercise.dueDate || !overdueFilterActive || exercise.dueDate.isAfter(moment(new Date()))) &&
                (!isOrion || exercise.type === ExerciseType.PROGRAMMING),
        );
        this.groupExercises(filtered);
    }

    private groupExercises(exercises?: Exercise[]) {
        // set all values to 0
        this.exerciseCountMap = new Map<string, number>();
        this.weeklyExercisesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedExercises = {};
        const indexKeys: string[] = [];
        const sortedExercises = this.sortExercises(exercises) || [];
        const notAssociatedExercises: Exercise[] = [];
        const upcomingExercises: Exercise[] = [];
        sortedExercises.forEach((exercise) => {
            const dateValue = exercise.dueDate;
            this.increaseExerciseCounter(exercise);
            if (!dateValue) {
                notAssociatedExercises.push(exercise);
                return;
            }
            const dateIndex = dateValue ? moment(dateValue).startOf('week').format('YYYY-MM-DD') : 'NoDate';
            if (!groupedExercises[dateIndex]) {
                indexKeys.push(dateIndex);
                if (dateValue) {
                    groupedExercises[dateIndex] = {
                        start: moment(dateValue).startOf('week'),
                        end: moment(dateValue).endOf('week'),
                        isCollapsed: dateValue.isBefore(moment(), 'week'),
                        isCurrentWeek: dateValue.isSame(moment(), 'week'),
                        exercises: [],
                    };
                } else {
                    groupedExercises[dateIndex] = {
                        isCollapsed: false,
                        isCurrentWeek: false,
                        exercises: [],
                    };
                }
            }
            groupedExercises[dateIndex].exercises.push(exercise);
            if (exercise.dueDate && moment().isSameOrBefore(exercise.dueDate, 'day')) {
                upcomingExercises.push(exercise);
            }
        });
        this.updateUpcomingExercises(upcomingExercises);
        if (notAssociatedExercises.length > 0) {
            this.weeklyExercisesGrouped = {
                ...groupedExercises,
                noDate: {
                    label: this.translateService.instant('artemisApp.courseOverview.exerciseList.noExerciseDate'),
                    isCollapsed: false,
                    isCurrentWeek: false,
                    exercises: notAssociatedExercises,
                },
            };
            this.weeklyIndexKeys = [...indexKeys, 'noDate'];
        } else {
            this.weeklyExercisesGrouped = groupedExercises;
            this.weeklyIndexKeys = indexKeys;
        }
        this.calcNumberOfExercises();
    }

    private sortExercises(exercises?: Exercise[]) {
        return exercises?.sort((a, b) => {
            const aValue = a.dueDate ? a.dueDate.valueOf() : moment().valueOf();
            const bValue = b.dueDate ? b.dueDate.valueOf() : moment().valueOf();
            return this.sortingOrder.valueOf() * (aValue - bValue);
        });
    }

    private increaseExerciseCounter(exercise: Exercise) {
        if (!this.exerciseCountMap.has(exercise.type!)) {
            this.exerciseCountMap.set(exercise.type!, 1);
        } else {
            let exerciseCount = this.exerciseCountMap.get(exercise.type!)!;
            this.exerciseCountMap.set(exercise.type!, ++exerciseCount);
        }
    }

    private updateUpcomingExercises(upcomingExercises: Exercise[]) {
        const sortedExercises = this.sortExercises(upcomingExercises) || [];
        if (upcomingExercises.length <= 5) {
            this.upcomingExercises = sortedExercises;
        } else {
            // sort after due date and take the first 5 elements
            this.upcomingExercises = sortedExercises.slice(0, 5);
        }
    }
}

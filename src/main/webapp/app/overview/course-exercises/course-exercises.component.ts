import { Component, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs';
import { AccountService } from 'app/core/auth/account.service';
import { flatten, maxBy, sum } from 'lodash-es';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { isOrion } from 'app/shared/orion/orion';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { LocalStorageService } from 'ngx-webstorage';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

export enum ExerciseFilter {
    OVERDUE = 'OVERDUE',
    NEEDS_WORK = 'NEEDS_WORK',
    UNRELEASED = 'UNRELEASED',
}

export enum ExerciseSortingOrder {
    ASC = 1,
    DESC = -1,
}

enum SortFilterStorageKey {
    FILTER = 'artemis.course.exercises.filter',
    ORDER = 'artemis.course.exercises.order',
    ATTRIBUTE = 'artemis.course.exercises.attribute',
}

export enum SortingAttribute {
    DUE_DATE = 0,
    RELEASE_DATE = 1,
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

    readonly ASC = ExerciseSortingOrder.ASC;
    readonly DESC = ExerciseSortingOrder.DESC;
    readonly DUE_DATE = SortingAttribute.DUE_DATE;
    readonly RELEASE_DATE = SortingAttribute.RELEASE_DATE;
    readonly filterType = ExerciseFilter;
    sortingOrder: ExerciseSortingOrder;
    activeFilters: Set<ExerciseFilter>;
    numberOfExercises: number;
    exerciseForGuidedTour?: Exercise;
    nextRelevantExercise?: Exercise;
    sortingAttribute: SortingAttribute;

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
        this.loadOrderAndAttributeForSorting();
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
        this.nextRelevantExercise = this.exerciseService.getNextExerciseForHours(this.course?.exercises);
    }

    setSortingAttribute(attribute: SortingAttribute) {
        this.sortingAttribute = attribute;
        this.localStorage.store(SortFilterStorageKey.ATTRIBUTE, this.sortingAttribute.toString());
        this.applyFiltersAndOrder();
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
     * Checks whether an exercise is visible to students or not
     * @param exercise The exercise which should be checked
     */
    isVisibleToStudents(exercise: Exercise): boolean | undefined {
        return !this.activeFilters.has(ExerciseFilter.UNRELEASED) || (exercise as QuizExercise)?.visibleToStudents;
    }

    /**
     * Checks if the given exercise still needs work, i.e. wasn't even started yet or is not graded with 100%
     * @param exercise The exercise which should get checked
     */
    private needsWork(exercise: Exercise): boolean {
        const latestResult = maxBy(flatten(exercise.studentParticipations?.map((participation) => participation.results)), 'completionDate');
        return !latestResult || !latestResult.score || latestResult.score < 100;
    }

    /**
     * Applies all selected activeFilters and orders and groups the user's exercises
     */
    private applyFiltersAndOrder() {
        const needsWorkFilterActive = this.activeFilters.has(ExerciseFilter.NEEDS_WORK);
        const overdueFilterActive = this.activeFilters.has(ExerciseFilter.OVERDUE);
        const unreleasedFilterActive = this.activeFilters.has(ExerciseFilter.UNRELEASED);
        const filtered = this.course?.exercises?.filter(
            (exercise) =>
                (!needsWorkFilterActive || this.needsWork(exercise)) &&
                (!exercise.dueDate || !overdueFilterActive || exercise.dueDate.isAfter(dayjs(new Date()))) &&
                (!exercise.releaseDate || !unreleasedFilterActive || (exercise as QuizExercise)?.visibleToStudents) &&
                (!isOrion || exercise.type === ExerciseType.PROGRAMMING),
        );
        this.groupExercises(filtered);
    }

    private getSortingAttributeFromExercise(): (exercise: Exercise) => dayjs.Dayjs | undefined {
        return this.sortingAttribute === this.DUE_DATE ? (exercise) => exercise.dueDate : (exercise) => exercise.releaseDate;
    }

    private loadOrderAndAttributeForSorting() {
        const orderInStorage = this.localStorage.retrieve(SortFilterStorageKey.ORDER);
        const parsedOrderInStorage = Object.keys(ExerciseSortingOrder).find((exerciseOrder) => exerciseOrder === orderInStorage);
        this.sortingOrder = parsedOrderInStorage ? (+parsedOrderInStorage as ExerciseSortingOrder) : ExerciseSortingOrder.ASC;

        const attributeInStorage = this.localStorage.retrieve(SortFilterStorageKey.ATTRIBUTE);
        const parsedAttributeInStorage = Object.keys(SortingAttribute).find((exerciseOrder) => exerciseOrder === attributeInStorage);
        this.sortingAttribute = parsedAttributeInStorage ? (+parsedAttributeInStorage as SortingAttribute) : SortingAttribute.DUE_DATE;
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
        const sortedExercises = this.sortExercises((exercise) => exercise.dueDate, upcomingExercises) || [];
        if (upcomingExercises.length <= 5) {
            this.upcomingExercises = sortedExercises;
        } else {
            // sort after selected date and take the first 5 elements
            this.upcomingExercises = sortedExercises.slice(0, 5);
        }
    }

    private groupExercises(exercises?: Exercise[]) {
        // set all values to 0
        this.exerciseCountMap = new Map<string, number>();
        this.weeklyExercisesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedExercises = {};
        const indexKeys: string[] = [];
        const sortedExercises = this.sortExercises(this.getSortingAttributeFromExercise(), exercises) || [];
        const notAssociatedExercises: Exercise[] = [];
        const upcomingExercises: Exercise[] = [];
        sortedExercises.forEach((exercise) => {
            const dateValue = this.getSortingAttributeFromExercise()(exercise);
            this.increaseExerciseCounter(exercise);
            if (!dateValue) {
                notAssociatedExercises.push(exercise);
                return;
            }
            const dateIndex = dateValue ? dayjs(dateValue).startOf('week').format('YYYY-MM-DD') : 'NoDate';
            if (!groupedExercises[dateIndex]) {
                indexKeys.push(dateIndex);
                groupedExercises[dateIndex] = {
                    start: dayjs(dateValue).startOf('week'),
                    end: dayjs(dateValue).endOf('week'),
                    isCollapsed: dateValue.isBefore(dayjs(), 'week'),
                    isCurrentWeek: dateValue.isSame(dayjs(), 'week'),
                    exercises: [],
                };
            }
            groupedExercises[dateIndex].exercises.push(exercise);
            if (exercise.dueDate && !dayjs().isAfter(dateValue, 'day')) {
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

    private sortExercises(byAttribute: (exercise: Exercise) => dayjs.Dayjs | undefined, exercises?: Exercise[]) {
        return exercises?.sort((a, b) => {
            const sortingAttributeA = byAttribute(a);
            const sortingAttributeB = byAttribute(b);
            const aValue = sortingAttributeA ? sortingAttributeA.second(0).millisecond(0).valueOf() : dayjs().valueOf();
            const bValue = sortingAttributeB ? sortingAttributeB.second(0).millisecond(0).valueOf() : dayjs().valueOf();
            const titleSortValue = a.title && b.title ? a.title.localeCompare(b.title) : 0;
            return this.sortingOrder.valueOf() * (aValue - bValue === 0 ? titleSortValue : aValue - bValue);
        });
    }
}

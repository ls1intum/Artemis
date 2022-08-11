import { AfterViewInit, Component, OnChanges, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { flatten, maxBy, sum } from 'lodash-es';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { isOrion } from 'app/shared/orion/orion';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { LocalStorageService } from 'ngx-webstorage';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faAngleDown, faAngleUp, faFilter, faPlayCircle, faSortNumericDown, faSortNumericUp } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/overview/tab-bar/tab-bar';

export enum ExerciseFilter {
    OVERDUE = 'OVERDUE',
    NEEDS_WORK = 'NEEDS_WORK',
    UNRELEASED = 'UNRELEASED',
    OPTIONAL = 'OPTIONAL',
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

interface ExerciseWithDueDate {
    exercise: Exercise;
    dueDate?: dayjs.Dayjs;
}

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesComponent implements OnInit, OnChanges, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    private courseId: number;
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    private currentUser?: User;
    public course?: Course;
    public weeklyIndexKeys: string[];
    public weeklyExercisesGrouped: object;
    public upcomingExercises: ExerciseWithDueDate[] = [];
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
    nextRelevantExercise?: ExerciseWithDueDate;
    sortingAttribute: SortingAttribute;

    // Icons
    faPlayCircle = faPlayCircle;
    faFilter = faFilter;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
    faSortNumericUp = faSortNumericUp;
    faSortNumericDown = faSortNumericDown;

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
        useIndentation: true,
    };

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

        this.accountService.identity().then((user) => {
            this.currentUser = user;
            this.updateNextRelevantExercise();
        });
    }

    ngAfterViewInit(): void {
        // Send our controls template to parent so it will be rendered in the top bar
        if (this.controls) {
            this.controlConfiguration.subject!.next(this.controls);
        }
    }

    setSortingAttribute(attribute: SortingAttribute) {
        this.sortingAttribute = attribute;
        this.localStorage.store(SortFilterStorageKey.ATTRIBUTE, this.sortingAttribute.toString());
        this.applyFiltersAndOrder();
    }

    ngOnChanges() {
        this.updateNextRelevantExercise();
    }

    ngOnDestroy(): void {
        this.translateSubscription?.unsubscribe();
        this.courseUpdatesSubscription?.unsubscribe();
        this.paramSubscription?.unsubscribe();
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this.course!.exercises, true);
        this.applyFiltersAndOrder();
    }

    private calcNumberOfExercises() {
        this.numberOfExercises = sum(Array.from(this.exerciseCountMap.values()));
    }

    private updateNextRelevantExercise() {
        const nextExercise = this.exerciseService.getNextExerciseForHours(this.course?.exercises?.filter(this.fulfillsCurrentFilter.bind(this)), 12, this.currentUser);
        if (nextExercise) {
            const dueDate = CourseExercisesComponent.exerciseDueDate(nextExercise);
            this.nextRelevantExercise = {
                exercise: nextExercise,
                dueDate,
            };
        } else {
            this.nextRelevantExercise = undefined;
        }
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
        this.updateNextRelevantExercise();
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
        const filtered = this.course?.exercises?.filter(this.fulfillsCurrentFilter.bind(this));
        this.groupExercises(filtered);
    }

    /**
     * Check if the given exercise fulfills the currently selected filters
     * @param exercise the exercise to check
     * @return true if the given exercise fulfills the currently selected filters; false otherwise
     * @private
     */
    private fulfillsCurrentFilter(exercise: Exercise): boolean {
        const needsWorkFilterActive = this.activeFilters.has(ExerciseFilter.NEEDS_WORK);
        const overdueFilterActive = this.activeFilters.has(ExerciseFilter.OVERDUE);
        const unreleasedFilterActive = this.activeFilters.has(ExerciseFilter.UNRELEASED);
        const optionalFilterActive = this.activeFilters.has(ExerciseFilter.OPTIONAL);
        const participation = CourseExercisesComponent.studentParticipation(exercise);
        return !!(
            (!needsWorkFilterActive || this.needsWork(exercise)) &&
            (!exercise.dueDate || !overdueFilterActive || !hasExerciseDueDatePassed(exercise, participation)) &&
            (!exercise.releaseDate || !unreleasedFilterActive || (exercise as QuizExercise)?.visibleToStudents) &&
            (!optionalFilterActive || exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED) &&
            (!isOrion || exercise.type === ExerciseType.PROGRAMMING)
        );
    }

    private getSortingAttributeFromExercise(): (exercise: Exercise) => dayjs.Dayjs | undefined {
        if (this.sortingAttribute === this.DUE_DATE) {
            return CourseExercisesComponent.exerciseDueDate;
        } else {
            return (exercise) => exercise.releaseDate;
        }
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
        const sortedExercises = (this.sortExercises(CourseExercisesComponent.exerciseDueDate, upcomingExercises, ExerciseSortingOrder.ASC) || []).map((exercise) => {
            return { exercise, dueDate: CourseExercisesComponent.exerciseDueDate(exercise) };
        });
        if (upcomingExercises.length <= 5) {
            this.upcomingExercises = sortedExercises;
        } else {
            // sort after selected date and take the first 5 elements
            this.upcomingExercises = sortedExercises.slice(0, 5);
        }
    }

    private static exerciseDueDate(exercise: Exercise): dayjs.Dayjs | undefined {
        return getExerciseDueDate(exercise, CourseExercisesComponent.studentParticipation(exercise));
    }

    private static studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations && exercise.studentParticipations.length > 0 ? exercise.studentParticipations[0] : undefined;
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

    private sortExercises(byAttribute: (exercise: Exercise) => dayjs.Dayjs | undefined, exercises?: Exercise[], sortOrder?: ExerciseSortingOrder) {
        const sortingOrder = sortOrder ?? this.sortingOrder;
        return exercises?.sort((a, b) => {
            const sortingAttributeA = byAttribute(a);
            const sortingAttributeB = byAttribute(b);
            const aValue = sortingAttributeA ? sortingAttributeA.second(0).millisecond(0).valueOf() : dayjs().valueOf();
            const bValue = sortingAttributeB ? sortingAttributeB.second(0).millisecond(0).valueOf() : dayjs().valueOf();
            const titleSortValue = a.title && b.title ? a.title.localeCompare(b.title) : 0;
            return sortingOrder.valueOf() * (aValue - bValue === 0 ? titleSortValue : aValue - bValue);
        });
    }
}

import { AfterViewInit, Component, OnDestroy, OnInit, TemplateRef, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { ActivatedRoute } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { flatten, maxBy, sum } from 'lodash-es';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { courseExerciseOverviewTour } from 'app/guided-tour/tours/course-exercise-overview-tour';
import { isOrion } from 'app/shared/orion/orion';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faAngleDown, faAngleUp, faFilter, faMagnifyingGlass, faPlayCircle, faSortNumericDown, faSortNumericUp, faXmark } from '@fortawesome/free-solid-svg-icons';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { BarControlConfiguration, BarControlConfigurationProvider } from 'app/shared/tab-bar/tab-bar';
import { ExerciseFilter as ExerciseFilterModel } from 'app/entities/exercise-filter.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { CourseUnenrollmentModalComponent } from 'app/overview/course-unenrollment-modal.component';

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

export interface ExerciseWithDueDate {
    exercise: Exercise;
    dueDate?: dayjs.Dayjs;
}

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesComponent implements OnInit, OnDestroy, AfterViewInit, BarControlConfigurationProvider {
    private courseId: number;
    private paramSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    private translateSubscription: Subscription;
    public course?: Course;
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
    sortingAttribute: SortingAttribute;
    searchExercisesInput: string;
    exerciseFilter: ExerciseFilterModel;

    filteredAndSortedExercises: Exercise[] | undefined;
    showExercisesGroupedByTimeframe: boolean = true;
    lastAppliedSearchString: string | undefined;

    // Icons
    faPlayCircle = faPlayCircle;
    faFilter = faFilter;
    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;
    faSortNumericUp = faSortNumericUp;
    faSortNumericDown = faSortNumericDown;
    faMagnifyingGlass = faMagnifyingGlass;
    faXmark = faXmark;

    // The extracted controls template from our template to be rendered in the top bar of "CourseOverviewComponent"
    @ViewChild('controls', { static: false }) private controls: TemplateRef<any>;
    // Provides the control configuration to be read and used by "CourseOverviewComponent"
    public readonly controlConfiguration: BarControlConfiguration = {
        subject: new Subject<TemplateRef<any>>(),
    };

    constructor(
        private courseStorageService: CourseStorageService,
        private translateService: TranslateService,
        private route: ActivatedRoute,
        private guidedTourService: GuidedTourService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private localStorage: LocalStorageService,
        private modalService: NgbModal,
    ) {}

    ngOnInit() {
        this.exerciseCountMap = new Map<string, number>();
        this.exerciseFilter = new ExerciseFilterModel();
        this.numberOfExercises = 0;
        this.searchExercisesInput = '';
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

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.onCourseLoad();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.onCourseLoad();
        });

        this.translateSubscription = this.translateService.onLangChange.subscribe(() => {
            this.applyFiltersAndOrder();
        });

        this.exerciseForGuidedTour = this.guidedTourService.enableTourForCourseExerciseComponent(this.course, courseExerciseOverviewTour, true);
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

    isVisibleToStudents(exercise: Exercise): boolean | undefined {
        return !this.activeFilters.has(ExerciseFilter.UNRELEASED) || (exercise as QuizExercise)?.visibleToStudents;
    }

    setShowExercisesGroupedByDueDateCategory(updatedShowExercisesGroupedByDueDateCategory: boolean) {
        this.showExercisesGroupedByTimeframe = updatedShowExercisesGroupedByDueDateCategory;
    }

    /**
     * Method is called when enter key is pressed on search input or search button is clicked
     */
    onSearch() {
        this.searchExercisesInput = this.searchExercisesInput.trim();
        this.exerciseFilter = new ExerciseFilterModel(this.searchExercisesInput);
        this.applyFiltersAndOrder();
        this.lastAppliedSearchString = this.exerciseFilter.exerciseNameSearch;
    }

    get canUnenroll(): boolean {
        return !!this.course?.unenrollmentEnabled && dayjs().isBefore(this.course?.unenrollmentEndDate);
    }

    /**
     * Method is called when unenroll button is clicked
     */
    onUnenroll() {
        const modalRef = this.modalService.open(CourseUnenrollmentModalComponent, { size: 'xl' });
        modalRef.componentInstance.course = this.course;
    }

    /**
     * Checks if the given exercise still needs work, i.e. wasn't even started yet or is not graded with 100%
     * @param exercise The exercise which should get checked
     */
    private static needsWork(exercise: Exercise): boolean {
        const latestResult = maxBy(flatten(exercise.studentParticipations?.map((participation) => participation.results)), 'completionDate');
        return !latestResult || !latestResult.score || latestResult.score < 100;
    }

    /**
     * Applies all selected activeFilters and orders and groups the user's exercises
     */
    private applyFiltersAndOrder() {
        let filtered = this.course?.exercises?.filter((exercise) => CourseExercisesComponent.fulfillsCurrentFilter(exercise, this.activeFilters));
        filtered = filtered?.filter((exercise) => this.exerciseFilter.matchesExercise(exercise));

        this.filteredAndSortedExercises = this.sortExercises(filtered);
        this.updateCourseInformationWidget(filtered);
    }

    /**
     * Check if the given exercise fulfills the currently selected filters
     * @param exercise to check
     * @param activeFilters that are applied
     * @return true if the given exercise fulfills the currently selected filters; false otherwise
     */
    public static fulfillsCurrentFilter(exercise: Exercise, activeFilters: Set<ExerciseFilter>): boolean {
        const needsWorkFilterActive = activeFilters.has(ExerciseFilter.NEEDS_WORK);
        const overdueFilterActive = activeFilters.has(ExerciseFilter.OVERDUE);
        const unreleasedFilterActive = activeFilters.has(ExerciseFilter.UNRELEASED);
        const optionalFilterActive = activeFilters.has(ExerciseFilter.OPTIONAL);
        const participation = CourseExercisesComponent.studentParticipation(exercise);
        return !!(
            (!needsWorkFilterActive || CourseExercisesComponent.needsWork(exercise)) &&
            (!exercise.dueDate || !overdueFilterActive || !hasExerciseDueDatePassed(exercise, participation)) &&
            (!exercise.releaseDate || !unreleasedFilterActive || (exercise as QuizExercise)?.visibleToStudents) &&
            (!optionalFilterActive || exercise.includedInOverallScore !== IncludedInOverallScore.NOT_INCLUDED) &&
            (!isOrion || exercise.type === ExerciseType.PROGRAMMING)
        );
    }

    public static getSortingAttributeFromExercise(exercise: Exercise, sortingAttribute?: SortingAttribute): dayjs.Dayjs | undefined {
        if (sortingAttribute === SortingAttribute.DUE_DATE) {
            return CourseExercisesComponent.exerciseDueDate(exercise);
        } else {
            return exercise.releaseDate;
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
        const sortedExercises = (this.sortExercises(upcomingExercises, ExerciseSortingOrder.ASC, SortingAttribute.DUE_DATE) || []).map((exercise) => {
            return { exercise, dueDate: CourseExercisesComponent.exerciseDueDate(exercise) };
        });

        if (upcomingExercises.length <= 5) {
            this.upcomingExercises = sortedExercises;
        } else {
            // sort after selected date and take the first 5 elements
            this.upcomingExercises = sortedExercises.slice(0, 5);
        }
    }

    public static exerciseDueDate(exercise: Exercise): dayjs.Dayjs | undefined {
        return getExerciseDueDate(exercise, CourseExercisesComponent.studentParticipation(exercise));
    }

    private static studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations && exercise.studentParticipations.length > 0 ? exercise.studentParticipations[0] : undefined;
    }

    private updateCourseInformationWidget(exercises?: Exercise[]) {
        this.exerciseCountMap = new Map<string, number>();
        const upcomingExercises: Exercise[] = [];
        exercises?.forEach((exercise) => {
            const dateValue = CourseExercisesComponent.getSortingAttributeFromExercise(exercise, SortingAttribute.DUE_DATE);
            if (exercise.dueDate && !dayjs().isAfter(dateValue, 'day')) {
                upcomingExercises.push(exercise);
            }

            this.increaseExerciseCounter(exercise);
        });

        this.updateUpcomingExercises(upcomingExercises);
        this.calcNumberOfExercises();
    }

    private sortExercises(exercises?: Exercise[], sortOrder?: ExerciseSortingOrder, sortAttribute?: SortingAttribute) {
        const sortingAttribute = sortAttribute ?? this.sortingAttribute;
        const sortingOrder = sortOrder ?? this.sortingOrder;

        return exercises?.sort((exerciseA, exerciseB) => {
            const sortingAttributeA = CourseExercisesComponent.getSortingAttributeFromExercise(exerciseA, sortingAttribute);
            const sortingAttributeB = CourseExercisesComponent.getSortingAttributeFromExercise(exerciseB, sortingAttribute);
            const aValue = sortingAttributeA ? sortingAttributeA.second(0).millisecond(0).valueOf() : dayjs().valueOf();
            const bValue = sortingAttributeB ? sortingAttributeB.second(0).millisecond(0).valueOf() : dayjs().valueOf();
            const titleSortValue = exerciseA.title && exerciseB.title ? exerciseA.title.localeCompare(exerciseB.title) : 0;
            return sortingOrder.valueOf() * (aValue - bValue === 0 ? titleSortValue : aValue - bValue);
        });
    }
}

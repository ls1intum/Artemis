import { Component, OnDestroy, OnInit } from '@angular/core';
import { Course, CourseScoreCalculationService, CourseService } from 'app/entities/course';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Exercise, ExerciseService } from 'app/entities/exercise';
import { AccountService, User } from 'app/core';

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesComponent implements OnInit, OnDestroy {
    public readonly DUE_DATE_ASC = 1;
    public readonly DUE_DATE_DESC = -1;
    public readonly OVERDUE = 0;
    public readonly NEEDS_WORK = 1;
    private courseId: number;
    private paramSubscription: Subscription;
    private translateSubscription: Subscription;
    private filters: Set<number>;
    private user: User | null;
    private order: number;
    public course: Course | null;
    public weeklyIndexKeys: string[];
    public weeklyExercisesGrouped: object;
    public upcomingExercises: Exercise[];
    public exerciseCountMap: Map<string, number>;

    constructor(
        private courseService: CourseService,
        private courseCalculationService: CourseScoreCalculationService,
        private courseServer: CourseService,
        private translateService: TranslateService,
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.exerciseCountMap = new Map<string, number>();
        this.filters = new Set<number>();
        this.accountService.identity(true).then(user => (this.user = user));
        this.order = this.DUE_DATE_DESC;
        this.paramSubscription = this.route.parent!.params.subscribe(params => {
            this.courseId = parseInt(params['courseId'], 10);
        });

        this.course = this.courseCalculationService.getCourse(this.courseId);
        if (this.course == null) {
            this.courseService.findAll().subscribe((res: HttpResponse<Course[]>) => {
                this.courseCalculationService.setCourses(res.body!);
                this.course = this.courseCalculationService.getCourse(this.courseId);
            });
        }
        this.applyFiltersAndOrder();

        this.translateSubscription = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
            this.applyFiltersAndOrder();
        });
    }

    ngOnDestroy(): void {
        this.translateSubscription.unsubscribe();
        this.paramSubscription.unsubscribe();
    }

    /**
     * Return the total number of exercises for the logged in user
     */
    getNrOfExercises(): number {
        return [...this.exerciseCountMap.values()].reduce((a, b) => a + b, 0);
    }

    /**
     * Reorders all displayed exercises
     * @param selectedOrder The order in which the exercises should be displayed
     */
    orderUpdate(selectedOrder: number) {
        this.order = selectedOrder;
        this.applyFiltersAndOrder();
    }

    /**
     * Filters all displayed exercises by applying the selected filters
     * @param filters The filters which should be applied
     * @param active Should the filters be active or inactive
     */
    filterUpdate(filters: number[], active: boolean) {
        if (active) {
            filters.forEach(filter => this.filters.add(filter));
        } else {
            filters.forEach(filter => this.filters.delete(filter));
        }

        this.applyFiltersAndOrder();
    }

    /**
     * Checks if the given exercise still needs work, i.e. is not graded with 100%, or wasn't even started, yet.
     * @param exercise The exercise which should get checked
     */
    private needsWork(exercise: Exercise): boolean {
        console.log(this.user);
        console.log(exercise);
        return (
            exercise.participations.some(
                participation => participation.student.id === this.user!.id && participation.results && participation.results.some(result => result.score !== 100),
            ) || exercise.participations.every(participation => participation.student.id !== this.user!.id || participation.results.length === 0)
        );
    }

    /**
     * Applies all selected filters and orders and groups the user's exercises
     */
    private applyFiltersAndOrder() {
        const filtered = [...this.course!.exercises].filter(
            exercise =>
                (!this.filters.has(this.NEEDS_WORK) || this.needsWork(exercise)) &&
                (!exercise.dueDate || !this.filters.has(this.OVERDUE) || exercise.dueDate.isAfter(moment(new Date()))),
        );
        this.groupExercises(filtered);
    }

    private groupExercises(exercises: Exercise[]) {
        // set all values to 0
        this.exerciseCountMap = new Map<string, number>();
        this.weeklyExercisesGrouped = {};
        this.weeklyIndexKeys = [];
        const groupedExercises = {};
        const indexKeys: string[] = [];
        const sortedExercises = this.sortExercises(exercises, this.order);
        const notAssociatedExercises: Exercise[] = [];
        const upcomingExercises: Exercise[] = [];
        sortedExercises.forEach(exercise => {
            const dateValue = exercise.dueDate;
            this.increaseExerciseCounter(exercise);
            if (!dateValue) {
                notAssociatedExercises.push(exercise);
                return;
            }
            const dateIndex = dateValue
                ? moment(dateValue)
                      .startOf('week')
                      .format('YYYY-MM-DD')
                : 'NoDate';
            if (!groupedExercises[dateIndex]) {
                indexKeys.push(dateIndex);
                if (dateValue) {
                    groupedExercises[dateIndex] = {
                        label: `<b>${moment(dateValue)
                            .startOf('week')
                            .format('DD/MM/YYYY')}</b> - <b>${moment(dateValue)
                            .endOf('week')
                            .format('DD/MM/YYYY')}</b>`,
                        isCollapsed: dateValue.isBefore(moment(), 'week'),
                        isCurrentWeek: dateValue.isSame(moment(), 'week'),
                        exercises: [],
                    };
                } else {
                    groupedExercises[dateIndex] = {
                        label: `No date associated`,
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
    }

    private sortExercises(exercises: Exercise[], selectedOrder: number) {
        return exercises.sort((a, b) => {
            const aValue = a.dueDate ? a.dueDate.valueOf() : moment().valueOf();
            const bValue = b.dueDate ? b.dueDate.valueOf() : moment().valueOf();

            return selectedOrder * (aValue - bValue);
        });
    }

    private increaseExerciseCounter(exercise: Exercise) {
        if (!this.exerciseCountMap.has(exercise.type)) {
            this.exerciseCountMap.set(exercise.type, 1);
        } else {
            let exerciseCount = this.exerciseCountMap.get(exercise.type)!;
            this.exerciseCountMap.set(exercise.type, ++exerciseCount);
        }
    }

    private updateUpcomingExercises(upcomingExercises: Exercise[]) {
        if (upcomingExercises.length < 5) {
            this.upcomingExercises = this.sortExercises(upcomingExercises, this.order);
        } else {
            const numberOfExercises = upcomingExercises.length;
            upcomingExercises = upcomingExercises.slice(numberOfExercises - 5, numberOfExercises);
            this.upcomingExercises = this.sortExercises(upcomingExercises, this.order);
        }
    }

    get nextRelevantExercise(): Exercise {
        return this.exerciseService.getNextExerciseForHours(this.course!.exercises);
    }
}

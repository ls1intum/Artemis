import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { CourseExercisesComponent, ExerciseFilter, ExerciseWithDueDate, SortingAttribute } from 'app/overview/course-exercises/course-exercises.component';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';

type GroupedExercisesByWeek = Record<
    string,
    {
        exercises: Exercise[];
        isCurrentWeek: boolean;
        isCollapsed: boolean;
        start?: dayjs.Dayjs;
        end?: dayjs.Dayjs;
    }
>;

@Component({
    selector: 'jhi-course-exercises-grouped-by-week',
    templateUrl: './course-exercises-grouped-by-week.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesGroupedByWeekComponent implements OnInit, OnChanges {
    protected readonly Object = Object;

    @Input() filteredAndSortedExercises?: Exercise[];
    @Input() course: Course;
    @Input() exerciseForGuidedTour?: Exercise;
    @Input() weeklyIndexKeys: string[];
    @Input() activeFilters: Set<ExerciseFilter>;

    exerciseGroups: GroupedExercisesByWeek;
    nextRelevantExercise?: ExerciseWithDueDate;

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    private currentUser?: User;

    constructor(
        private exerciseService: ExerciseService,
        private accountService: AccountService,
        private translateService: TranslateService,
    ) {}

    ngOnInit() {
        this.accountService.identity().then((user) => {
            this.currentUser = user;
            this.updateNextRelevantExercise();
        });

        this.groupExercises(this.filteredAndSortedExercises);
    }

    ngOnChanges() {
        this.updateNextRelevantExercise();
        this.groupExercises(this.filteredAndSortedExercises);
    }

    /**
     * Checks whether an exercise is visible to students or not
     * @param exercise The exercise which should be checked
     */
    isVisibleToStudents(exercise: Exercise): boolean | undefined {
        return !this.activeFilters.has(ExerciseFilter.UNRELEASED) || (exercise as QuizExercise)?.visibleToStudents;
    }

    private groupExercises(exercises?: Exercise[]) {
        const groupedExercises: GroupedExercisesByWeek = {};
        const noDueDateKey = this.translateService.instant('artemisApp.courseOverview.exerciseList.noExerciseDate');

        exercises?.forEach((exercise) => {
            // TODO exchange sorting attribute
            const dateValue = CourseExercisesComponent.getSortingAttributeFromExercise(exercise, SortingAttribute.DUE_DATE);
            if (!dateValue) {
                if (!groupedExercises[noDueDateKey]) {
                    groupedExercises[noDueDateKey] = {
                        exercises: [],
                        isCurrentWeek: false,
                        isCollapsed: false,
                    };
                }

                groupedExercises[noDueDateKey].exercises.push(exercise);

                return;
            }
            const dateIndex = dateValue ? dayjs(dateValue).startOf('week').format('YYYY-MM-DD') : 'NoDate';

            if (!groupedExercises[dateIndex]) {
                groupedExercises[dateIndex] = {
                    start: dayjs(dateValue).startOf('week'),
                    end: dayjs(dateValue).endOf('week'),
                    isCollapsed: dateValue.isBefore(dayjs(), 'week'),
                    isCurrentWeek: dateValue.isSame(dayjs(), 'week'),
                    exercises: [],
                };
            }

            groupedExercises[dateIndex].exercises.push(exercise);
        });

        // // Sort the keys of groupedExercises
        // const sortedKeys = Object.keys(groupedExercises).sort((a, b) => {
        //     // Place the "no due date" key first
        //     if (a === noDueDateKey) {
        //         return 1;
        //     }
        //     if (b === noDueDateKey) {
        //         return -1;
        //     }
        //     // Sort other keys based on your criteria
        //     return a.localeCompare(b);
        // });
        //
        // // Create a new object with sorted keys
        // const sortedGroupedExercises: GroupedExercisesByWeek = {};
        // for (const key of sortedKeys) {
        //     sortedGroupedExercises[key] = groupedExercises[key];
        // }
        //
        // this.exerciseGroups = sortedGroupedExercises;

        this.exerciseGroups = groupedExercises;
    }

    private updateNextRelevantExercise() {
        const nextExercise = this.exerciseService.getNextExerciseForHours(
            this.course?.exercises?.filter((exercise) => CourseExercisesComponent.fulfillsCurrentFilter(exercise, this.activeFilters)),
            12,
            this.currentUser,
        );
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
}

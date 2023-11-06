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

export const WEEK_EXERCISE_GROUP_FORMAT_STRING = 'YYYY-MM-DD';

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
    @Input() activeFilters: Set<ExerciseFilter>;
    @Input() sortingAttribute: SortingAttribute;

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
        const noDueDateExercises: Exercise[] = [];

        exercises?.forEach((exercise) => {
            const dateValue = CourseExercisesComponent.getSortingAttributeFromExercise(exercise, this.sortingAttribute);
            if (!dateValue) {
                noDueDateExercises.push(exercise);
                return;
            }
            const dateIndex = dateValue ? dayjs(dateValue).startOf('week').format(WEEK_EXERCISE_GROUP_FORMAT_STRING) : 'NoDate';

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

        // Exercises without due date shall always be displayed at the bottom
        if (noDueDateExercises.length) {
            groupedExercises[noDueDateKey] = {
                exercises: noDueDateExercises,
                isCurrentWeek: false,
                isCollapsed: false,
            };
        }

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

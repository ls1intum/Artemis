import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { CourseExercisesComponent, ExerciseFilter, ExerciseWithDueDate } from 'app/overview/course-exercises/course-exercises.component';
import { Course } from 'app/entities/course.model';
import { faAngleDown, faAngleUp } from '@fortawesome/free-solid-svg-icons';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { getAsMutableObject } from 'app/shared/util/utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-course-exercises-grouped-by-week',
    templateUrl: './course-exercises-grouped-by-week.component.html',
    styleUrls: ['../course-overview.scss'],
})
export class CourseExercisesGroupedByWeekComponent implements OnInit, OnChanges {
    @Input() course: Course;
    @Input() exerciseForGuidedTour?: Exercise;
    @Input() weeklyIndexKeys: string[];
    @Input() immutableWeeklyExercisesGrouped: object;
    @Input() activeFilters: Set<ExerciseFilter>;

    weeklyExercisesGrouped: object;
    nextRelevantExercise?: ExerciseWithDueDate;

    faAngleUp = faAngleUp;
    faAngleDown = faAngleDown;

    private currentUser?: User;

    constructor(
        private exerciseService: ExerciseService,
        private accountService: AccountService,
    ) {}

    ngOnInit() {
        this.weeklyExercisesGrouped = getAsMutableObject(this.immutableWeeklyExercisesGrouped);

        this.accountService.identity().then((user) => {
            this.currentUser = user;
            this.updateNextRelevantExercise();
        });
    }

    ngOnChanges() {
        this.weeklyExercisesGrouped = getAsMutableObject(this.immutableWeeklyExercisesGrouped);
        this.updateNextRelevantExercise();
    }

    /**
     * Checks whether an exercise is visible to students or not
     * @param exercise The exercise which should be checked
     */
    isVisibleToStudents(exercise: Exercise): boolean | undefined {
        return !this.activeFilters.has(ExerciseFilter.UNRELEASED) || (exercise as QuizExercise)?.visibleToStudents;
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

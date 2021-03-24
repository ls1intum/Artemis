import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { ExerciseRowType } from 'app/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { Course } from 'app/entities/course.model';
import { CachingStrategy } from 'app/shared/image/secured-image.component';

@Component({
    selector: 'jhi-course-management-card',
    templateUrl: './course-management-card.component.html',
    styleUrls: ['course-management-card.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseManagementCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    CachingStrategy = CachingStrategy;
    @Input() course: Course;
    @Input() courseStatistics: CourseManagementOverviewStatisticsDto;
    @Input() courseWithExercises: Course;
    @Input() courseWithUsers: Course;
    @Input() isGuidedTour: boolean;

    statisticsPerExercise = new Map<number, CourseManagementOverviewExerciseStatisticsDTO>();

    futureExercises: Exercise[];
    currentExercises: Exercise[];
    exercisesInAssessment: Exercise[];
    pastExercises: Exercise[];

    showFutureExercises = false;
    showCurrentExercises = true;
    showExercisesInAssessment = true;
    showPastExercises = false;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;

    private statisticsSorted = false;
    private exercisesSorted = false;

    ngOnChanges() {
        // Only sort one time once loaded
        if (!this.statisticsSorted && this.courseStatistics && this.courseStatistics.exerciseDTOS?.length > 0) {
            this.statisticsSorted = true;
            this.courseStatistics.exerciseDTOS.forEach((dto) => (this.statisticsPerExercise[dto.exerciseId!] = dto));
        }

        // Only sort one time once loaded
        if (this.exercisesSorted || !this.courseWithExercises || !this.courseWithExercises.exercises) {
            return;
        }

        this.sortExercises(this.courseWithExercises.exercises);

        // Directly show future exercises if there are no current exercises for the students or to assess
        this.showFutureExercises = this.currentExercises?.length === 0 && this.exercisesInAssessment?.length === 0;

        // If there are no future exercises either, show the past exercises by default
        this.showPastExercises = this.futureExercises?.length === 0 && this.currentExercises?.length === 0 && this.exercisesInAssessment?.length === 0;
    }

    /**
     * Sorts the given exercises into the future, current, in assessment and past exercise categories
     * - Future exercises are the first five exercises to be released soon which have a release date not longer than seven days in the future
     * - Current exercises are all exercises with a past release date and a due date in the future
     * - Exercises in assessment are all exercises with a due date in the past and an assessment due date in the future
     * - Past exercises are all exercises with an assessment due date (or due date if there is no assessment due date) not longer than seven days in the past
     *
     * @param exercises the exercises to sort into future, current, in assessment and past exercise categories
     */
    private sortExercises(exercises: Exercise[]): void {
        this.exercisesSorted = true;

        const inSevenDays = moment().add(7, 'days').endOf('day');
        const sevenDaysAgo = moment().subtract(7, 'days').startOf('day');

        this.futureExercises = exercises
            .filter((exercise) => exercise.releaseDate && exercise.releaseDate > moment() && exercise.releaseDate <= inSevenDays)
            .sort((exerciseA, exerciseB) => {
                return exerciseA.releaseDate!.valueOf() - exerciseB.releaseDate!.valueOf();
            })
            .slice(0, 5);

        this.currentExercises = exercises.filter(
            (exercise) =>
                (exercise.releaseDate && exercise.releaseDate <= moment() && (!exercise.dueDate || exercise.dueDate > moment())) ||
                (!exercise.releaseDate && exercise.dueDate && exercise.dueDate > moment()),
        );

        this.exercisesInAssessment = exercises.filter(
            (exercise) => exercise.dueDate && exercise.dueDate <= moment() && exercise.assessmentDueDate && exercise.assessmentDueDate > moment(),
        );

        this.pastExercises = exercises.filter(
            (exercise) =>
                (!exercise.assessmentDueDate && exercise.dueDate && exercise.dueDate <= moment() && exercise.dueDate >= sevenDaysAgo) ||
                (exercise.assessmentDueDate && exercise.assessmentDueDate <= moment() && exercise.assessmentDueDate >= sevenDaysAgo),
        );
    }
}

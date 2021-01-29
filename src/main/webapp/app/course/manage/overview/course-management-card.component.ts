import { Component, Input, OnChanges } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { CourseExerciseStatisticsDTO } from 'app/exercises/shared/exercise/exercise-statistics-dto.model';
import { ExerciseRowType } from './course-management-exercise-row.component';
import { CourseManagementOverviewCourseDto } from 'app/course/manage/course-management-overview-course-dto.model';

@Component({
    selector: 'jhi-course-management-card',
    templateUrl: './course-management-card.component.html',
    styleUrls: ['course-management-card.scss'],
})
export class CourseManagementCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    @Input() course: Course;
    @Input() courseStatistic: CourseManagementOverviewCourseDto;
    @Input() isGuidedTour: boolean;

    futureExercises: Exercise[];
    currentExercises: Exercise[];
    exercisesInAssessment: Exercise[];
    pastExercises: Exercise[];

    showFutureExercises = true;
    showCurrentExercises = true;
    showExercisesInAssessment = true;
    showPastExercises = false;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;

    private statistics = new Map<number, CourseExerciseStatisticsDTO>();

    ngOnChanges() {
        // Only display once loaded
        if (!this.courseStatistic) {
            return;
        }

        const exercises = this.courseStatistic.exercises;
        this.futureExercises = exercises
            .filter((e) => e.releaseDate && moment(e.releaseDate) > moment())
            .sort((a, b) => {
                return moment(a.releaseDate).valueOf() - moment(b.releaseDate).valueOf();
            })
            .slice(0, 5);
        this.currentExercises = exercises.filter((e) => (!e.releaseDate || moment(e.releaseDate) <= moment()) && (!e.dueDate || moment(e.dueDate) > moment()));
        this.exercisesInAssessment = exercises.filter((e) => (!e.dueDate || moment(e.dueDate) <= moment()) && e.assessmentDueDate && moment(e.assessmentDueDate) > moment());
        this.pastExercises = exercises.filter(
            (e) => (!e.assessmentDueDate && e.dueDate && moment(e.dueDate) <= moment()) || (e.assessmentDueDate && moment(e.assessmentDueDate) <= moment()),
        );

        this.courseStatistic.exerciseDTOS.forEach((e) => (this.statistics[e.exerciseId!] = e));
    }

    getStatisticForExercise(exercise: Exercise) {
        return this.statistics[exercise.id!];
    }
}

import { Component, Input, OnChanges } from '@angular/core';
import { Course } from 'app/entities/course.model';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { CourseManagementOverviewCourseDto } from 'app/course/manage/course-management-overview-course-dto.model';
import { ExerciseRowType } from 'app/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/entities/course-management-overview-exercise-statistics-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/course-management-overview-statistics-dto.model';

@Component({
    selector: 'jhi-course-management-card',
    templateUrl: './course-management-card.component.html',
    styleUrls: ['course-management-card.scss'],
})
export class CourseManagementCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    @Input() course: Course;
    @Input() courseDetails: CourseManagementOverviewCourseDto;
    @Input() courseStatistics: CourseManagementOverviewStatisticsDto;
    @Input() isGuidedTour: boolean;

    statisticsPerExercise = new Map<number, CourseManagementOverviewExerciseStatisticsDTO>();

    futureExercises: CourseManagementOverviewExerciseStatisticsDTO[];
    currentExercises: CourseManagementOverviewExerciseStatisticsDTO[];
    exercisesInAssessment: CourseManagementOverviewExerciseStatisticsDTO[];
    pastExercises: CourseManagementOverviewExerciseStatisticsDTO[];

    showFutureExercises = false;
    showCurrentExercises = true;
    showExercisesInAssessment = true;
    showPastExercises = false;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;

    ngOnChanges() {
        console.log('details');
        console.log(this.courseDetails);
        console.log('stats');
        console.log(this.courseStatistics);

        // Only display once loaded
        if (this.courseStatistics && this.courseStatistics.exerciseStatisticsDTOs) {
            this.courseStatistics.exerciseStatisticsDTOs.forEach((dto) => (this.statisticsPerExercise[dto.exerciseId!] = dto));
        }

        // Only display once loaded
        if (!this.courseDetails || !this.courseDetails.exerciseDTOS) {
            return;
        }

        const exercises = this.courseDetails.exerciseDTOS;
        this.futureExercises = exercises
            .filter((e) => e.releaseDate && moment(e.releaseDate) > moment() && !(moment(e.releaseDate) > moment().add(7, 'days').endOf('day')))
            .sort((a, b) => {
                return moment(a.releaseDate).valueOf() - moment(b.releaseDate).valueOf();
            })
            .slice(0, 5);
        this.currentExercises = exercises.filter((e) => (!e.releaseDate || moment(e.releaseDate) <= moment()) && (!e.dueDate || moment(e.dueDate) > moment()));
        this.exercisesInAssessment = exercises.filter((e) => (!e.dueDate || moment(e.dueDate) <= moment()) && e.assessmentDueDate && moment(e.assessmentDueDate) > moment());
        this.pastExercises = exercises.filter(
            (e) =>
                (!e.assessmentDueDate && e.dueDate && moment(e.dueDate) <= moment() && !(moment(e.dueDate) < moment().subtract(7, 'days').startOf('day'))) ||
                (e.assessmentDueDate && moment(e.assessmentDueDate) <= moment() && !(moment(e.assessmentDueDate) < moment().subtract(7, 'days').startOf('day'))),
        );
    }
}

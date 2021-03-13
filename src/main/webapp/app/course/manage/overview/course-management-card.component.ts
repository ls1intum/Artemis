import { Component, Input, OnChanges } from '@angular/core';
import { ARTEMIS_DEFAULT_COLOR } from 'app/app.constants';
import { ExerciseType } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { ExerciseRowType } from 'app/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/course/manage/overview/course-management-overview-statistics-dto.model';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';
import { CourseManagementOverviewDto } from 'app/course/manage/overview/course-management-overview-dto.model';
import { Course } from 'app/entities/course.model';
import { CachingStrategy } from 'app/shared/image/secured-image.component';

@Component({
    selector: 'jhi-course-management-card',
    templateUrl: './course-management-card.component.html',
    styleUrls: ['course-management-card.scss'],
})
export class CourseManagementCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    CachingStrategy = CachingStrategy;
    @Input() course: Course;
    @Input() courseDetails: CourseManagementOverviewDto;
    @Input() courseStatistics: CourseManagementOverviewStatisticsDto;
    @Input() isGuidedTour: boolean;

    statisticsPerExercise = new Map<number, CourseManagementOverviewExerciseStatisticsDTO>();

    futureExercises: CourseManagementOverviewExerciseDetailsDTO[];
    currentExercises: CourseManagementOverviewExerciseDetailsDTO[];
    exercisesInAssessment: CourseManagementOverviewExerciseDetailsDTO[];
    pastExercises: CourseManagementOverviewExerciseDetailsDTO[];

    showFutureExercises = false;
    showCurrentExercises = true;
    showExercisesInAssessment = true;
    showPastExercises = false;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;

    ngOnChanges() {
        // Only display once loaded
        if (this.courseStatistics && this.courseStatistics.exerciseDTOS?.length > 0) {
            this.courseStatistics.exerciseDTOS.forEach((dto) => (this.statisticsPerExercise[dto.exerciseId!] = dto));
        }

        // Only display once loaded
        if (!this.courseDetails || !this.courseDetails.exerciseDetails) {
            return;
        }

        const exercises = this.courseDetails.exerciseDetails;
        this.futureExercises = exercises
            .filter((e) => !e.releaseDate || (e.releaseDate && e.releaseDate > moment() && !(e.releaseDate > moment().add(7, 'days').endOf('day'))))
            .sort((a, b) => {
                if (a.releaseDate && b.releaseDate) {
                    return a.releaseDate!.valueOf() - b.releaseDate!.valueOf();
                } else if (!a.releaseDate) {
                    return 1;
                }
                return -1;
            })
            .slice(0, 5);
        this.currentExercises = exercises.filter((e) => e.releaseDate && e.releaseDate <= moment() && (!e.dueDate || e.dueDate > moment()));
        this.exercisesInAssessment = exercises.filter((e) => e.dueDate && e.dueDate <= moment() && e.assessmentDueDate && e.assessmentDueDate > moment());
        this.pastExercises = exercises.filter(
            (e) =>
                (!e.assessmentDueDate && e.dueDate && e.dueDate <= moment() && !(e.dueDate < moment().subtract(7, 'days').startOf('day'))) ||
                (e.assessmentDueDate && e.assessmentDueDate <= moment() && !(e.assessmentDueDate < moment().subtract(7, 'days').startOf('day'))),
        );
    }
}

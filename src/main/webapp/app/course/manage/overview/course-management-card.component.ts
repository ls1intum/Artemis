import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
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
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseManagementCardComponent implements OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    CachingStrategy = CachingStrategy;
    @Input() course: Course;
    @Input() courseDetails: CourseManagementOverviewDto;
    @Input() courseStatistics: CourseManagementOverviewStatisticsDto;
    @Input() courseWithUsers: Course;
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
    quizStatus = {
        CLOSED: 'CLOSED',
        OPEN_FOR_PRACTICE: 'OPEN_FOR_PRACTICE',
        ACTIVE: 'ACTIVE',
        VISIBLE: 'VISIBLE',
        HIDDEN: 'HIDDEN',
    };

    private statisticsSorted = false;
    private exercisesSorted = false;

    ngOnChanges() {
        // Only sort one time once loaded
        if (!this.statisticsSorted && this.courseStatistics && this.courseStatistics.exerciseDTOS?.length > 0) {
            this.statisticsSorted = true;
            this.courseStatistics.exerciseDTOS.forEach((dto) => (this.statisticsPerExercise[dto.exerciseId!] = dto));
        }

        // Only sort one time once loaded
        if (this.exercisesSorted || !this.courseDetails || !this.courseDetails.exerciseDetails) {
            return;
        }

        this.exercisesSorted = true;
        const exercises = this.courseDetails.exerciseDetails;
        const inSevenDays = moment().add(7, 'days').endOf('day');
        const sevenDaysAgo = moment().subtract(7, 'days').startOf('day');
        this.futureExercises = exercises
            .filter((e) => (e.releaseDate && e.releaseDate > moment() && e.releaseDate <= inSevenDays) || e.quizStatus === this.quizStatus.VISIBLE)
            .sort((a, b) => {
                return a.releaseDate!.valueOf() - b.releaseDate!.valueOf();
            })
            .slice(0, 5);
        this.currentExercises = exercises.filter(
            (e) =>
                ((e.releaseDate && e.releaseDate <= moment() && (!e.dueDate || e.dueDate > moment())) || (!e.releaseDate && e.dueDate && e.dueDate > moment())) &&
                (!e.quizStatus || e.quizStatus === this.quizStatus.ACTIVE),
        );
        this.exercisesInAssessment = exercises.filter((e) => e.dueDate && e.dueDate <= moment() && e.assessmentDueDate && e.assessmentDueDate > moment());
        this.pastExercises = exercises.filter(
            (e) =>
                (!e.assessmentDueDate && e.dueDate && e.dueDate <= moment() && e.dueDate >= sevenDaysAgo) ||
                (e.assessmentDueDate && e.assessmentDueDate <= moment() && e.assessmentDueDate >= sevenDaysAgo) ||
                ((e.quizStatus === this.quizStatus.OPEN_FOR_PRACTICE || e.quizStatus === this.quizStatus.CLOSED) && e.releaseDate && e.releaseDate >= sevenDaysAgo),
        );
        this.showPastExercises = this.futureExercises?.length === 0 && this.currentExercises?.length === 0 && this.exercisesInAssessment?.length === 0;
    }
}

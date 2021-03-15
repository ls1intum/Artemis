import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { CourseManagementOverviewExerciseDetailsDTO } from 'app/course/manage/overview/course-management-overview-exercise-details-dto.model';
import { Course } from 'app/entities/course.model';

export enum ExerciseRowType {
    FUTURE = 'future',
    CURRENT = 'current',
    ASSESSING = 'assessment',
    PAST = 'past',
}

@Component({
    selector: 'jhi-course-management-exercise-row',
    templateUrl: './course-management-exercise-row.component.html',
    styleUrls: ['course-management-exercise-row.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseManagementExerciseRowComponent implements OnChanges {
    @Input() course: Course;
    @Input() details: CourseManagementOverviewExerciseDetailsDTO;
    @Input() statistic: CourseManagementOverviewExerciseStatisticsDTO;
    @Input() rowType: ExerciseRowType;

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

    JSON = JSON;

    hasLeftoverAssessments = false;
    displayTitle: string;
    averageScoreNumerator: number;
    icon: IconProp;
    iconTooltip: string;

    getIcon(type: ExerciseType | undefined): IconProp {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return 'keyboard';
            case ExerciseType.MODELING:
                return 'project-diagram';
            case ExerciseType.QUIZ:
                return 'check-double';
            case ExerciseType.TEXT:
                return 'font';
            default:
            case ExerciseType.FILE_UPLOAD:
                return 'file-upload';
        }
    }

    getIconTooltip(type: ExerciseType | undefined): string {
        switch (type) {
            case ExerciseType.PROGRAMMING:
                return 'artemisApp.exercise.isProgramming';
            case ExerciseType.MODELING:
                return 'artemisApp.exercise.isModeling';
            case ExerciseType.QUIZ:
                return 'artemisApp.exercise.isQuiz';
            case ExerciseType.TEXT:
                return 'artemisApp.exercise.isText';
            default:
            case ExerciseType.FILE_UPLOAD:
                return 'artemisApp.exercise.isFileUpload';
        }
    }

    private detailsLoaded = false;
    private statisticsLoaded = false;

    constructor() {}

    ngOnChanges() {
        if (this.details && !this.detailsLoaded) {
            this.detailsLoaded = true;
            this.displayTitle = this.details.exerciseTitle ?? '';
            this.icon = this.getIcon(this.details.exerciseType);
            this.iconTooltip = this.getIconTooltip(this.details.exerciseType);
        }

        if (!this.statistic || this.statisticsLoaded) {
            return;
        }

        this.statisticsLoaded = true;
        this.averageScoreNumerator = Math.round((this.statistic.averageScoreInPercent! * this.statistic.exerciseMaxPoints!) / 100);
    }
}

import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/entities/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Course } from 'app/entities/course.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { faBook, faExclamationTriangle, faFileSignature, faTable, faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faCalendarAlt } from '@fortawesome/free-regular-svg-icons';

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
    @Input() details: Exercise;
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
    averageScoreNumerator: number;
    icon: IconProp;
    iconTooltip: string;

    private detailsLoaded = false;
    private statisticsLoaded = false;

    // Icons
    faTimes = faTimes;
    faCalendarAlt = faCalendarAlt;
    faBook = faBook;
    faWrench = faWrench;
    faUsers = faUsers;
    faTable = faTable;
    faExclamationTriangle = faExclamationTriangle;
    faFileSignature = faFileSignature;

    constructor() {}

    ngOnChanges() {
        if (this.details && !this.detailsLoaded) {
            this.detailsLoaded = true;
            this.iconTooltip = getIconTooltip(this.details.type);
            this.setIcon(this.details.type);
        }

        if (!this.statistic || this.statisticsLoaded) {
            return;
        }

        this.statisticsLoaded = true;
        this.averageScoreNumerator = roundValueSpecifiedByCourseSettings((this.statistic.averageScoreInPercent! * this.statistic.exerciseMaxPoints!) / 100, this.course);
    }

    setIcon(exerciseType?: ExerciseType) {
        if (exerciseType) {
            this.icon = getIcon(exerciseType);
        }
    }
}

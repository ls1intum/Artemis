import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/core/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { faBook, faExclamationTriangle, faFileSignature, faTable, faTimes, faUsers, faWrench } from '@fortawesome/free-solid-svg-icons';
import { faCalendarAlt } from '@fortawesome/free-regular-svg-icons';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { ExerciseCategoriesComponent } from 'app/exercise/exercise-categories/exercise-categories.component';

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
    imports: [
        RouterLink,
        FaIconComponent,
        NgbTooltip,
        ExerciseCategoriesComponent,
        NgClass,
        TranslateDirective,
        ProgressBarComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class CourseManagementExerciseRowComponent implements OnChanges {
    @Input() course: Course;
    @Input() details: Exercise;
    @Input() statistic?: CourseManagementOverviewExerciseStatisticsDTO;
    @Input() rowType: ExerciseRowType;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;
    quizStatus = {
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

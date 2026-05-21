import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
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
export class CourseManagementExerciseRowComponent {
    readonly course = input.required<Course>();
    readonly details = input.required<Exercise>();
    readonly statistic = input<CourseManagementOverviewExerciseStatisticsDTO>();
    readonly rowType = input.required<ExerciseRowType>();

    // Expose enums to the template
    readonly exerciseType = ExerciseType;
    readonly exerciseRowType = ExerciseRowType;
    readonly quizStatus = {
        OPEN_FOR_PRACTICE: 'OPEN_FOR_PRACTICE',
        ACTIVE: 'ACTIVE',
        VISIBLE: 'VISIBLE',
        HIDDEN: 'HIDDEN',
    };

    readonly JSON = JSON;

    readonly hasLeftoverAssessments = signal(false);
    readonly averageScoreNumerator = signal<number | undefined>(undefined);

    readonly icon = computed<IconProp | undefined>(() => {
        const details = this.details();
        return details?.type ? getIcon(details.type) : undefined;
    });

    readonly iconTooltip = computed(() => {
        const details = this.details();
        return details?.type ? getIconTooltip(details.type) : '';
    });

    private statisticsLoaded = false;

    // Icons
    readonly faTimes = faTimes;
    readonly faCalendarAlt = faCalendarAlt;
    readonly faBook = faBook;
    readonly faWrench = faWrench;
    readonly faUsers = faUsers;
    readonly faTable = faTable;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faFileSignature = faFileSignature;

    constructor() {
        // Effect to process statistic changes
        effect(() => {
            const statistic = this.statistic();
            const course = this.course();
            if (!statistic || this.statisticsLoaded) {
                return;
            }

            this.statisticsLoaded = true;
            this.averageScoreNumerator.set(roundValueSpecifiedByCourseSettings((statistic.averageScoreInPercent! * statistic.exerciseMaxPoints!) / 100, course));
        });
    }
}

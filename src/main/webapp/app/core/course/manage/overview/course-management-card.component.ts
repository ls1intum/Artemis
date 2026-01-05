import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { ARTEMIS_DEFAULT_COLOR, MODULE_FEATURE_ATLAS, MODULE_FEATURE_EXAM, MODULE_FEATURE_LECTURE, MODULE_FEATURE_TUTORIALGROUP } from 'app/app.constants';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseRowType } from 'app/core/course/manage/overview/course-management-exercise-row.component';
import { CourseManagementOverviewExerciseStatisticsDTO } from 'app/core/course/manage/overview/course-management-overview-exercise-statistics-dto.model';
import { CourseManagementOverviewStatisticsDto } from 'app/core/course/manage/overview/course-management-overview-statistics-dto.model';
import { Course, isCommunicationEnabled, isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import {
    faAngleDown,
    faAngleUp,
    faChartBar,
    faClipboard,
    faComments,
    faFilePdf,
    faFlag,
    faGraduationCap,
    faListAlt,
    faNetworkWired,
    faPersonChalkboard,
    faQuestion,
    faSpinner,
    faTable,
    faUserCheck,
} from '@fortawesome/free-solid-svg-icons';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { NgStyle } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ImageComponent } from 'app/shared/image/image.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CourseManagementExerciseRowComponent } from './course-management-exercise-row.component';
import { CourseManagementOverviewStatisticsComponent } from './course-management-overview-statistics.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FeatureToggleHideDirective } from 'app/shared/feature-toggle/feature-toggle-hide.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { getContrastingTextColor } from 'app/shared/util/color.utils';

@Component({
    selector: 'jhi-course-management-card',
    templateUrl: './course-management-card.component.html',
    styleUrls: ['course-management-card.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        NgStyle,
        RouterLink,
        ImageComponent,
        TranslateDirective,
        FaIconComponent,
        CourseManagementExerciseRowComponent,
        CourseManagementOverviewStatisticsComponent,
        NgbTooltip,
        FeatureToggleHideDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        FeatureOverlayComponent,
    ],
})
export class CourseManagementCardComponent implements OnInit, OnChanges {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    private readonly profileService = inject(ProfileService);

    // TODO: can we merge the 3 courses here?
    @Input() course: Course;
    @Input() courseStatistics?: CourseManagementOverviewStatisticsDto;
    @Input() courseWithExercises: Course | undefined;
    @Input() courseWithUsers: Course | undefined;

    atlasEnabled = false;
    examEnabled = false;
    lectureEnabled = false;
    tutorialGroupEnabled = false;

    statisticsPerExercise = new Map<number, CourseManagementOverviewExerciseStatisticsDTO>();

    futureExercises: Exercise[];
    currentExercises: Exercise[];
    exercisesInAssessment: Exercise[];
    pastExercises: Exercise[];
    pastExerciseCount: number;

    showFutureExercises = false;
    showCurrentExercises = true;
    showExercisesInAssessment = true;
    showPastExercises = false;

    // Expose enums to the template
    exerciseType = ExerciseType;
    exerciseRowType = ExerciseRowType;

    private statisticsSorted = false;
    private exercisesSorted = false;

    // Icons
    faTable = faTable;
    faUserCheck = faUserCheck;
    faFlag = faFlag;
    faNetworkWired = faNetworkWired;
    faListAlt = faListAlt;
    faChartBar = faChartBar;
    faFilePdf = faFilePdf;
    faComments = faComments;
    faClipboard = faClipboard;
    faGraduationCap = faGraduationCap;
    faAngleDown = faAngleDown;
    faAngleUp = faAngleUp;
    faPersonChalkboard = faPersonChalkboard;
    faSpinner = faSpinner;
    faQuestion = faQuestion;

    courseColor: string;
    contentColor: string;

    readonly FeatureToggle = FeatureToggle;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    ngOnInit() {
        this.atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
        this.examEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_EXAM);
        this.lectureEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LECTURE);
        this.tutorialGroupEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_TUTORIALGROUP);
    }

    ngOnChanges() {
        const targetCourseColor = this.course.color || this.ARTEMIS_DEFAULT_COLOR;
        if (this.courseColor !== targetCourseColor) {
            this.courseColor = targetCourseColor;
            this.contentColor = getContrastingTextColor(this.courseColor);
        }

        // Only sort one time once loaded
        if (!this.statisticsSorted && this.courseStatistics && this.courseStatistics.exerciseDTOS?.length > 0) {
            this.statisticsSorted = true;
            this.courseStatistics.exerciseDTOS.forEach((dto) => {
                if (dto.exerciseId !== undefined) {
                    this.statisticsPerExercise.set(dto.exerciseId, dto);
                }
            });
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

        const inSevenDays = dayjs().add(7, 'days').endOf('day');

        this.futureExercises = exercises
            .filter((exercise) => exercise.releaseDate && exercise.releaseDate > dayjs() && exercise.releaseDate <= inSevenDays)
            .sort((exerciseA, exerciseB) => {
                return exerciseA.releaseDate!.valueOf() - exerciseB.releaseDate!.valueOf();
            })
            .slice(0, 5);

        this.currentExercises = exercises.filter(
            (exercise) =>
                (exercise.releaseDate && exercise.releaseDate <= dayjs() && (!exercise.dueDate || exercise.dueDate > dayjs())) ||
                (!exercise.releaseDate && exercise.dueDate && exercise.dueDate > dayjs()),
        );

        this.exercisesInAssessment = exercises.filter(
            (exercise) => exercise.dueDate && exercise.dueDate <= dayjs() && exercise.assessmentDueDate && exercise.assessmentDueDate > dayjs(),
        );

        const allPastExercises = exercises
            .filter(
                (exercise) =>
                    (!exercise.assessmentDueDate && exercise.dueDate && exercise.dueDate <= dayjs()) || (exercise.assessmentDueDate && exercise.assessmentDueDate <= dayjs()),
            )
            .sort((exerciseA, exerciseB) => {
                // Sort by assessment due date (or due date if there is no assessment due date) descending
                // Note: The server side statistic generation uses the same sorting
                return (exerciseB.assessmentDueDate ?? exerciseB.dueDate)!.valueOf() - (exerciseA.assessmentDueDate ?? exerciseA.dueDate)!.valueOf();
            });

        this.pastExerciseCount = allPastExercises.length;
        this.pastExercises = allPastExercises.slice(0, 5);
    }
}

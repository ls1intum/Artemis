import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
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
export class CourseManagementCardComponent {
    readonly ARTEMIS_DEFAULT_COLOR = ARTEMIS_DEFAULT_COLOR;

    private readonly profileService = inject(ProfileService);

    // TODO: can we merge the 3 courses here?
    readonly course = input.required<Course>();
    readonly courseStatistics = input<CourseManagementOverviewStatisticsDto>();
    readonly courseWithExercises = input<Course>();
    readonly courseWithUsers = input<Course>();

    readonly atlasEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
    readonly examEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_EXAM);
    readonly lectureEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_LECTURE);
    readonly tutorialGroupEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_TUTORIALGROUP);

    readonly statisticsPerExercise = signal(new Map<number, CourseManagementOverviewExerciseStatisticsDTO>());

    readonly futureExercises = signal<Exercise[]>([]);
    readonly currentExercises = signal<Exercise[]>([]);
    readonly exercisesInAssessment = signal<Exercise[]>([]);
    readonly pastExercises = signal<Exercise[]>([]);
    readonly pastExerciseCount = signal<number>(0);

    readonly showFutureExercises = signal(false);
    readonly showCurrentExercises = signal(true);
    readonly showExercisesInAssessment = signal(true);
    readonly showPastExercises = signal(false);

    // Expose enums to the template
    readonly exerciseType = ExerciseType;
    readonly exerciseRowType = ExerciseRowType;

    private statisticsSorted = false;
    private exercisesSorted = false;

    // Icons
    readonly faTable = faTable;
    readonly faUserCheck = faUserCheck;
    readonly faFlag = faFlag;
    readonly faNetworkWired = faNetworkWired;
    readonly faListAlt = faListAlt;
    readonly faChartBar = faChartBar;
    readonly faFilePdf = faFilePdf;
    readonly faComments = faComments;
    readonly faClipboard = faClipboard;
    readonly faGraduationCap = faGraduationCap;
    readonly faAngleDown = faAngleDown;
    readonly faAngleUp = faAngleUp;
    readonly faPersonChalkboard = faPersonChalkboard;
    readonly faSpinner = faSpinner;
    readonly faQuestion = faQuestion;

    readonly courseColor = computed(() => this.course().color || this.ARTEMIS_DEFAULT_COLOR);
    readonly contentColor = computed(() => getContrastingTextColor(this.courseColor()));

    readonly FeatureToggle = FeatureToggle;

    readonly isCommunicationEnabled = isCommunicationEnabled;
    readonly isMessagingEnabled = isMessagingEnabled;

    constructor() {
        // Effect to process courseStatistics changes
        effect(() => {
            const courseStatistics = this.courseStatistics();
            if (!this.statisticsSorted && courseStatistics && courseStatistics.exerciseDTOS?.length > 0) {
                this.statisticsSorted = true;
                const newMap = new Map<number, CourseManagementOverviewExerciseStatisticsDTO>();
                courseStatistics.exerciseDTOS.forEach((dto) => {
                    if (dto.exerciseId !== undefined) {
                        newMap.set(dto.exerciseId, dto);
                    }
                });
                this.statisticsPerExercise.set(newMap);
            }
        });

        // Effect to process courseWithExercises changes
        effect(() => {
            const courseWithExercises = this.courseWithExercises();
            if (this.exercisesSorted || !courseWithExercises || !courseWithExercises.exercises) {
                return;
            }

            this.sortExercises(courseWithExercises.exercises);

            // Directly show future exercises if there are no current exercises for the students or to assess
            // Use untracked to prevent this effect from re-running when these signals change
            untracked(() => {
                this.showFutureExercises.set(this.currentExercises().length === 0 && this.exercisesInAssessment().length === 0);
                // If there are no future exercises either, show the past exercises by default
                this.showPastExercises.set(this.futureExercises().length === 0 && this.currentExercises().length === 0 && this.exercisesInAssessment().length === 0);
            });
        });
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

        this.futureExercises.set(
            exercises
                .filter((exercise) => exercise.releaseDate && exercise.releaseDate > dayjs() && exercise.releaseDate <= inSevenDays)
                .sort((exerciseA, exerciseB) => {
                    return exerciseA.releaseDate!.valueOf() - exerciseB.releaseDate!.valueOf();
                })
                .slice(0, 5),
        );

        this.currentExercises.set(
            exercises.filter(
                (exercise) =>
                    (exercise.releaseDate && exercise.releaseDate <= dayjs() && (!exercise.dueDate || exercise.dueDate > dayjs())) ||
                    (!exercise.releaseDate && exercise.dueDate && exercise.dueDate > dayjs()),
            ),
        );

        this.exercisesInAssessment.set(
            exercises.filter((exercise) => exercise.dueDate && exercise.dueDate <= dayjs() && exercise.assessmentDueDate && exercise.assessmentDueDate > dayjs()),
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

        this.pastExerciseCount.set(allPastExercises.length);
        this.pastExercises.set(allPastExercises.slice(0, 5));
    }
}

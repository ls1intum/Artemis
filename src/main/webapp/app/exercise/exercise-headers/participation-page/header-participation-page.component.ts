import { Component, ViewEncapsulation, computed, input } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, getCourseFromExercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass, NgStyle } from '@angular/common';
import { DifficultyBadgeComponent } from '../difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from '../included-in-score-badge/included-in-score-badge.component';
import { SubmissionResultStatusComponent } from 'app/course/overview/submission-result-status/submission-result-status.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';

@Component({
    selector: 'jhi-header-participation-page',
    templateUrl: './header-participation-page.component.html',
    styleUrls: ['./header-participation-page.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        NgClass,
        NgStyle,
        DifficultyBadgeComponent,
        IncludedInScoreBadgeComponent,
        SubmissionResultStatusComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class HeaderParticipationPageComponent {
    readonly ButtonType = ButtonType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly title = input<string>();
    readonly exercise = input<Exercise>();
    readonly participation = input<StudentParticipation>();

    readonly exerciseStatusBadge = computed<string>(() => {
        const exercise = this.exercise();
        if (exercise) {
            return hasExerciseDueDatePassed(exercise, this.participation()) ? 'bg-danger' : 'bg-success';
        }
        return 'bg-success';
    });

    readonly exerciseCategories = computed<ExerciseCategory[] | undefined>(() => {
        const exercise = this.exercise();
        if (exercise) {
            return exercise.categories || [];
        }
        return undefined;
    });

    readonly dueDate = computed<dayjs.Dayjs | undefined>(() => {
        const exercise = this.exercise();
        if (exercise) {
            return getExerciseDueDate(exercise, this.participation());
        }
        return undefined;
    });

    readonly achievedPoints = computed<number | undefined>(() => {
        const exercise = this.exercise();
        if (exercise) {
            const result = getLatestResultOfStudentParticipation(this.participation(), false, true);
            if (result?.rated) {
                return roundValueSpecifiedByCourseSettings((result.score! * exercise.maxPoints!) / 100, getCourseFromExercise(exercise));
            }
        }
        return undefined;
    });

    getIcon = getIcon;

    /**
     * Returns false if it is an exam exercise and the publishResultsDate is in the future, true otherwise
     */
    get resultsPublished(): boolean {
        const exercise = this.exercise();
        if (exercise?.exerciseGroup?.exam) {
            if (exercise.exerciseGroup.exam.publishResultsDate) {
                return dayjs().isAfter(exercise.exerciseGroup.exam.publishResultsDate);
            }
            // default to false if it is an exam exercise but the publishResultsDate is not set
            return false;
        }
        return true;
    }
}

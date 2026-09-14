import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { QuizLiveHeaderInfo } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';

/**
 * Remaining time for a live quiz, sized for the exercise title bar.
 *
 * Separate from the exam mode timer on purpose: exam mode uses its own header components and never renders
 * this one, so the two share an appearance rather than a lifecycle.
 */
@Component({
    selector: 'jhi-quiz-exercise-countdown',
    templateUrl: './quiz-exercise-countdown.component.html',
    styleUrl: './quiz-exercise-countdown.component.scss',
    imports: [TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QuizExerciseCountdownComponent {
    readonly info = input<QuizLiveHeaderInfo>();

    /** What the bar shows, or undefined while neither a countdown nor a duration applies. */
    readonly display = computed<{ labelKey: string; value: string; severity?: string } | undefined>(() => {
        const info = this.info();
        if (info?.showRemainingTime) {
            return { labelKey: 'artemisApp.quizExercise.remainingTime', value: info.remainingTimeText ?? '', severity: info.remainingTimeColor };
        }
        if (info?.showDuration) {
            return { labelKey: 'artemisApp.quizExercise.duration', value: info.durationText ?? '' };
        }
        return undefined;
    });
}

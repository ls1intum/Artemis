import { Component, Input } from '@angular/core';
import { faStopwatch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-quiz-timer-bar',
    templateUrl: './quiz-timer-bar.component.html',
    styleUrl: './quiz-timer-bar.component.scss',
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class QuizTimerBarComponent {
    style?: string;

    @Input() timeLimit: number;
    @Input() remainingSeconds: number | undefined;
    @Input() open: boolean;

    faStopwatch = faStopwatch;

    protected readonly JSON = JSON;

    get displayedRemainingSeconds(): number {
        if (this.remainingSeconds === undefined) {
            // This is to show a full bar while fade-in and fade-out of bar
            return this.timeLimit;
        }
        return Math.max(this.remainingSeconds, 0);
    }

    get progress(): number {
        if (this.timeLimit <= 0) {
            return 100;
        }

        return Math.max(0, Math.min(100, (this.displayedRemainingSeconds / this.timeLimit) * 100));
    }
}

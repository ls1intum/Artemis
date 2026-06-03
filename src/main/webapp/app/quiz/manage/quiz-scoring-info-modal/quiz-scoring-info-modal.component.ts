import { Component, signal } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { DialogModule } from 'primeng/dialog';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-quiz-scoring-info-modal',
    templateUrl: './quiz-scoring-info-modal.component.html',
    imports: [TranslateDirective, FaIconComponent, DialogModule, ArtemisTranslatePipe],
})
export class QuizScoringInfoModalComponent {
    // Icons
    farQuestionCircle = faQuestionCircle;

    readonly isVisible = signal(false);

    /**
     * Open the info dialog.
     */
    open() {
        this.isVisible.set(true);
    }
}

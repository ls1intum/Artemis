import { Component, signal } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { TumAetUiTableDirective } from '@tumaet/ui-angular/table-directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-quiz-scoring-info-modal',
    templateUrl: './quiz-scoring-info-modal.component.html',
    imports: [TranslateDirective, FaIconComponent, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiDialogComponent, TumAetUiTableDirective],
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

import { Component, signal } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-system-info-modal',
    templateUrl: './grading-info-modal.component.html',
    imports: [TranslateDirective, FaIconComponent, DialogModule, ButtonModule, ArtemisTranslatePipe],
})
export class GradingInfoModalComponent {
    // Icons
    farQuestionCircle = faQuestionCircle;

    visible = signal(false);

    /**
     * Open the info dialog.
     */
    open() {
        this.visible.set(true);
    }

    /**
     * Close the info dialog.
     */
    close() {
        this.visible.set(false);
    }
}

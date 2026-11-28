import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { TumAetUiTableDirective } from '@tumaet/ui-angular/table-directive';
import { Component, signal } from '@angular/core';
import { faQuestionCircle } from '@fortawesome/free-regular-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-system-info-modal',
    templateUrl: './grading-info-modal.component.html',
    imports: [TranslateDirective, FaIconComponent, TumAetUiDialogComponent, TumAetUiButtonDirective, ArtemisTranslatePipe, TumAetUiTableDirective],
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

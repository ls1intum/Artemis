import { Component, input, model, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { DialogModule } from 'primeng/dialog';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Modal component that warns users about unsaved changes before discarding them.
 */
@Component({
    selector: 'jhi-unsaved-changes-warning',
    templateUrl: './unsaved-changes-warning.component.html',
    imports: [FormsModule, TranslateDirective, ButtonComponent, DialogModule, ArtemisTranslatePipe],
})
export class UnsavedChangesWarningComponent {
    readonly visible = model<boolean>(false);

    /** The warning message to display */
    readonly textMessage = input<string>();

    /** Emitted when the user decides to discard their changes */
    readonly discarded = output<void>();

    /**
     * Closes the modal and signals that the changes should be discarded
     */
    discardContent() {
        this.discarded.emit();
        this.visible.set(false);
    }

    /**
     * Closes the modal without discarding changes (stay editing)
     */
    continueEditing() {
        this.visible.set(false);
    }
}

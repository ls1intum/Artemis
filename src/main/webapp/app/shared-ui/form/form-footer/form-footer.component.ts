import { Component, computed, inject, input, output } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { translateValidationReasons } from 'app/exercise/util/exercise-validation.util';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { faBan, faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SwitchEditModeButtonComponent } from 'app/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ExerciseUpdateNotificationComponent } from 'app/exercise/exercise-update-notification/exercise-update-notification.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-form-footer',
    templateUrl: 'form-footer.component.html',
    styleUrls: ['form-footer.component.scss'],
    imports: [
        NgbTooltip,
        SwitchEditModeButtonComponent,
        HelpIconComponent,
        ExerciseUpdateNotificationComponent,
        TranslateDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
    ],
})
export class FormFooterComponent {
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly ButtonSize = ButtonSize;
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faSpinner = faSpinner;

    isSaving = input(false);
    isDisabled = input(false);
    invalidReasons = input<ValidationReason[]>([]);
    /**
     * Reads the reasons from the current state of the form. The displayed {@link invalidReasons} come from the last change
     * detection pass and can lag behind a form that mutates through template-driven fields (for instance while a date
     * picker still processes a blur and a selection), so a click that they block is checked against the form again.
     */
    revalidate = input<(() => ValidationReason[]) | undefined>();
    notificationText = input<string | undefined>();
    switchEditMode = input<(() => void) | undefined>();
    isImport = input<boolean>();
    isCreation = input<boolean>();
    isSimpleMode = input<boolean>();
    areAuxiliaryRepositoriesValid = input<boolean>(true);

    notificationTextChange = output<string>();
    save = output<void>();
    onCancel = output<void>();

    saveTitle = computed<string>(() => (this.isImport() ? 'entity.action.import' : this.isCreation() ? 'entity.action.generate' : 'entity.action.save'));

    isSubmitDisabled = computed<boolean>(() => !!this.invalidReasons().length || this.isDisabled() || this.isSaving());

    /** Target of the submit buttons' aria-describedby; the reason list is rendered under this id. */
    protected readonly invalidReasonsId = 'form-footer-invalid-reasons';

    protected readonly invalidReasonTexts = computed<string[]>(() => {
        this.currentLocale();
        return translateValidationReasons(this.invalidReasons(), this.translateService);
    });

    onSwitchEditMode() {
        this.switchEditMode()?.();
    }

    // The submit buttons are aria-disabled rather than disabled, so they stay focusable and can explain
    // themselves. That leaves them clickable, hence the guards.
    onSave() {
        if (!this.isBlocked()) {
            this.save.emit();
        }
    }

    /**
     * Whether a click on a submit button is held back. Reasons that block it are confirmed against the form first, so that a
     * valid form can always be submitted. Everything else that blocks it (a running save, a disabled footer) is not
     * something a fresh read of the form could change.
     */
    private isBlocked(): boolean {
        if (this.isDisabled() || this.isSaving()) {
            return true;
        }
        if (!this.invalidReasons().length) {
            return false;
        }
        const freshReasons = this.revalidate()?.();
        return freshReasons === undefined || freshReasons.length > 0;
    }
}

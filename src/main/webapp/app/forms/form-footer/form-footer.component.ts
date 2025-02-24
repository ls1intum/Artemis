import { Component, EventEmitter, Input, Output, computed, input } from '@angular/core';
import { ValidationReason } from 'app/entities/exercise.model';
import { faBan, faExclamationCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared/components/button.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { SwitchEditModeButtonComponent } from '../../exercises/programming/manage/update/switch-edit-mode-button/switch-edit-mode-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ExerciseUpdateNotificationComponent } from '../../exercises/shared/exercise-update-notification/exercise-update-notification.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
        ButtonComponent,
        ArtemisTranslatePipe,
    ],
})
export class FormFooterComponent {
    protected readonly ButtonSize = ButtonSize;
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faExclamationCircle = faExclamationCircle;

    @Input() isSaving = false;
    @Input() isDisabled = false;
    @Input() invalidReasons: ValidationReason[] = [];
    @Input() notificationText?: string;
    @Input() switchEditMode?: () => void;
    isImport = input<boolean>();
    isCreation = input<boolean>();
    isSimpleMode = input<boolean>();
    areAuxiliaryRepositoriesValid = input<boolean>(true);

    @Output() notificationTextChange = new EventEmitter<string>();
    @Output() save = new EventEmitter<void>();
    @Output() onCancel = new EventEmitter<void>();

    saveTitle = computed<string>(() => (this.isImport() ? 'entity.action.import' : this.isCreation() ? 'entity.action.generate' : 'entity.action.save'));
}

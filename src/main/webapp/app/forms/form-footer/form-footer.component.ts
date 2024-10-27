import { Component, EventEmitter, Input, Output, computed, input } from '@angular/core';
import { ValidationReason } from 'app/entities/exercise.model';
import { faBan, faExclamationCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { ButtonSize } from 'app/shared/components/button.component';

@Component({
    selector: 'jhi-form-footer',
    templateUrl: 'form-footer.component.html',
    styleUrls: ['form-footer.component.scss'],
})
export class FormFooterComponent {
    protected readonly ButtonSize = ButtonSize;
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faExclamationCircle = faExclamationCircle;

    @Input() isSaving: boolean = false;
    @Input() isDisabled: boolean = false;
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

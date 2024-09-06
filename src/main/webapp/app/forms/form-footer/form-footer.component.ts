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

    @Output() save = new EventEmitter<void>();
    @Output() cancel = new EventEmitter<void>();

    @Input() isSaving: boolean = false;
    @Input() isDisabled: boolean = false;

    @Input() invalidReasons: ValidationReason[] = [];

    @Input() notificationText?: string;
    @Output() notificationTextChange = new EventEmitter<string>();

    isImport = input<boolean>();
    isCreation = input<boolean>();

    saveTitle = computed<string>(() => (this.isImport() ? 'entity.action.import' : this.isCreation() ? 'entity.action.generate' : 'entity.action.save'));
}

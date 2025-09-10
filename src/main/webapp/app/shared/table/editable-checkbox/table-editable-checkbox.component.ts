import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * A checkbox to show in a table to edit boolean attributes.
 */
@Component({
    selector: 'jhi-table-editable-checkbox',
    styles: ['.table-editable-field {display: flex; align-items: center}'],
    template: `
        <div class="table-editable-field">
            <input class="table-editable-field__checkbox form-check-input" type="checkbox" [disabled]="disabled" [ngModel]="value" (ngModelChange)="sendValueUpdate()" />
        </div>
    `,
    imports: [FormsModule],
})
export class TableEditableCheckboxComponent {
    @Input() value: boolean;
    @Input() disabled: boolean;
    @Output() onValueUpdate = new EventEmitter();

    /**
     * Triggers and update of the checkbox value when the model changes (e.g. click on the checkbox).
     */
    sendValueUpdate() {
        this.onValueUpdate.emit();
    }
}

import { Component, EventEmitter, Input, Output } from '@angular/core';

/**
 * A checkbox to show in a table to edit boolean attributes.
 */
@Component({
    selector: 'jhi-table-editable-checkbox',
    styles: ['.table-editable-field {display: flex; align-items: center}'],
    template: `
        <div class="table-editable-field">
            <input class="table-editable-field__checkbox" type="checkbox" [disabled]="disabled" [ngModel]="value" (ngModelChange)="sendValueUpdate()" />
        </div>
    `,
})
export class TableEditableCheckboxComponent {
    @Input() value: boolean;
    @Input() disabled: boolean;
    @Output() onValueUpdate = new EventEmitter();

    sendValueUpdate() {
        this.onValueUpdate.emit();
    }
}

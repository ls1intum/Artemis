import { Component, ElementRef, Input, ViewChild } from '@angular/core';

/**
 * An inline editing field to use for tables.
 */
@Component({
    selector: 'jhi-table-editable-field',
    styles: ['.table-editable-field {display: flex; align-items: center}', '.table-editable-field__input {flex: 2 1 auto;}'],
    template: `
        <div class="table-editable-field">
            <input
                #editingInput
                [id]="id"
                class="table-editable-field__input form-control mr-2"
                (blur)="sendValueUpdate($event)"
                (keyup.enter)="sendValueUpdate($event)"
                [value]="inputValue"
                (input)="inputValue = $event.target.value"
                type="text"
            />
        </div>
    `,
})
export class TableEditableFieldComponent {
    @ViewChild('editingInput', { static: false }) editingInput: ElementRef;

    @Input() id: string;
    @Input() set value(value: any) {
        this.inputValue = value;
    }
    @Input() onValueUpdate: (value: any) => any;

    inputValue: any;

    /**
     * Triggers a value update signal and delegates the task to method specified in the Output decorator,
     * sending in also the updated value of the object.
     * @param event The event that occurred.
     */
    sendValueUpdate(event: any) {
        this.inputValue = this.onValueUpdate(event.target.value);
    }
}

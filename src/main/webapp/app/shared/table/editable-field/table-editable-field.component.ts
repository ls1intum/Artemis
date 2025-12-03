import { Component, Input, ViewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';

/**
 * An inline editing field to use for tables.
 */
@Component({
    selector: 'jhi-table-editable-field',
    styles: ['.table-editable-field {display: flex; align-items: center}', '.table-editable-field__input {flex: 2 1 auto; margin-left: 0.25rem}'],
    templateUrl: './table-editable-field.component.html',
    imports: [FormsModule, TranslateDirective, KeyValuePipe, RemoveKeysPipe],
})
export class TableEditableFieldComponent {
    @ViewChild('editingInput') editingInput: NgModel;

    @Input() id: string;
    @Input() pattern?: RegExp;
    @Input() isRequired: boolean;
    @Input() translationBase: string;

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

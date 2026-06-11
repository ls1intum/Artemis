import { Component, effect, input, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/foundation/pipes/remove-keys.pipe';

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
    private readonly editingInputRef = viewChild<NgModel>('editingInput');
    private editingInputOverride?: NgModel;

    get editingInput(): NgModel {
        return (this.editingInputOverride ?? this.editingInputRef())!;
    }

    set editingInput(editingInput: NgModel | undefined) {
        this.editingInputOverride = editingInput;
    }

    id = input<string>('');
    pattern = input<RegExp>();
    isRequired = input(false);
    translationBase = input<string>('');
    value = input<any>();
    onValueUpdate = input.required<(value: any) => any>();

    constructor() {
        effect(() => {
            this.inputValue = this.value();
        });
    }

    inputValue: any;

    /**
     * Triggers a value update signal and delegates the task to method specified in the Output decorator,
     * sending in also the updated value of the object.
     * @param event The event that occurred.
     */
    sendValueUpdate(event: any) {
        this.inputValue = this.onValueUpdate()(event.target.value);
    }
}

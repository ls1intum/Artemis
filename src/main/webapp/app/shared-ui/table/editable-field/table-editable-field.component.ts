import { Component, ViewChild, effect, input } from '@angular/core';
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
    // DEFERRED (Angular 21 migration): kept as @ViewChild instead of viewChild() because the public `editingInput`
    // (an NgModel) is read imperatively as a property from the programming exercise carve-out
    // (programming/manage/.../programming-exercise-information reads `field.editingInput?.valueChanges` / `.valid`
    // over a QueryList<TableEditableFieldComponent>). Converting it to a signal viewChild() would require touching
    // that carve-out consumer, so it is deferred to a follow-up.
    @ViewChild('editingInput') editingInput: NgModel;

    id = input<string>();
    pattern = input<RegExp>();
    isRequired = input<boolean>();
    translationBase = input<string>();

    readonly value = input<any>();
    onValueUpdate = input<(value: any) => any>();

    inputValue: any;

    constructor() {
        // Mirror the `value` input into the internal mutable `inputValue` used by [(ngModel)],
        // preserving the original `@Input() set value` behavior.
        effect(() => {
            this.inputValue = this.value();
        });
    }

    /**
     * Triggers a value update signal and delegates the task to method specified in the Output decorator,
     * sending in also the updated value of the object.
     * @param event The event that occurred.
     */
    sendValueUpdate(event: any) {
        this.inputValue = this.onValueUpdate()?.(event.target.value);
    }
}

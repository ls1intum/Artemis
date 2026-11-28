import { Component, effect, input, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { TumAetUiInputDirective } from '@tumaet/ui-angular/input';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular/message';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { KeyValuePipe } from '@angular/common';
import { RemoveKeysPipe } from 'app/foundation/pipes/remove-keys.pipe';

/**
 * An inline editing field to use for tables.
 */
@Component({
    selector: 'jhi-table-editable-field',
    styles: ['.table-editable-field__input {flex: 2 1 auto; margin-left: 0.25rem}'],
    templateUrl: './table-editable-field.component.html',
    imports: [FormsModule, TumAetUiInputDirective, TumAetUiMessageComponent, ArtemisTranslatePipe, KeyValuePipe, RemoveKeysPipe],
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
    value = input<string | number>();
    // The field edits text, so the update callback always receives the raw string typed into the input.
    // It may return the coerced value the field should display (number/string/boolean) or undefined.
    onValueUpdate = input.required<(value: string) => string | number | boolean | undefined>();

    constructor() {
        effect(() => {
            this.inputValue = this.value();
        });
    }

    inputValue: string | number | boolean | undefined;

    /**
     * Triggers a value update signal and delegates the task to method specified in the Output decorator,
     * sending in also the updated value of the object.
     * @param event The event that occurred.
     */
    sendValueUpdate(event: Event) {
        this.inputValue = this.onValueUpdate()((event.target as HTMLInputElement).value);
    }
}

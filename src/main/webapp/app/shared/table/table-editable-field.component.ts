import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';

/**
 * An inline editing field to use for tables.
 */
@Component({
    selector: 'jhi-table-editable-field',
    styles: [
        '.table-editable-field {display: flex; align-items: center}',
        '.table-editable-field__value {flex: 2 1 auto;}',
        '.table-editable-field__input {flex: 2 1 auto;}',
        '.table-editable-field__edit {flex: 0 1 auto; margin-left: auto;}',
    ],
    template: `
        <div class="table-editable-field">
            <span class="table-editable-field__value" *ngIf="!isEditing">{{ value }}</span>
            <input
                #editingInput
                *ngIf="isEditing"
                class="table-editable-field__input form-control mr-2"
                autofocus
                (blur)="sendValueUpdate($event)"
                (keyup.enter)="sendValueUpdate($event)"
                (keyup.escape)="sendCancelEvent()"
                [value]="value"
                type="text"
            />
            <button class="table-editable-field__edit btn-light" [disabled]="!canEdit" (click)="sendEditStart($event)"><fa-icon [icon]="'pencil-alt'"></fa-icon></button>
        </div>
    `,
})
export class TableEditableFieldComponent<T> implements OnChanges {
    @ViewChild('editingInput', { static: false }) editingInput: ElementRef;

    @Input() value: T;
    @Input() canEdit: boolean;
    @Input() isEditing: boolean;
    @Output() onEditStart = new EventEmitter();
    @Output() onValueUpdate = new EventEmitter<T>();
    @Output() onCancel = new EventEmitter();

    ngOnChanges(changes: SimpleChanges): void {
        // If the field is now being edited, wait for the template to re-render and focus the field.
        if (changes.isEditing && changes.isEditing.currentValue && !changes.isEditing.previousValue) {
            setTimeout(() => {
                if (this.editingInput) {
                    this.editingInput.nativeElement.focus();
                }
            }, 0);
        }
    }

    sendEditStart(event: any) {
        event.preventDefault();
        this.onEditStart.emit();
    }

    sendValueUpdate(event: any) {
        this.onValueUpdate.emit(event.target.value);
    }

    sendCancelEvent() {
        this.onCancel.emit();
    }
}

import { Component, EventEmitter, Input, Output, OnChanges, SimpleChanges, ViewChild, ElementRef } from '@angular/core';

@Component({
    selector: 'jhi-table-editable-checkbox',
    styles: ['.table-editable-field {display: flex; align-items: center}'],
    template: `
        <div class="table-editable-field">
            <input type="checkbox" [ngModel]="value" (ngModelChange)="sendValueUpdate()" />
        </div>
    `,
})
export class TableEditableCheckboxComponent {
    @Input() value: boolean;
    @Output() onValueUpdate = new EventEmitter();

    sendValueUpdate() {
        this.onValueUpdate.emit();
    }
}

import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass } from '@angular/common';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-segmented-toggle',
    standalone: true,
    imports: [NgClass, ButtonComponent],
    templateUrl: './segmented-toggle.component.html',
    styleUrls: ['./segmented-toggle.component.scss'],
})
export class SegmentedToggleComponent<T> {
    @Input() options: { label: string; value: T }[] = [];
    @Input() selected: T;
    @Output() selectedChange = new EventEmitter<T>();

    // Button types for template
    protected readonly ButtonType = ButtonType;

    select(value: T) {
        this.selected = value;
        this.selectedChange.emit(value);
    }
}

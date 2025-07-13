import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-segmented-toggle',
    standalone: true,
    imports: [NgClass],
    templateUrl: './segmented-toggle.component.html',
    styleUrls: ['./segmented-toggle.component.scss'],
})
export class SegmentedToggleComponent<T> {
    @Input() options: { label: string; value: T }[] = [];
    @Input() selected: T;
    @Output() selectedChange = new EventEmitter<T>();

    select(value: T) {
        this.selected = value;
        this.selectedChange.emit(value);
    }
}

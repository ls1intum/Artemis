import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-segmented-toggle',
    standalone: true,
    imports: [NgClass],
    templateUrl: './segmented-toggle.component.html',
})
export class SegmentedToggleComponent {
    @Input() options: { label: string; value: any }[] = [];
    @Input() selected: any;
    @Output() selectedChange = new EventEmitter<any>();

    select(value: any) {
        this.selected = value;
        this.selectedChange.emit(value);
    }
}

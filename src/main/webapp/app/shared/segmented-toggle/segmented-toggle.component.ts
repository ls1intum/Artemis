import { Component, input, model } from '@angular/core';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-segmented-toggle',
    standalone: true,
    imports: [NgClass],
    templateUrl: './segmented-toggle.component.html',
    styleUrls: ['./segmented-toggle.component.scss'],
})
export class SegmentedToggleComponent<T> {
    options = input<{ label: string; value: T }[]>([]);
    selected = model<T>();
    maxLines = input(2);

    select(value: T) {
        this.selected.set(value);
    }
}

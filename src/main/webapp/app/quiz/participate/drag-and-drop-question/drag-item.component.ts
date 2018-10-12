import { Component, Input } from '@angular/core';
import { DragItem } from '../../../entities/drag-item';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html'
})
export class DragItemComponent {
    @Input()
    dragItem: DragItem;
    @Input()
    clickDisabled: boolean;
    // Unused
    @Input()
    invalid: boolean;

    constructor() {}
}

import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
})
export class DragItemComponent {

    @Input() dragItem;
    @Input() clickDisabled;
    // Unused
    @Input() invalid;

    constructor() {}

}

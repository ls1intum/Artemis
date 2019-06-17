import { Component, Input, OnInit } from '@angular/core';
import { DragItem } from '../../../entities/drag-item';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
})
export class DragItemComponent implements OnInit {
    @Input() minWidth: string;
    @Input() dragItem: DragItem;
    @Input() clickDisabled: boolean;
    @Input() invalid: boolean;
    isMobile = false;

    constructor() {}

    ngOnInit(): void {
        if (window.screen.width <= 1024) {
            this.isMobile = true;
        }
    }
}

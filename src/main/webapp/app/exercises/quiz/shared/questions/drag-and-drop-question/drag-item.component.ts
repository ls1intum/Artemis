import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import isMobile from 'ismobilejs-es5';
import { DragItem } from 'app/entities/quiz/drag-item.model';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
    styleUrls: ['./drag-item.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DragItemComponent implements OnInit {
    @Input() minWidth: string;
    @Input() dragItem: DragItem;
    @Input() clickDisabled: boolean;
    @Input() invalid: boolean;
    isMobile = false;

    constructor() {}

    /**
     * Initializes device information and whether the device is a mobile device
     */
    ngOnInit(): void {
        this.isMobile = isMobile(window.navigator.userAgent).any;
    }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDragItem } from 'app/shared/model/drag-item.model';

@Component({
    selector: 'jhi-drag-item-detail',
    templateUrl: './drag-item-detail.component.html'
})
export class DragItemDetailComponent implements OnInit {
    dragItem: IDragItem;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragItem }) => {
            this.dragItem = dragItem;
        });
    }

    previousState() {
        window.history.back();
    }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';

@Component({
    selector: 'jhi-drag-and-drop-mapping-detail',
    templateUrl: './drag-and-drop-mapping-detail.component.html'
})
export class DragAndDropMappingDetailComponent implements OnInit {
    dragAndDropMapping: IDragAndDropMapping;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropMapping }) => {
            this.dragAndDropMapping = dragAndDropMapping;
        });
    }

    previousState() {
        window.history.back();
    }
}

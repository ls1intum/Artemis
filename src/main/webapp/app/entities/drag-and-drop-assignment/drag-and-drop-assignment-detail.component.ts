import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';

@Component({
    selector: 'jhi-drag-and-drop-assignment-detail',
    templateUrl: './drag-and-drop-assignment-detail.component.html'
})
export class DragAndDropAssignmentDetailComponent implements OnInit {
    dragAndDropAssignment: IDragAndDropAssignment;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropAssignment }) => {
            this.dragAndDropAssignment = dragAndDropAssignment;
        });
    }

    previousState() {
        window.history.back();
    }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDropLocation } from 'app/shared/model/drop-location.model';

@Component({
    selector: 'jhi-drop-location-detail',
    templateUrl: './drop-location-detail.component.html'
})
export class DropLocationDetailComponent implements OnInit {
    dropLocation: IDropLocation;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dropLocation }) => {
            this.dropLocation = dropLocation;
        });
    }

    previousState() {
        window.history.back();
    }
}

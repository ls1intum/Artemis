import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDropLocationCounter } from 'app/shared/model/drop-location-counter.model';

@Component({
    selector: 'jhi-drop-location-counter-detail',
    templateUrl: './drop-location-counter-detail.component.html'
})
export class DropLocationCounterDetailComponent implements OnInit {
    dropLocationCounter: IDropLocationCounter;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dropLocationCounter }) => {
            this.dropLocationCounter = dropLocationCounter;
        });
    }

    previousState() {
        window.history.back();
    }
}

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IPointCounter } from 'app/shared/model/point-counter.model';

@Component({
    selector: 'jhi-point-counter-detail',
    templateUrl: './point-counter-detail.component.html'
})
export class PointCounterDetailComponent implements OnInit {
    pointCounter: IPointCounter;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ pointCounter }) => {
            this.pointCounter = pointCounter;
        });
    }

    previousState() {
        window.history.back();
    }
}

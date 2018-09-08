import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IResult } from 'app/shared/model/result.model';

@Component({
    selector: 'jhi-result-detail',
    templateUrl: './result-detail.component.html'
})
export class ResultDetailComponent implements OnInit {
    result: IResult;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ result }) => {
            this.result = result;
        });
    }

    previousState() {
        window.history.back();
    }
}

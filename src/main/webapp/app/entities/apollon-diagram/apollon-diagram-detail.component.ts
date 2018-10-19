import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IApollonDiagram } from 'app/shared/model/apollon-diagram.model';

@Component({
    selector: 'jhi-apollon-diagram-detail',
    templateUrl: './apollon-diagram-detail.component.html'
})
export class ApollonDiagramDetailComponent implements OnInit {
    apollonDiagram: IApollonDiagram;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ apollonDiagram }) => {
            this.apollonDiagram = apollonDiagram;
        });
    }

    previousState() {
        window.history.back();
    }
}

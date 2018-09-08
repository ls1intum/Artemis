import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IParticipation } from 'app/shared/model/participation.model';

@Component({
    selector: 'jhi-participation-detail',
    templateUrl: './participation-detail.component.html'
})
export class ParticipationDetailComponent implements OnInit {
    participation: IParticipation;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ participation }) => {
            this.participation = participation;
        });
    }

    previousState() {
        window.history.back();
    }
}

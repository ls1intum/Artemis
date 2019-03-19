import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TutorGroup } from 'app/entities/tutor-group';

@Component({
    selector: 'jhi-tutor-group-detail',
    templateUrl: './tutor-group-detail.component.html'
})
export class TutorGroupDetailComponent implements OnInit {
    tutorGroup: TutorGroup;

    constructor(protected activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ tutorGroup }) => {
            this.tutorGroup = tutorGroup;
        });
    }

    previousState() {
        window.history.back();
    }
}

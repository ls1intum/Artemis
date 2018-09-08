import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ILtiUserId } from 'app/shared/model/lti-user-id.model';

@Component({
    selector: 'jhi-lti-user-id-detail',
    templateUrl: './lti-user-id-detail.component.html'
})
export class LtiUserIdDetailComponent implements OnInit {
    ltiUserId: ILtiUserId;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ ltiUserId }) => {
            this.ltiUserId = ltiUserId;
        });
    }

    previousState() {
        window.history.back();
    }
}

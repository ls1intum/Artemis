import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ILtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';

@Component({
    selector: 'jhi-lti-outcome-url-detail',
    templateUrl: './lti-outcome-url-detail.component.html'
})
export class LtiOutcomeUrlDetailComponent implements OnInit {
    ltiOutcomeUrl: ILtiOutcomeUrl;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ ltiOutcomeUrl }) => {
            this.ltiOutcomeUrl = ltiOutcomeUrl;
        });
    }

    previousState() {
        window.history.back();
    }
}

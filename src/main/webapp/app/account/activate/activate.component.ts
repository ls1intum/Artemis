import { Component, OnInit } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';

import { ActivateService } from './activate.service';

@Component({
    selector: 'jhi-activate',
    templateUrl: './activate.component.html',
})
export class ActivateComponent implements OnInit {
    error: string | null;
    success: string | null;
    modalRef: NgbModalRef;

    constructor(private activateService: ActivateService, private route: ActivatedRoute) {}

    /**
     * Checks if the user can be activated with ActivateService
     */
    ngOnInit() {
        this.route.queryParams.subscribe(params => {
            this.activateService.get(params['key']).subscribe(
                () => {
                    this.error = null;
                    this.success = 'OK';
                },
                () => {
                    this.success = null;
                    this.error = 'ERROR';
                },
            );
        });
    }
}

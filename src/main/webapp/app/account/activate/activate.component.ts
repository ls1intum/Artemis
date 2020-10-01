import { Component, OnInit } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';

import { ActivateService } from './activate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

@Component({
    selector: 'jhi-activate',
    templateUrl: './activate.component.html',
})
export class ActivateComponent implements OnInit {
    error: string | null;
    success: string | null;
    modalRef: NgbModalRef;
    isRegistrationEnabled = false;

    constructor(private activateService: ActivateService, private route: ActivatedRoute, private profileService: ProfileService) {}

    /**
     * Checks if the user can be activated with ActivateService
     */
    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isRegistrationEnabled = profileInfo.registrationEnabled;
                if (this.isRegistrationEnabled) {
                    // only try to activate an account if the registration is enabled
                    this.activateAccount();
                }
            }
        });
    }

    activateAccount() {
        this.route.queryParams.subscribe((params) => {
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

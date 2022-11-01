import { Component, OnInit } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { LtiInitializerModalComponent } from 'app/overview/exercise-details/lti-initializer-modal.component';
import { UserService } from 'app/core/user/user.service';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
    selector: 'jhi-lti-initializer',
    template: '',
})
export class LtiInitializerComponent implements OnInit {
    modalRef: NgbModalRef | undefined;

    constructor(
        private modalService: NgbModal,
        private userService: UserService,
        private alertService: AlertService,
        private router: Router,
        private activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.activatedRoute.queryParams.subscribe((queryParams) => {
            if (queryParams['initialize'] !== undefined) {
                this.userService.initializeLTIUser().subscribe((res) => {
                    const password = res.body?.password;
                    if (!password) {
                        this.alertService.info('artemisApp.lti.initializationError');
                        this.router.navigate([], {
                            relativeTo: this.activatedRoute,
                            queryParams: { initialize: null },
                            queryParamsHandling: 'merge',
                        });
                        return;
                    }
                    this.modalRef = this.modalService.open(LtiInitializerModalComponent, { size: 'lg', backdrop: 'static', keyboard: false });
                    this.modalRef.componentInstance.password = password;
                });
            }
        });
    }
}

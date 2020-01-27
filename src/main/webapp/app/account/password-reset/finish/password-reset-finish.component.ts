import { AfterViewInit, Component, ElementRef, OnInit, Renderer2 } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';

import { PasswordResetFinishService } from './password-reset-finish.service';

@Component({
    selector: 'jhi-password-reset-finish',
    templateUrl: './password-reset-finish.component.html',
})
export class PasswordResetFinishComponent implements OnInit, AfterViewInit {
    confirmPassword: string;
    doNotMatch: string | null;
    error: string | null;
    keyMissing: boolean;
    resetAccount: any;
    success: string | null;
    modalRef: NgbModalRef;
    key: string;

    constructor(private passwordResetFinishService: PasswordResetFinishService, private route: ActivatedRoute, private elementRef: ElementRef, private renderer: Renderer2) {}

    ngOnInit() {
        this.route.queryParams.subscribe(params => {
            this.key = params['key'];
        });
        this.resetAccount = {};
        this.keyMissing = !this.key;
    }

    ngAfterViewInit() {
        if (this.elementRef.nativeElement.querySelector('#password') != null) {
            this.renderer.selectRootElement('#password', true).focus();
        }
    }

    finishReset() {
        this.doNotMatch = null;
        this.error = null;
        if (this.resetAccount.password !== this.confirmPassword) {
            this.doNotMatch = 'ERROR';
        } else {
            this.passwordResetFinishService.save({ key: this.key, newPassword: this.resetAccount.password }).subscribe(
                () => {
                    this.success = 'OK';
                },
                () => {
                    this.success = null;
                    this.error = 'ERROR';
                },
            );
        }
    }
}

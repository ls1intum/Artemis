import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LtiInitializerModalComponent } from 'app/core/course/overview/exercise-details/lti-initializer/lti-initializer-modal.component';
import { UserService } from 'app/core/user/shared/user.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-lti-initializer',
    template: '<jhi-lti-initializer-modal [(visible)]="showLtiModal" [loginName]="ltiLoginName()" [password]="ltiPassword()" />',
    imports: [LtiInitializerModalComponent],
})
export class LtiInitializerComponent implements OnInit {
    private userService = inject(UserService);
    private alertService = inject(AlertService);
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private accountService = inject(AccountService);
    private destroyRef = inject(DestroyRef);

    showLtiModal = signal(false);
    ltiLoginName = signal('');
    ltiPassword = signal('');

    ngOnInit() {
        this.activatedRoute.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((queryParams) => {
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
                    this.ltiLoginName.set(this.accountService.userIdentity()?.login ?? '');
                    this.ltiPassword.set(password);
                    this.showLtiModal.set(true);
                });
            }
        });
    }
}

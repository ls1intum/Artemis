import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ActivateService } from './activate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { mergeMap } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-activate',
    templateUrl: './activate.component.html',
    imports: [TranslateDirective, RouterLink],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivateComponent implements OnInit {
    private readonly activateService = inject(ActivateService);
    private readonly route = inject(ActivatedRoute);
    private readonly profileService = inject(ProfileService);
    private readonly destroyRef = inject(DestroyRef);

    readonly error = signal(false);
    readonly success = signal(false);
    readonly isRegistrationEnabled: boolean;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
    }

    /**
     * Checks if the user can be activated with ActivateService
     */
    ngOnInit() {
        if (this.isRegistrationEnabled) {
            // only try to activate an account if the registration is enabled
            this.activateAccount();
        }
    }

    activateAccount() {
        this.route.queryParams
            .pipe(
                mergeMap((params) => this.activateService.get(params.key)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: () => this.success.set(true),
                error: () => this.error.set(true),
            });
    }
}

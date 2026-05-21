import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ActivateService } from './activate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { mergeMap } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Component that handles user account activation via email confirmation link.
 * Users receive an activation link after registration containing a unique key.
 * This component extracts that key from the URL and validates it with the server.
 */
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

    /** Indicates whether the activation request failed */
    readonly error = signal(false);
    /** Indicates whether the account was successfully activated */
    readonly success = signal(false);
    /** Whether user registration is enabled on this Artemis instance */
    readonly isRegistrationEnabled: boolean;

    constructor() {
        const profileInfo = this.profileService.getProfileInfo();
        this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
    }

    /**
     * Initiates the account activation process on component initialization.
     * Only attempts activation if registration is enabled on the server.
     */
    ngOnInit() {
        if (this.isRegistrationEnabled) {
            this.activateAccount();
        }
    }

    /**
     * Extracts the activation key from URL query parameters and sends it
     * to the server for validation. Updates success/error signals based
     * on the server response.
     */
    activateAccount() {
        this.route.queryParams
            .pipe(
                mergeMap((params) => this.activateService.activate(params.key)),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                next: () => this.success.set(true),
                error: () => this.error.set(true),
            });
    }
}

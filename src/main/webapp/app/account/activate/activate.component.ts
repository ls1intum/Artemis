import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ActivateService } from './activate.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { mergeMap } from 'rxjs/operators';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-activate',
    templateUrl: './activate.component.html',
    standalone: true,
    imports: [TranslateDirective, RouterLink, ArtemisSharedModule],
})
export class ActivateComponent implements OnInit {
    private activateService = inject(ActivateService);
    private route = inject(ActivatedRoute);
    private profileService = inject(ProfileService);

    error = false;
    success = false;
    isRegistrationEnabled = false;

    /**
     * Checks if the user can be activated with ActivateService
     */
    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.isRegistrationEnabled = profileInfo.registrationEnabled || false;
                if (this.isRegistrationEnabled) {
                    // only try to activate an account if the registration is enabled
                    this.activateAccount();
                }
            }
        });
    }

    activateAccount() {
        this.route.queryParams.pipe(mergeMap((params) => this.activateService.get(params.key))).subscribe({
            next: () => (this.success = true),
            error: () => (this.error = true),
        });
    }
}
